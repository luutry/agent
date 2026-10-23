package com.luutry.agent.preprocessor.router;

import com.luutry.agent.preprocessor.cleaner.CleanResult;
import com.luutry.agent.preprocessor.cleaner.QueryCleaner;
import com.luutry.agent.preprocessor.model.IntentResult;
import com.luutry.agent.preprocessor.model.ProcessedQuery;
import com.luutry.agent.preprocessor.model.QueryIntent;
import com.luutry.agent.preprocessor.model.QueryUnit;
import com.luutry.agent.preprocessor.model.SubTask;
import com.luutry.agent.preprocessor.resolver.CoreferenceResolver;
import com.luutry.agent.preprocessor.resolver.ResolveResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 统一查询路由器，编排完整的预处理管道。
 *
 * 管道顺序：
 * 1. 清洗 → 2. 指代消解 → 3. 复合拆分（保守规则） → 4. 逐单元意图分类 → 5. 路由聚合
 */
@Slf4j
@Component
public class QueryRouter {

    private final QueryCleaner queryCleaner;
    private final CoreferenceResolver coreferenceResolver;
    private final IntentClassifier intentClassifier;
    private final TaskDecomposer taskDecomposer;

    public QueryRouter(
            QueryCleaner queryCleaner,
            CoreferenceResolver coreferenceResolver,
            IntentClassifier intentClassifier,
            TaskDecomposer taskDecomposer) {
        this.queryCleaner = Objects.requireNonNull(queryCleaner);
        this.coreferenceResolver = Objects.requireNonNull(coreferenceResolver);
        this.intentClassifier = Objects.requireNonNull(intentClassifier);
        this.taskDecomposer = Objects.requireNonNull(taskDecomposer);
    }

    /**
     * 通过完整预处理管道处理原始用户查询。
     *
     * @param rawQuery  原始用户输入
     * @param sessionId 会话标识，供指代消解器按需拉取历史；为空时退化为单轮
     * @return 带意图、实体和子任务的完整预处理查询
     */
    public ProcessedQuery route(String rawQuery, String sessionId) {
        ProcessedQuery result = new ProcessedQuery(rawQuery);

        // 步骤 1：清洗（归一化 / 截断）
        CleanResult cleanResult = queryCleaner.clean(rawQuery);
        String cleaned = cleanResult.getCleanedText();
        result.setCleanedQuery(cleaned);
        result.setCleanResult(cleanResult);

        if (cleanResult.isTruncated()) {
            log.info("查询被截断: boundary={}, hardLimited={}, originalCodePoints={}, hardRejected={}",
                    cleanResult.getBoundary(), cleanResult.isHardLimited(),
                    cleanResult.getOriginalCodePoints(), cleanResult.isHardRejected());
        }

        if (cleaned.isBlank()) {
            result.setIntent(QueryIntent.CHITCHAT);
            result.setConfidence(0.0);
            return result;
        }

        // 步骤 2：指代消解
        // 历史不再由本层传入：消解器自行注入 ConversationHistoryService，按需拉取并清洗（R3）
        ResolveResult resolveResult = coreferenceResolver.resolve(cleaned, sessionId);
        String resolved = resolveResult.getResolvedText();
        // 消解文本可能来自 LLM 或规则拼接：同样要过清洗（截断上限、数字脱敏、噪声清理），
        // 否则它是一条绕过入站清洗进入 Prompt 的旁路
        if (resolveResult.isResolved()) {
            CleanResult resolvedClean = queryCleaner.clean(resolved);
            if (!resolvedClean.getCleanedText().isBlank()) {
                if (resolvedClean.isTruncated()) {
                    log.warn("消解后文本超长已被截断: boundary={}", resolvedClean.getBoundary());
                }
                resolved = resolvedClean.getCleanedText();
            }
        }
        result.setResolvedQuery(resolved);
        result.setResolveResult(resolveResult);

        if (resolveResult.isResolved()) {
            log.info("指代消解成功: method={}, confidence={}, pairs={}",
                    resolveResult.getMethod(), resolveResult.getConfidence(),
                    resolveResult.getResolvedPairs());
        }

        // 步骤 3：复合拆分（保守规则）→ 1 个或多个 unit
        // 规则只在"每段都有独立谓语"时才拆，共享谓语的并列宾语保持单 unit
        List<SubTask> subTasks = taskDecomposer.decompose(resolved);
        if (subTasks.isEmpty()) {
            result.addUnit(new QueryUnit(0, resolved));
        } else {
            for (SubTask subTask : subTasks) {
                result.addUnit(new QueryUnit(subTask.getIndex(), subTask.getQuery()));
            }
            log.info("复合查询拆分: units={}", subTasks.size());
        }

        // 步骤 4：对每个 unit 独立分类（输入 = unit 原文，不带扩展）
        for (QueryUnit u : result.getUnits()) {
            IntentResult intentResult = intentClassifier.classify(u.getQuery());
            u.setIntent(intentResult.getIntent());
            u.setConfidence(intentResult.getConfidence());
        }

        // 步骤 4.5：把逐单元意图回填给子任务，供 Planner 按各自意图执行
        if (!subTasks.isEmpty()) {
            List<QueryUnit> units = result.getUnits();
            for (int i = 0; i < subTasks.size(); i++) {
                subTasks.get(i).setIntent(units.get(i).getIntent());
                result.addSubTask(subTasks.get(i));
            }
        }

        // 步骤 5：路由聚合
        aggregateIntents(result);

        return result;
    }

    /**
     * 聚合各单元意图为顶层路由。
     * 全同 → 单路由；不同 → MIXED。
     */
    private void aggregateIntents(ProcessedQuery result) {
        List<QueryUnit> units = result.getUnits();
        if (units.isEmpty()) {
            result.setIntent(QueryIntent.CHITCHAT);
            result.setConfidence(0.0);
            return;
        }

        if (units.size() == 1) {
            QueryUnit single = units.get(0);
            result.setIntent(single.getIntent());
            result.setConfidence(single.getConfidence());
            return;
        }

        // 多单元：全同意图则采用，否则 MIXED
        QueryIntent first = units.get(0).getIntent();
        boolean allSame = units.stream().allMatch(u -> u.getIntent() == first);

        if (allSame) {
            result.setIntent(first);
        } else {
            result.setIntent(QueryIntent.MIXED);
        }

        double avgConfidence = units.stream()
                .mapToDouble(QueryUnit::getConfidence)
                .average()
                .orElse(0.0);
        result.setConfidence(avgConfidence);
    }

    /**
     * 无会话上下文的便捷重载方法。
     */
    public ProcessedQuery route(String rawQuery) {
        return route(rawQuery, null);
    }
}

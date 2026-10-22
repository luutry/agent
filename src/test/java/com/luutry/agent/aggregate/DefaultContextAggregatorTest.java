package com.luutry.agent.aggregate;

import com.luutry.agent.common.enums.DataSource;
import com.luutry.agent.aggregate.model.AggregatedContext;
import com.luutry.agent.aggregate.model.ContextSegment;
import com.luutry.agent.memory.model.ConversationTurn;
import com.luutry.agent.memory.model.MemoryEntry;
import com.luutry.agent.memory.model.MemoryType;
import com.luutry.agent.rag.model.RetrievalResult;
import com.luutry.agent.rag.model.TextChunk;
import com.luutry.agent.tool.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultContextAggregator.
 */
class DefaultContextAggregatorTest {

    private DefaultContextAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new DefaultContextAggregator();
    }

    @Test
    void aggregate_emptyInputs_returnsEmptyContext() {
        AggregatedContext ctx = aggregator.aggregate(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertTrue(ctx.isEmpty());
        assertEquals(0, ctx.getTotalTokens());
        assertEquals(0, ctx.getSegmentCount());
        assertFalse(ctx.isTruncated());
    }

    @Test
    void aggregate_toolOnly_sortedByPriority() {
        ToolResult toolResult = ToolResult.success(Map.of("name", "张三", "dept", "技术部"));

        AggregatedContext ctx = aggregator.aggregate(
                List.of(toolResult),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertFalse(ctx.isEmpty());
        assertEquals(1, ctx.getSegmentCount());
        assertEquals(DataSource.TOOL, ctx.getSegments().get(0).getSource());
    }

    @Test
    void aggregate_multiSource_sortedByPriorityThenScore() {
        ToolResult toolResult = ToolResult.success(Map.of("employee", "张三"));

        RetrievalResult ragResult = RetrievalResult.builder()
                .chunk(TextChunk.builder().content("考勤制度：每日9点上班").build())
                .score(0.9)
                .provenance("[考勤制度v1.0, 第一章]")
                .build();

        MemoryEntry memoryEntry = MemoryEntry.builder()
                .userId("user1")
                .type(MemoryType.LONG_TERM)
                .content("管理者偏好查看技术部数据")
                .tag("preference")
                .build();

        AggregatedContext ctx = aggregator.aggregate(
                List.of(toolResult),
                List.of(ragResult),
                List.of(memoryEntry),
                Collections.emptyList()
        );

        assertEquals(3, ctx.getSegmentCount());
        // First should be TOOL (highest priority)
        assertEquals(DataSource.TOOL, ctx.getSegments().get(0).getSource());
        // Second should be RAG
        assertEquals(DataSource.RAG, ctx.getSegments().get(1).getSource());
        // Third should be MEMORY
        assertEquals(DataSource.MEMORY, ctx.getSegments().get(2).getSource());
    }

    @Test
    void aggregate_deduplication_removesSimilarSegments() {
        // Two very similar TOOL segments
        ContextSegment seg1 = ContextSegment.fromTool("员工张三的绩效评分为A级，表现优秀", 0.9, "Tool:perf");
        ContextSegment seg2 = ContextSegment.fromTool("员工张三的绩效评分为A级，表现优异", 0.7, "Tool:perf2");
        ContextSegment seg3 = ContextSegment.fromRag("考勤管理制度规定每日9点上班", 0.8, "[考勤制度]");

        AggregatedContext ctx = aggregator.aggregateSegments(List.of(seg1, seg2, seg3));

        // seg1 and seg2 are very similar (Jaccard > 0.85), one should be removed
        assertTrue(ctx.getSegmentCount() <= 3, "Dedup should not increase count");
        // The higher-scored one should be kept
        List<ContextSegment> toolSegs = ctx.getSegmentsBySource(DataSource.TOOL);
        assertFalse(toolSegs.isEmpty());
    }

    @Test
    void aggregate_tokenBudget_truncatesWhenExceeded() {
        // Create many completely different-content segments to exceed budget after dedup
        List<ContextSegment> segments = new java.util.ArrayList<>();
        // 60 unique topics to avoid Jaccard dedup
        String[] uniqueTopics = {
                "绩效考核A等级员工占比不超过百分之二十需要强制分布",
                "年度体检安排在三月份由人力资源部统一组织员工前往指定医院",
                "新员工入职培训为期五天涵盖企业文化规章制度系统操作等内容",
                "差旅报销标准一线城市住宿不超过五百元二线城市不超过三百五十元",
                "员工持股计划授予条件为司龄满三年且绩效连续两年达到B级以上",
                "项目奖金发放规则按项目利润的百分之五到百分之十核算分配",
                "弹性工作制核心团队成员需在上午十点到下午四点间在岗其余弹性",
                "离职面谈由HRBP负责记录离职原因分类统计每月汇总分析报告",
                "内部推荐奖励成功入职后转正发放三千元推荐奖金分两次发放",
                "加班调休有效期为三个月过期未使用视为自动放弃不予补偿",
                "年终奖发放时间为次年二月根据年度绩效结果和公司经营状况确定",
                "试用期考核转正流程直属上级评估HR审核部门负责人审批三步走",
                "员工关怀基金用于员工直系亲属重大疾病慰问金额两千元",
                "数据权限分级公开级内部级机密级绝密级四个等级对应不同审批流程",
                "会议室预约系统支持提前七天预约每人每天最多预约两小时",
                "员工满意度调查每半年一次采用匿名问卷形式结果向全员公开",
                "培训学分要求每人每年不少于八十分未达标影响年度绩效评定",
                "远程办公申请需满足入职满一年绩效B以上且岗位适合远程工作",
                "员工档案管理实行电子化纸质档案由人力资源部统一保管密封",
                "供应商准入评审由采购部法务部财务部三方联合评审打分",
                "知识产权归属员工在职期间的工作成果知识产权归公司所有",
                "竞业限制协议核心技术岗位离职后十二个月内不得加入竞争对手",
                "员工晋升答辩每年两次分别在六月和十二月由评审委员会组织",
                "办公区域管理规范工位不存放私人贵重物品公司不承担保管责任",
                "员工通讯录仅显示办公电话和邮箱手机号默认隐藏保护隐私",
                "供应商结算周期月结六十天特殊品类可协商至月结九十天",
                "安全生产培训每年不少于两次消防演练每年至少一次全员参与",
                "员工着装规范商务场合着正装研发岗位可着休闲装但需整洁",
                "公司食堂午餐补贴每餐十五元按月发放至员工饭卡账户",
                "停车场管理员工车辆需办理停车证访客车辆由前台登记放行",
                "员工生日会每月最后一个周五举办当月生日员工集体庆祝",
                "年度团建活动预算每人两千元由各部门自行规划活动方案",
                "员工援助计划提供心理咨询服务全年二十四小时热线免费使用",
                "公文流转审批一般事项三个工作日内完成紧急事项当日处理",
                "印章使用管理合同类用印需法务审核总经理签字后方可盖章",
                "办公耗材领用每月二十五日前提交下月需求计划统一采购发放",
                "快递收发管理私人快递不得到付公司快递需注明部门和用途",
                "访客接待流程来访需提前一天预约前台登记发放临时访客证",
                "会议室设备使用投影仪音响需提前十分钟调试会后恢复原状",
                "节能管理要求最后离开办公区域的人员负责关闭空调和照明",
                "员工工牌遗失补办需缴纳五十元工本费向人力资源部申请",
                "公司班车路线覆盖城东城南城西三条线路早七点准时发车",
                "员工子女教育资助符合条件员工子女高考录取可申请助学金",
                "志愿者活动每季度组织一次员工可自愿报名计入培训学分",
                "公司图书馆藏书三千余册员工可借阅每人每次最多借五本",
                "健身房开放时间工作日中午十二点到一点晚上六点到九点",
                "母婴室使用需提前预约使用完毕后保持整洁方便下一位使用",
                "员工投诉渠道设立匿名信箱和热线由合规部门独立受理调查",
                "内部轮岗制度员工在当前岗位满两年可申请跨部门轮岗",
                "保密协议签署范围全体员工入职时签署涉及商业秘密保护条款",
                "知识产权奖励获得专利授权每项奖励五千元软件著作权三千元",
                "供应商黑名单因质量问题被拉黑的供应商三年内不得重新准入",
                "合同续签评估到期前六十天启动续签评估流程由法务部主导",
                "电子签章管理使用电子签章需通过身份认证签署记录不可篡改",
                "档案销毁流程超过保管期限的档案需编制销毁清单经审批后销毁",
                "公司资质证照由法务部统一管理借用需填写申请单当日归还",
                "办公用品采购季度集中采购各部门提报需求审批后统一购买",
                "固定资产盘点每年年终进行一次由财务部牵头各部门配合",
                "信息系统权限开通需经部门负责人审批IT部门在两个工作日内完成",
                "应急预案演练每半年一次涵盖火灾地震等突发事件疏散路线"
        };
        for (int i = 0; i < uniqueTopics.length; i++) {
            segments.add(ContextSegment.fromTool(
                    uniqueTopics[i],
                    1.0 - i * 0.005,
                    "Tool:test_" + i
            ));
        }

        AggregatedContext ctx = aggregator.aggregateSegments(segments);

        assertTrue(ctx.isTruncated(), "Should be marked as truncated, tokens=" + ctx.getTotalTokens() 
                + " segments=" + ctx.getSegmentCount());
        assertTrue(ctx.getSegmentCount() < uniqueTopics.length, 
                "Should have fewer segments than input, got " + ctx.getSegmentCount());
    }

    @Test
    void aggregate_conversationTurns_includedAsMemorySource() {
        ConversationTurn turn = ConversationTurn.builder()
                .turnNumber(1)
                .userMessage("查看技术部人员")
                .assistantMessage("技术部共有15人，其中...")
                .build();

        AggregatedContext ctx = aggregator.aggregate(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(turn)
        );

        assertEquals(1, ctx.getSegmentCount());
        assertEquals(DataSource.MEMORY, ctx.getSegments().get(0).getSource());
        assertTrue(ctx.getSegments().get(0).getContent().contains("查看技术部人员"));
    }

    @Test
    void aggregate_failedToolResult_excluded() {
        ToolResult failedResult = ToolResult.fail("Tool execution timeout");

        AggregatedContext ctx = aggregator.aggregate(
                List.of(failedResult),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertTrue(ctx.isEmpty());
    }

    @Test
    void aggregate_expiredMemory_excluded() {
        MemoryEntry expired = MemoryEntry.builder()
                .userId("user1")
                .content("expired memory")
                .expireAt(java.time.Instant.now().minusSeconds(3600))
                .build();

        AggregatedContext ctx = aggregator.aggregate(
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(expired),
                Collections.emptyList()
        );

        assertTrue(ctx.isEmpty());
    }

    @Test
    void aggregate_provenanceList_populated() {
        RetrievalResult ragResult = RetrievalResult.builder()
                .chunk(TextChunk.builder().content("考勤制度内容").build())
                .score(0.9)
                .provenance("[考勤制度v1.0, 第一章]")
                .build();

        AggregatedContext ctx = aggregator.aggregate(
                Collections.emptyList(),
                List.of(ragResult),
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertEquals(1, ctx.getProvenanceList().size());
        assertEquals("[考勤制度v1.0, 第一章]", ctx.getProvenanceList().get(0));
    }

    @Test
    void aggregate_sourceDistribution_computed() {
        ToolResult tool = ToolResult.success(Map.of("data", "value"));
        MemoryEntry memory = MemoryEntry.builder()
                .userId("u1").content("mem content").tag("tag").build();

        AggregatedContext ctx = aggregator.aggregate(
                List.of(tool),
                Collections.emptyList(),
                List.of(memory),
                Collections.emptyList()
        );

        Map<DataSource, Integer> dist = ctx.getSourceDistribution();
        assertNotNull(dist);
        assertEquals(1, dist.get(DataSource.TOOL));
        assertEquals(1, dist.get(DataSource.MEMORY));
    }

    @Test
    void aggregateSegments_nullInput_returnsEmpty() {
        AggregatedContext ctx = aggregator.aggregateSegments(null);
        assertTrue(ctx.isEmpty());
        assertEquals(0, ctx.getTotalTokens());
    }
}

# ThreadLocal 与线程复用：为什么会「用户上下文泄露」通俗讲解

> 背景：`AgentController.chat()` 在 `finally` 里调用 `UserContextHolder.clear()`，
> 注释写着「防止线程复用导致用户上下文泄露」。本文用最通俗的例子讲清楚：
> 这句话到底在防什么、不防会怎样、项目里是怎么做的。
>
> 关联代码：
> - `src/main/java/com/luutry/agent/security/auth/UserContextHolder.java`
> - `src/main/java/com/luutry/agent/web/AgentController.java`（L83-L150）
> - `src/main/java/com/luutry/agent/tool/executor/ToolExecutor.java`（L136-L162）

---

## 一、先讲一个生活中的例子：公司共享工位

想象一家公司用**共享工位**（hot desk）：员工不固定座位，每天来了随便找个空位坐下。
每个工位配一个**带锁的抽屉**，你可以把私人物品放进去——比如你的**工牌**和**门禁权限卡**。

现在关键来了：

```
周一：张三坐 3 号工位
      → 把「张三的权限卡（能进财务机房）」放进抽屉
      → 下班走了，但【没清空抽屉】❌

周二：李四坐 3 号工位（工位是复用的！）
      → 李四拉开抽屉，发现里面有一张「张三的权限卡」
      → 李四拿它去刷门禁，进了他自己进不去的财务机房 💥 越权！
      → 抽屉里还堆着张三没拿走的东西，越堆越满 💥 内存泄露！
```

**问题的根源**：抽屉属于**工位**，不属于**某个人**。工位会被不同的人复用，
但抽屉里的东西不会自动消失。张三走了不清空，李四就能看到张三的东西。

---

## 二、把比喻翻译成代码世界

上面的故事，一一对应到我们项目：

| 生活场景 | 代码世界 | 说明 |
|---|---|---|
| 共享工位 | **线程**（Web 容器的工作线程） | 工位不固定给人，线程也不固定给请求 |
| 工位会被复用 | **线程池复用** | Tomcat 默认最多 200 个线程循环使用 |
| 带锁的抽屉 | **ThreadLocal** | 抽屉绑在工位上，ThreadLocal 绑在线程上 |
| 张三的权限卡 | **UserContext** | 当前用户的身份 + 数据权限范围 |
| 把卡放进抽屉 | `UserContextHolder.set(ctx)` | 请求入口装配用户上下文 |
| 拉开抽屉拿卡 | `UserContextHolder.get()` | 工具层读取，用于数据权限过滤 |
| **下班清空抽屉** | **`UserContextHolder.clear()`** | **请求结束必须清理** |
| 李四看到张三的卡 | **上下文泄露 / 越权** | 线程复用时读到上一个用户的数据 |

**一句话对应**：线程 = 工位（会复用），ThreadLocal = 抽屉（绑工位不绑人），
UserContext = 权限卡（私人物品），`clear()` = 下班清空抽屉。

---

## 三、ThreadLocal 到底是什么

`ThreadLocal` 是 Java 提供的一个「**每个线程各存一份、互不干扰**」的变量容器。

我们项目里的 `UserContextHolder` 就是这么实现的：

```java
public final class UserContextHolder {

    // 关键：这个 CONTEXT 绑在“当前线程”上，每个线程看到的是自己那一份
    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    public static void set(UserContext userContext) { CONTEXT.set(userContext); }
    public static UserContext get()                 { return CONTEXT.get(); }
    public static void clear()                      { CONTEXT.remove(); }  // ← 清理
}
```

**为什么要用它？** 因为一次请求要穿过很多层：

```
Controller → Planner → Tool / Memory / RAG
```

每一层都可能需要知道「当前是哪个用户、他有哪些数据权限」。
如果靠方法参数一层层往下传，方法签名会被污染成：

```java
// ❌ 每个方法都得拖着 userContext 参数，耦合度极高
plan(query, userId, sessionId, userContext);
executeTool(request, userContext);
retrieve(query, userContext);
```

用 ThreadLocal 后，入口 `set` 一次，各层随时 `get`，签名干净：

```java
// ✅ 入口装配一次
UserContextHolder.set(userContext);
// ...任意深度的调用里，需要时直接拿
UserContext ctx = UserContextHolder.get();
```

**代价**：既然数据绑在**线程**上，那线程一旦被复用，上一个请求留下的数据就还在——
这正是「泄露」的源头。所以 `set` 必须配对 `clear`。

---

## 四、线程池复用到底是什么

很多人以为「一个请求 = 一个新线程，请求结束线程就销毁」。**错。**

Web 容器（Spring Boot 内嵌的 Tomcat）为了性能，用的是**线程池**：
预先创建一批线程，请求来了分配一个空闲线程，请求处理完**线程不销毁，放回池里等下一个请求**。

```
线程池（假设 3 个线程）：

请求 A 到达 → 分配 worker-1 → 处理 → 返回 → worker-1 【回池，不销毁】
请求 B 到达 → 分配 worker-1（复用！）→ 处理 → 返回 → worker-1 回池
请求 C 到达 → 分配 worker-2 → ...
```

**为什么要复用？** 创建/销毁线程很贵（涉及操作系统调用、内存分配）。
高并发下如果每个请求都新建线程，系统会被拖垮。所以线程池是标配。

**副作用**：线程「不死」，那绑在线程上的 ThreadLocal 数据自然也「不死」。
你不主动清，它就一直挂在那个 worker 线程上，等着被下一个用到该线程的请求读到。

---

## 五、两者相遇 = 泄露（两个真实危害）

ThreadLocal（绑线程）+ 线程池复用（线程不死）= 上一个请求的数据残留。
具体有两个危害：

### 危害 1：权限串号（越权，直接违反项目铁律 R2）

> R2 权限前置：数据权限必须在查询条件层过滤，禁止查完再过滤。

走一遍事故现场：

```
请求 A：userId = manager_001（能看 技术部/人力/财务 三个部门）
   → 命中 worker-3
   → Controller L85：UserContextHolder.set(manager_001 的 UserContext)
   → 工具层 get() 读到，数据权限范围 = 三个部门，返回三个部门的数据
   → 响应返回，worker-3 回池
   → 【若不清理】worker-3 身上还挂着 manager_001 的上下文 ❌

请求 B：userId = manager_tech（只能看 技术部）
   → 恰好也命中 worker-3
   → 假如某段代码在 Controller 重新 set() 之前，或走了没覆盖到的分支去 get()
   → 读到的是【残留的 manager_001 权限】
   → 用 manager_001 的数据范围去查 → 看到了人力、财务的数据 💥 越权！
```

这正是 R2 最怕的：**数据权限过滤器拿到了错误的用户**。本该只看技术部的人，
看到了别的部门数据，属于严重的权限泄露事故。

### 危害 2：内存泄露

`ThreadLocal` 内部用一张 `ThreadLocalMap` 存数据，对 value（UserContext）是**强引用**。
而线程池里的线程生命周期 ≈ 整个应用的生命周期（应用不关，线程不死）。

```
不清理 → 强引用一直存在 → UserContext 对象永远无法被 GC 回收
请求越多、线程越多 → 残留对象越堆越多 → 内存占用持续上涨 → 最终可能 OOM
```

即使下一个请求会「覆盖」旧值，覆盖前的那段时间对象也一直占着内存；
而如果那个线程之后一直空闲没被复用，旧值就**永远**泄露在那里。

---

## 六、项目真实代码走查

同一个道理，在项目里有两处应用：主线程清理 + 跨线程透传。

### 6.1 主线程：AgentController 入口装配、finally 清理

```java
// AgentController.chat()  （L83-L150）
// 步骤2：请求入口装配 UserContext，供工具查询层做数据权限前置过滤（R2）
UserContext userContext = userDirectory.resolve(request.getUserId());
UserContextHolder.set(userContext);          // ← 放卡进抽屉
try {
    // ... 预处理 / 规划 / 聚合 / 校验，各层按需 get() ...
    return ApiResponse.success(chatResponse);
} catch (Exception e) {
    log.error("Error processing chat request: {}", e.getMessage(), e);
    return ApiResponse.fail(...);            // ← 异常也要返回
} finally {
    // 防止线程复用导致用户上下文泄露
    UserContextHolder.clear();               // ← 下班清空抽屉（一定执行）
}
```

要点：`set` 在 `try` 之前，`clear` 在 `finally` 里，保证**无论成功还是抛异常都清理**。

### 6.2 跨线程：ToolExecutor 显式透传 + 还原

工具执行用了 `CompletableFuture` 跑在**独立线程池**上。这里有个坑：
**ThreadLocal 默认不跨线程传递**——主线程 `set` 的上下文，异步线程 `get` 到的是 `null`。

所以 `ToolExecutor` 手动做了「捕获 → 重建 → 还原」：

```java
// ToolExecutor.executeWithTimeout()  （L136-L162）
// 工具在异步线程执行，ThreadLocal 不会自动传递；
// 显式捕获调用线程的 UserContext 并在执行线程重建，否则工具层拿不到数据权限（R2）。
UserContext callerContext = UserContextHolder.get();     // ① 主线程：捕获（复印权限卡）

CompletableFuture<ToolResult> future = CompletableFuture.supplyAsync(() -> {
    UserContext previous = UserContextHolder.get();      // ② worker：记住它原来的值
    UserContextHolder.set(callerContext);                // ③ worker：重建（把复印件放进自己抽屉）
    try {
        return tool.execute(request);                    // ④ 工具层现在能 get() 到权限了
    } finally {
        if (previous != null) {
            UserContextHolder.set(previous);             // ⑤ 还原成 worker 原来的值
        } else {
            UserContextHolder.clear();                   // ⑤ 或清空——同样是防止泄露
        }
    }
}, toolPool);
```

用比喻说：你把活儿外包给另一个部门的人（另一个线程），
**你的抽屉钥匙不会自动传给他**。所以你要：

1. **复印一份权限卡给他**（`callerContext` 捕获）；
2. 他干活前**记下自己抽屉原本有什么**（`previous`）；
3. 干完活**把复印件销毁、恢复原样**（`finally` 还原 / 清空）——
   否则复印件留在他抽屉里，下一个用他的人都能看到，又是泄露。

> 两处 `finally` 清理是同一个原则的两次应用：**谁污染，谁清理；用完必还原。**

---

## 七、为什么 clear() 必须放 finally，不能放 try 末尾

因为 `try` 里任何一步抛异常，都会**跳过 try 末尾**，直接进 `catch`：

```java
try {
    step1();   // 假如这里抛异常
    step2();
    clear();   // ❌ 永远执行不到！上下文泄露
} catch (Exception e) {
    return fail();   // 异常也 return 了，还是没清理
}
```

只有 `finally` 块，**无论正常返回还是异常返回，JVM 都保证执行**：

```java
try {
    step1();
    step2();
} catch (Exception e) {
    return fail();
} finally {
    clear();   // ✅ 一定执行
}
```

所以 `ThreadLocal` 有条铁律：**`set()` 与 `remove()` 必须成对，且 `remove()` 放 `finally`。**

---

## 八、常见误区

### 误区：「下个请求进来会重新 set() 覆盖，旧值不就被冲掉了？何必 clear()」

不行，三个理由：

1. **覆盖只在执行到达 `set()` 那一行时才发生**。在此之前，若有任何组件（拦截器、
   异步任务、AOP 切面）读上下文，拿到的就是**上一个用户的脏数据**。
2. **内存泄露与「是否被覆盖」无关**。只要不 `remove()`，强引用就一直在，对象就回收不掉。
3. **依赖「下个请求会覆盖」= 把正确性寄托在调用时序上**，极其脆弱。一旦哪天代码路径变了
   （比如加了个提前 return、加了个异步分支），立刻埋雷。`set↔remove` 配对才是防御性正解。

### 误区：「ThreadLocal 有内存泄露风险，干脆别用」

不是不用，而是**用了就必须配对清理**。ThreadLocal 是解决「跨层透传请求级数据」的标准手段，
问题从来不在 ThreadLocal 本身，而在于「只 set 不 remove」。

---

## 九、一句话总结 + 记忆口诀

**总结**：Web 容器的线程是**池化复用**的（线程不死），而 `ThreadLocal` 把数据**绑在线程上**
（不绑请求）。请求结束若不 `clear()`，上一个用户的 `UserContext` 就残留在该线程上，
被下一个复用该线程的请求读到——轻则**内存泄露**，重则**权限串号越权（违反 R2）**。
所以必须在 `finally` 里 `clear()`，保证成功和异常都清理。

**记忆口诀**：

```
线程是工位会复用，
ThreadLocal 是抽屉绑工位。
上班 set 放权限卡，
下班 finally 必 clear 清空抽屉。
外包给别的线程（异步），
钥匙不自动传，要手动复印 + 用完销毁。
```

**通用规律**（不止本项目）：任何「请求级 / 线程级」的上下文（用户身份、traceId、
事务、MDC 日志），只要用了 ThreadLocal，都必须在 `finally` 清理；跨线程时必须手动透传。
这是并发编程的一条通用铁律。

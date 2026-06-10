# Core 核心引擎

## 概述

`debug4j-core` 模块是 debug4j 的调试引擎核心，涵盖 Instrumentation 管理、字节码操作、在线编译、Socket 通信、代理转发等关键技术。

## Debugger — 调试器入口

```java
// 1. 安装 ByteBuddyAgent 获取 JVM Instrumentation
instrumentation = ByteBuddyAgent.install();

// 2. 运行时修补 jadx 反编译器的字段排序
new AgentBuilder.Default()
    .type(named("jadx.core.dex.visitors.ExtractFieldInit"))
    .transform((builder, type, cl, module, pd) ->
        builder.method(named("applyFieldsOrder"))
               .intercept(MethodDelegation.to(ApplyFieldsOrderPatch.class)))
    .installOn(inst);

// 3. 启动保活定时器（10 秒间隔）
scheduledExecutor.scheduleWithFixedDelay(() -> {
    if (socketClient == null || !socketClient.isAlive()) {
        socketClient = new SocketClient(host, port, ...);
    }
}, 0, 10, TimeUnit.SECONDS);
```

## SocketClient — 通信客户端

基于 smart-socket `AioQuickClient` 实现，覆盖连接生命周期管理、分包重装、消息分发：

```java
onConnect(NEW_SESSION) → send AUTH(key) → send INFO(commandInfoMessage)
onMessage(ProtocolType) → {
    COMMAND → handCommand() → 分包重装 → 业务分发
    PROXY  → forward to SocketTFProxyClient
    FILE   → RandomAccessFile 分块写入 → Files.move()
}
```

**分包重装：** 按 clientId 缓存分包数据，全部收齐后还原为完整消息。

## 三级字节码捕获

debug4j 定义了三种字节码来源，用于判断类是否被 Agent 修改：

| 字节码类型 | 来源 |
|-----------|------|
| **原始字节码**（`originalClassFile`） | 从 ClassLoader.getResource() 加载 |
| **Javassist 影响**（`agentOnlyJavassist`） | ClassPool 转换后 toBytecode() |
| **ByteBuddy 影响**（`agentWithByteBuddy`） | 拦截 retransform 获取 |

通过 MD5 哈希（字段+方法签名）对比自动选择反编译策略。

## ClassFileTransformer 热更新引擎

`Debug4jClassFileTransformer` 的 `transform()` 方法根据命令类型执行不同操作：

| 命令类型 | 操作 |
|----------|------|
| `CLASS_RELOAD` | 解码 Base64 字节码 → 设置到目标类 |
| `CLASS_RELOAD_JAVA` | 编译源码为字节码 → 设置到目标类 |
| `CLASS_RELOAD_JAVA_LINE` | javassist `insertAt(line, code)` 注入行代码 |
| `CLASS_SOURCE` | 检测字节码类型（三级机制） |
| `CLASS_RESTORE` | 返回原始字节码 |

**核心流程（reTransformer）：**

```java
protected static void reTransformer(ClassLoader loader, byte[] newByteCode, String className) {
    inst.addTransformer(transformer, true);   // 注册（仅 retransform 时触发）
    try {
        inst.retransformClasses(targetClass);  // 触发 transform()
    } finally {
        inst.removeTransformer(transformer);   // 移除（一次性使用）
    }
}
```

## 在线编译系统

使用 Java 编译器 API（`javax.tools.JavaCompiler`），**完全在内存中**完成编译：

```
JavaSourceFileObject（源码）
  → CompilationTask
    → JavaClassFileManager（拦截输出）
      → JavaClassFileObject（ByteArrayOutputStream）
        → ResourceClassLoader（defineClass）
```

## 进程调整引擎

`Debug4jProcessOperator`（1088 行）包含一个大规模的 `switch-case` 指令分发器，涵盖所有进程级别调整操作：

| 调整类型 | 实现 |
|----------|------|
| 日志级别 | `LoggerOperator.setLevel()`（Logback/Log4j2 适配器） |
| Heap Dump | `HotSpotDiagnosticMXBean.dumpHeap()` |
| JFR 录制 | 反射创建 `jdk.jfr.Recording`（Object 类型声明避免类加载失败） |
| SFTP | Apache MINA SSHD 启动/停止 |
| 文件操作 | Hutool FileUtil + NIO Files.walkFileTree() |
| 对象操作 | 反射 getFieldValue / setFieldValue / invokeRaw |
| 方法追踪 | ByteBuddy Advice 安装/卸载 |
| Arthas/SSH | Debug4jResourceExtractor 解压 + ProcessBuilder 安装 |

## 日志文件跟踪

`Debug4jWatcher` 基于 Hutool Tailer + 超时管理实现 `tail -f`：

```java
private static final TimedCache<String, TaskInfo> watcher = CacheUtil.newTimedCache(30 * 1000);
// 30 秒无心跳自动停止，防止资源泄漏
watcher.setListener((key, cachedObject) -> cachedObject.getTailer().stop());
```

## 模块文件清单

| 文件 | 职责 |
|------|------|
| `Debugger.java` | 入口：Instrumentation、jadx 补丁、保活 |
| `SocketClient.java` | Socket 通信客户端 |
| `Debug4jAttachOperator.java` | 类操作：反编译、热更新、行补丁 |
| `Debug4jClassFileTransformer.java` | ClassFileTransformer 实现 |
| `Debug4jProcessOperator.java` | 进程调整引擎 |
| `Debug4jWatcher.java` | 日志文件实时跟踪 |
| `Debug4jTraceInstaller.java` | ByteBuddy 追踪安装/卸载 |
| `Debug4jTraceLogAdvice.java` | 方法追踪 Advice |
| `Debug4jTraceSafePrinter.java` | 安全打印 + Trace ID |
| `LoggerOperator.java` | 日志级别管理 |
# 日志管理

## 概述

增强用户进程的日志管理能力，涵盖日志级别调整、标准输出复制、实时跟踪与检索。

## 功能清单

### 日志级别调整

动态修改日志框架的 Logger 级别，支持 TRACE / DEBUG / INFO / WARN / ERROR / OFF。

**技术实现（LoggerOperator）：** 运行时自动检测日志框架：

```java
public enum Level {TRACE, DEBUG, INFO, WARN, ERROR, OFF}

public synchronized static void setLevel(String logger, Level level) {
    switch (detectLoggerImpl()) {
        case "LOGBACK" -> LogbackAdapter.set(logger, level);
        case "LOG4J2"  -> Log4j2Adapter.set(logger, level);
    }
}

private static String detectLoggerImpl() {
    // 通过 Class.forName() 检测 classpath 中的日志框架
    try { Class.forName("ch.qos.logback.classic.Logger"); return "LOGBACK"; } catch (...) {}
    try { Class.forName("org.apache.logging.log4j.core.Logger"); return "LOG4J2"; } catch (...) {}
    return "UNKNOWN";
}
```

### 标准输出复制

将 `System.out` / `System.err` 通过 `ListenableOutputStream` 代理，按行触发回调，支持关键词过滤转存。

```java
public class ListenableOutputStream extends OutputStream {
    private final OutputStream original;
    private final Consumer<String> lineProcessor;
    private boolean inProcessing = false;  // 防止回调重入

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        original.write(b, off, len);  // 原始输出保持不变
        // 按行分隔处理 ...
    }
}
```

### 日志文件实时跟踪

基于 **Hutool Tailer** + **超时管理** 实现类似 `tail -f` 的日志跟踪：

```java
// 30 秒无心跳自动停止
private static final TimedCache<String, TaskInfo> watcher = CacheUtil.newTimedCache(30 * 1000);

static {
    watcher.setListener((key, cachedObject) -> cachedObject.getTailer().stop());
    watcher.schedulePrune(1000);
}
```

- Server 端每 30 秒发送任务心跳维持 watcher 存活
- Server 断开时，watcher 自动超时清理，防止 Client 端资源泄漏
- 每读取一行，通过 Socket 回调发送给 Server，Server 通过 SSE 推送到前端

### 日志文件检索

类似 `zgrep` 的日志检索，支持：
- 递归扫描日志目录
- 关键词匹配与上下文行数控制
- GZIP 压缩文件的读取支持
- 结果高亮显示
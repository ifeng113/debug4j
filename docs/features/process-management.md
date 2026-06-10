# 进程管理

## 概述

增强或修改 JVM 的各类参数、属性及方法，实现运行时的深度控制。

## 功能清单

### 系统参数

- **查看所有进程参数**：包含 JVM 参数、环境变量、Spring 及 Nacos 等配置参数
- **动态修改系统参数**：
  - JVM `System.Property`
  - Spring `MutablePropertySources`（支持 `@RefreshScope` 自动更新 Bean）
  - 环境变量
  - 启动参数

### 进程重载

支持两种重载模式，用于修改进程启动时的 JVM 参数：

| 模式 | 行为 | 适用场景 |
|------|------|----------|
| **Reload（重载）** | 当前进程内重新运行 `SpringApplication.run()` | 普通配置变更 |
| **Restart（重启）** | `ProcessBuilder` 新建子进程，携带修改后的参数 | 需修改 JDWP/GC Log/JMX 等 JVM 参数 |

可动态开启：
- JDWP 远程调试（`-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`）
- GC 日志（`-Xlog:gc*,gc+age=trace,gc+heap=info:file=debug4j-gc.log:time,level,tags`）
- JMX 远程监控（`-Dcom.sun.management.jmxremote ...`）

### 对象增强

| 操作 | 实现方式 |
|------|----------|
| **获取内存对象信息** | `instrumentation.getAllLoadedClasses()` + 实例遍历 |
| **修改对象属性值** | `ReflectUtil.getFieldValue()` / `setFieldValue()` |
| **执行对象方法** | `ReflectUtil.invokeRaw()` |
| **Spring Bean 支持** | 自动发现 + AOP 代理解包（`AopProxyUtils.getSingletonTarget()`） |
| **方法耗追踪** | ByteBuddy Advice 插桩，记录参数/返回值/耗时 |

### JVM 监控

| 功能 | 实现 |
|------|------|
| **内存快照（Heap Dump）** | `HotSpotDiagnosticMXBean.dumpHeap()` |
| **JFR 飞行记录** | `jdk.jfr.Recording` — 启动/停止/下载 .jfr 文件 |
| **监控文件管理** | 统一管理各类监控文件 |

#### JFR 关键技术点

> 使用 `Object` 类型声明避免类加载失败。如果直接声明为 `jdk.jfr.Recording`，在 JRE 环境（无 `jdk.jfr` 模块）下会直接导致类加载失败。使用 `Object` 延迟实际类型绑定，运行时才通过反射创建 `Recording` 实例。
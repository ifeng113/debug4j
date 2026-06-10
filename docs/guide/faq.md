# 常见问题

## 类签名限制

**问题：为什么无法修改字段名或方法名？**

这是 JVM 层级的硬限制：已加载类的结构（字段名、字段类型、方法名、方法参数类型、方法返回值类型）是固定的。

- `Instrumentation.retransformClasses()` **不支持**修改类签名信息的新增、修改或删除
- Javassist / ByteBuddy 虽然可以动态生成新方法 / 字段，但会生成**新的 Class 对象**，原有代码无法直接使用新类
- debug4j 专注于**方法体内的修改**（调试方向），而非热编码业务方向

---

## Agent 兼容性

**问题：使用 SkyWalking / Arthas 等 Agent 后，debug4j 功能异常？**

- Agent（特别是 ByteBuddy）可能改变字节码结构，导致源码热更新与字节码热更新功能不可用
- JDK 8 下，行补丁因无法反编译执行源码，也不可用
- 推荐使用 debug4j 时**尽量不使用其他 Agent**，或修改 agent 相关配置
- `-javaagent` 指定的 agent 编译版本需与主程序 JDK 版本兼容

**相关链接：**
- [SkyWalking 兼容 Arthas 问题](https://blog.csdn.net/weixin_42106289/article/details/128467219)
- [java.lang.ClassFormatError: null、SkyWalking Arthas 兼容使用](https://arthas.aliyun.com/doc/faq.html#java-lang-classformaterror-null%E3%80%81skywalking-arthas-%E5%85%BC%E5%AE%B9%E4%BD%BF%E7%94%A8)

---

## 进程重载与 Docker

**问题：Docker 环境中 Restart 模式是否正常工作？**

可以正常工作。Restart 模式通过 `ProcessBuilder` 创建**子进程**启动新应用实例，**主进程（PID 1）始终保持运行**，因此 Docker 容器不会销毁。Restart 模式设计为新建子进程的目的正是为了保持主进程不中断。

**模式选择：**
- **Reload 模式** — 在当前进程内重新加载 Spring 上下文，不新建进程，适用于普通配置变更
- **Restart 模式** — 新建子进程运行，可修改 JVM 参数（如 JDWP、GC Log 等），适用于需要变更启动参数的场景

---

## JRE 环境支持

debug4j 与 Arthas 均不兼容 JRE 环境，原因相同：两者都依赖 JDK 的 Attach API 获取 JVM Instrumentation，而 JRE 不包含此 API。相关核心特性支持如下：

| 功能 | JRE 支持 | 说明 |
|------|----------|------|
| JDWP 远程调试 | ✅ | JRE 17（eclipse-temurin:17-jre）可开启 |
| JMX 远程监控 | ✅ | 不受影响 |
| JFR 飞行记录 | ✅ | `jdk.jfr.Recording` 在 JRE 中可用 |
| ByteBuddyAgent.install() | ❌ | 需要 JDK 的 Attach API |
| `Arthas` | ❌ | 启动报错：`Cannot find java process. Try to run jps or jcmd commands to list the instrumented Java HotSpot VMs`，详见 [Arthas Docker 官方文档](https://arthas.aliyun.com/doc/docker.html#%E9%80%9A%E8%BF%87-docker-%E5%BF%AB%E9%80%9F%E5%85%A5%E9%97%A8) |

**JRE 下的未来方案设想**：通过 `Restart` 模式重载启动 debug4j-boot，并动态织入 `-javaagent:debug4j-agent.jar` 方式获取 JVM Instrumentation（尚未实现）。

---

## 其他常见问题

### final 变量无法修改

Java 12+ 彻底禁止修改 `static final` 字段（`Field.modifiers` 已不可反射修改），debug4j 不支持 final 变量修改。

### 运行中方法无法热更新

代码热更新不能作用于正在运行的方法，与 Arthas 相同。

### 内部类行补丁权限问题

行补丁中引用外部类的 `private` 成员变量会报错（外部类会生成 `access$xxx` 桥接方法，但 `javassist` 插入的代码无法访问私有成员）。建议使用 `getter/setter` 或 `public` 字段。

### 字节码版本兼容

class 文件的 JDK 编译版本需与目标 JVM 版本兼容，否则无法热更新。

### 进程模式编译

debug4j-boot（进程代理模式）在更改代码后需要对 debug4j-boot 与 debug4j-packing 进行 shadowJar，因为 debug4j-daemon 是通过创建 java 子进程（debug4j-packing 压缩包中的 debug4j-boot.jar）运行的，而不是源码编译运行。
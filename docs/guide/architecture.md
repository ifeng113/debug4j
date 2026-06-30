# 架构原理

## 整体架构

Debug4j 采用 **三端架构**，通过 TCP 长连接实现安全稳定的双向通信。

```
┌──────────────┐      ┌──────────────────┐      ┌──────────────────┐
│   🌐 浏览器    │      │    🖥️ Server 端    │      │   📦 Client 端    │
│  (管理界面)    │◄────►│  (API + Web 管理)  │◄────►│  (调试功能引擎)   │
└──────────────┘      │  端口: 7987/7988  │      └────────┬─────────┘
                      └──────────────────┘               │
                              ▲                          │
                              │  TCP长连接                │  TCP长连接
                              ▼                          ▼
                      ┌──────────────────┐      ┌──────────────────┐
                      │  🧩 Proxy Client  │◄────►│   🧑‍💻 用户进程      │
                      │  (流量代理引擎)    │      │  (目标Java应用)    │
                      └──────────────────┘      └──────────────────┘
```

### 组件说明

| 组件 | 说明 |
|------|------|
| **Server 端** | 服务端，提供 Web 管理界面和 REST API，负责管理所有 Client 连接和代理规则 |
| **Client 端** | 伴随用户进程启动，处理调试、源码热更新、进程管理等核心功能 |
| **Proxy Client 端** | 独立代理进程（由 Client 自动拉起），仅负责 TCP 流量代理，不参与任何调试逻辑 |

### 为什么需要单独创建 Proxy Client 端？

1. **功能分离**：Client 端专注调试修改，Proxy Client 端仅处理 TCP 代理流量，职责单一、互不干扰
2. **JDWP 阻塞问题**：当使用 JDWP 远程调试时，用户进程会全部阻塞。分离后 Proxy Client 作为独立进程，即使 JDWP 暂停了用户进程，代理流量仍可正常传输

---

## 两种工作模式

### 线程模式（Thread）

debug4j 作为一个线程运行在目标 JVM 内部，通过 `ByteBuddyAgent.install()` 获取 `Instrumentation` 实例。

```
目标进程
├── 业务代码
├── debug4j Client（线程）
│   ├── SocketClient（与 Server 通信）
│   ├── ByteBuddyAgent（Instrumentation）
│   └── Attach 操作引擎
└── Proxy Client（可通过线程模式拉起）
```

- ✅ 逻辑简单、资源消耗低、无需额外进程
- ❌ 侵入性较高、不支持 JDWP 远程调试（JDWP 会阻塞所有线程）

### 进程模式（Process）

通过新起一个独立的 JVM 进程（boot jar）与用户进程交互。

```
目标进程                     Boot 进程（独立 JVM）
├── 业务代码                  ├── debug4j Agent
└── debug4j Daemon            ├── SocketClient
    └── 启动 Boot 进程 ──────►├── ByteBuddyAgent
                              └── Proxy 处理器
```

- ✅ 隔离性好、耦合度低、支持自定义 agent.jar 加载
- ❌ 额外内存与进程开销、实现复杂度较高

### 混合模式（当前采用）

> **主体采用线程模式，使用 Proxy（含 JDWP 远程调试）功能时采用进程模式。**
>
> 项目执行顺序：`boot -> shadowJar` → `packing -> shadowJar` → `server -> run` → `demo1/demo2 -> run`

---

## 启动流程

### Debugger 启动时序

```
目标应用启动
  → ByteBuddyAgent.install()          // 获取 Instrumentation
  → ByteBuddy 修补 jadx 字段排序       // 保证反编译稳定
  → 构建 CommandInfoMessage            // 注册信息（应用名、PID、IP、模式等）
  → 保活定时器（10s 间隔）              // 启动 SocketClient 并维持连接
  → SocketClient 连接 Server           // AUTH → INFO → 就绪
```

### Daemon 守护进程启动

```
Debug4jDaemon.start()
  → Debugger.start()                    // 初始化 Instrumentation（线程模式）
  → Debug4jDaemonThread（proxyMode=true 时启动）
    → 从 classpath 解压 debug4j-boot.zip 到临时目录
    → ProcessBuilder 启动子进程：
      java -jar debug4j-boot.jar [daemon-args]
      → Debug4jBoot.main()
        → 解析 daemon 参数还原为 Debug4jArgs
        → Debugger.start()              // 子进程中再次初始化（进程模式）
        → 循环（10 秒间隔）监控原进程 PID 是否存活
        → 原进程退出后，boot 进程自行退出
```

---

## 节点模型

```
ServerName（服务名）
  └── NodeIp（节点 IP）
       └── NodeId（节点标识，同一用户进程的唯一标识）
             ├── Client 端（普通调试功能）
             ├── Proxy Client 端（代理穿透功能）
             └── Child Client 端（进程重载后接管调试功能）
```

| 功能 | 使用的 Client |
|------|--------------|
| 🚇 代理穿透 | **Proxy Client**（独立进程确保代理不中断） |
| 🔥 源码管理 / 🛠️ 进程管理 / 📜 日志管理 / 📦 组件增强 | **Client** 或 **Child Client** |
| 🔄 进程重载 | **原 Client**（Child 存在时仅保留此功能） |

---

## 两套 Instrumentation

| 进程 | 模式 | Instrumentation 来源 | 职责 |
|------|------|----------------------|------|
| 目标进程（原进程） | thread | `ByteBuddyAgent.install()`（self-attach） | 接受调试指令、源码热更新、对象操作 |
| Boot 进程 | process | `ByteBuddyAgent.install()`（在独立进程中） | 运行 TCP 代理、处理 JDWP 流量 |
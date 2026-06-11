# 🔥 Debug4j

<p align="center">
  <strong>一款开源的 Java 远程调试工具 —— 让异网环境（容器环境）的调试像本地开发一样简单</strong>
</p>

<p align="center">
  <a href="https://debug4j.xyz" target="_blank">
    <img src="https://img.shields.io/badge/官方文档-debug4j.xyz-059669?style=for-the-badge&logo=readthedocs&logoColor=white" alt="官方文档">
  </a>
</p>

<p align="center">
  <a href="#-特性一览"><img src="https://img.shields.io/badge/feature-代理穿透-blue" alt="代理穿透"></a>
  <a href="#-特性一览"><img src="https://img.shields.io/badge/feature-源码热更新-brightgreen" alt="源码热更新"></a>
  <a href="#-特性一览"><img src="https://img.shields.io/badge/feature-进程管理-orange" alt="进程管理"></a>
  <a href="#-特性一览"><img src="https://img.shields.io/badge/feature-日志管理-yellow" alt="日志管理"></a>
  <a href="#-特性一览"><img src="https://img.shields.io/badge/feature-组件增强-purple" alt="组件增强"></a>
  <a href="https://github.com/ifeng113/debug4j/blob/master/LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-green" alt="Apache-2.0"></a>
</p>

---

## 📋 目录

- [项目愿景](#-项目愿景)
- [架构原理](#-架构原理)
- [特性一览](#-特性一览)
- [技术栈](#-技术栈)
- [产品流程](#-产品流程)
- [约束说明](#-约束说明)
- [快速开始](#-快速开始)
- [项目结构](#-项目结构)
- [使用场景](#-使用场景)
- [使用限制](#-使用限制)
- [官方文档](#-官方文档)
- [交流与贡献](#-交流与贡献)

---

## 🎯 项目愿景

> **打造一款开源的、强大的 Java 调试工具，方便 Java 工程师针对异网环境（容器环境）进行类本地化开发。**

在生产环境、容器环境、K8s 集群等"异网"场景下，调试 Java 应用往往面临重重障碍：网络隔离无法直连、代码修改后需要重新构建发布、日志获取困难…… Debug4j 正是为解决这些痛点而生。

---

## 🏗️ 架构原理

Debug4j 采用 **三端架构**，通过 TCP 长连接实现安全稳定的双向通信。

```
┌──────────────┐      ┌──────────────────┐      ┌──────────────────┐
│   🌐 浏览器    │      │    🖥️ Server 端    │      │   📦 Client 端    │
│  (管理界面)    │◄────►│  (API + Web管理)  │◄────►│  (调试功能引擎)   │
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

### 🤔 为什么需要单独创建 Proxy Client 端？

1. **功能分离**：Client 端专注调试修改，Proxy Client 端仅处理 TCP 代理流量，职责单一、互不干扰
2. **JDWP 阻塞问题**：当使用 JDWP 远程调试时，用户进程会全部阻塞，此时若代理流量与调试功能在同一进程内，TCP 代理也会中断。分离后 Proxy Client 作为独立进程，即使 JDWP 暂停了用户进程，代理流量仍可正常传输

---

## ⭐ 特性一览

### 🔗 代理穿透

> 针对用户进程环境的网络代理功能，通过 Debug4j 能直接通过 Server 端访问用户进程的网络环境。

- **端口映射管理**：创建、编辑、删除代理映射规则
- **多协议支持**：TCP/HTTP 协议，覆盖绝大多数场景
- **快捷配置**：JDWP、SSH、SFTP、JMX 一键配置
- **状态监控**：实时显示代理连接状态

**典型用途**：

| 场景 | 说明 |
|------|------|
| 🔌 JDWP 远程调试端口 | 通过代理穿透，IDE 可直接连接容器内的调试端口 |
| 🔑 SSH/SFTP 端口 | 通过 Server 端代理映射，直接 SSH 登录容器环境 |
| 🛠️ Arthas Tenant/Web-Console | Tenant 可通过 CMD 直接连接，Web-Console 在浏览器中操作 |
| 🗄️ 中间件资源 | 访问用户进程连接的 MySQL、Redis 等服务资源 |
| 🌐 任意可达端口 | 用户进程网络可达的任意端口均可代理穿透 |

**示例**：用户进程环境 22 端口为 SSH Server，通过 Server 创建代理映射 `用户进程:22 ⇒ Server:30022`，使用 SSH Client 访问 `Server:30022` 的 TCP 流量就能自动穿透转发到用户进程的 22 端口。

---

### 📝 源码管理

> 提供针对用户进程的源代码编辑修改功能，能够直接修改进程任意代码并立即生效。

#### 🔍 源码获取
- **进程类源码**：获取运行中的类源码（支持内部类，完美处理匿名类和 Lambda）
- **字节码查看**：查看原始字节码和增强后字节码，对比分析

#### ✏️ 在线编辑
- **可视化编辑器**：基于 CodeMirror 6 的在线 IDE，Java 语法高亮、代码补全
- **源码热更新**：在线修改源码，立即编译并替换运行中的类，无需重启应用
- **字节码上传更新**：直接上传编译好的 `.class` 文件进行更新

#### 📎 行级补丁
- **源码行号获取**：精确获取源代码行号，用于定位修改位置
- **代码行补丁**：针对 Agent 修改后的代码进行补丁操作，或快速插入日志代码

---

### ⚙️ 进程管理

> 增强或修改 JVM 的各类参数、属性及方法。

#### 🖥️ 系统参数
- **查看所有进程参数**：包含 JVM 参数、环境变量、Spring 及 Nacos 等配置参数
- **动态修改系统参数**：
  - JVM `System.Property`
  - Spring `MutablePropertySources`（支持 `@RefreshScope` 自动更新 Bean）
  - 环境变量
  - 启动参数

#### 🔄 进程重载
> 修改进程启动时的 JVM 参数，如动态开启 JDWP、GC Log、JMX 等场景

- 支持 restart 模式，进程重载后会新开一个进程（称为 **Child Client 端**）
- 重载后 Child Client 接管调试功能，原 Client 仅保留进程重载能力

#### 🧬 对象增强
- **获取内存对象信息**：查看对象的变量和方法
- **修改内存对象属性值**：支持 Spring Bean 的属性动态修改
- **执行内存对象方法**：支持 Spring Bean 的方法动态调用
- **监控类方法耗时、参数、返回值**：无侵入式方法级监控

#### 📊 JVM 监控
- **内存快照**（Heap Dump）：一键生成并下载堆转储文件
- **JFR 飞行记录**（Java Flight Recorder）：录制 JVM 运行时事件，用于性能分析
- **监控文件管理**：统一管理各类监控文件

---

### 📋 日志管理

> 增强用户进程的日志管理能力。

- **日志等级查看与修改**：TRACE / DEBUG / INFO / WARN / ERROR / OFF，动态调整
- **标准输出重放**：将 stdout 日志重定向至文件，支持关键词过滤，仅保留含关键词的日志内容
- **实时日志观测**：基于日志文件（`app.log`、`gc.log` 等）的实时 tail，自动滚动更新
- **日志检索**：基于日志目录或文件的内容检索（类似 `zgrep`），支持自动递归目录，结果高亮显示

---

### 🧩 组件增强

> 提供非用户进程业务的外部操作增强。

#### 📁 文件管理
- 开启 SFTP 服务，通过 Server 端直接管理文件
- 文件列表、上传、下载、删除等操作
- 目录树导航，可视化浏览

#### 🛠️ 组件管理
- **OpenSSH**：安装并开启 SSH 服务
- **Arthas**：安装并开启 Arthas Telnet / Web Console 服务
- **HTTP(S) 代理**：开启 HTTP(S) 代理服务

---

## 🛠️ 技术栈

### 核心框架

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17+ | 核心编程语言 |
| Spring Boot | 3.2+ | 后端服务框架 |
| Smart-Socket | 1.5+ | 高性能 Socket 通信框架 |
| Sa-Token | 1.3+ | 权限认证框架 |
| Javassist | 3.29+ | 字节码操作库 |
| ASM | 9.5+ | 字节码分析与修改 |

### 功能模块

| 模块 | 说明 |
|------|------|
| **debug4j-core** | 核心调试引擎，类加载、字节码修改 |
| **debug4j-daemon** | 守护进程模块，Client 与服务端通信 |
| **debug4j-server** | 服务端，提供 API 和 Web 管理界面 |
| **debug4j-spring-boot-starter** | Spring Boot 自动配置 Starter |
| **debug4j-boot** | Boot 启动模块 |
| **debug4j-common** | 通用工具类 |
| **debug4j-packing** | 打包模块 |

---

## 🔄 产品流程

### 认证与连接

1. **登录认证**：系统需要登录才能访问，使用 HTTP Basic 认证方式
2. **自动注册**：引入 Debug4j 依赖后会自动创建：
   - **Client 端**（调试功能引擎）
   - **Proxy Client 端**（流量代理引擎）
   - **Child Client 端**（进程重载后产生）
3. **令牌校验**：Client 注册时携带令牌进行身份验证，并声明自身版本号
4. **版本兼容**：Server 端根据 Client 端不同版本进行兼容性处理

### 节点选择

> Debug4j 采用 **三层结构** 组织节点：

```
ServerName（服务名）
  └── NodeIp（节点 IP）
       └── NodeId（节点标识，同一用户进程的唯一标识）
```

- **ServerName → NodeIp**：一对多关系
- **NodeIp → NodeId**：一对多关系
- **相同 NodeId**：表示同一用户进程（包含 Client + Proxy Client + Child Client）

### 功能分配

登录系统后，用户需要先选择一个 `NodeId` 才能开始调试：

| 功能 | 使用的 Client |
|------|--------------|
| 🔗 代理穿透 | **Proxy Client**（独立进程确保代理不中断） |
| 📝 源码管理 / ⚙️ 进程管理 / 📋 日志管理 / 🧩 组件增强 | **Client** |
| 👑 接管所有调试功能（Child 存在时） | **Child Client** |
| 🔄 进程重载 | **原 Client**（Child 存在时仅保留此功能） |

---

## 📋 约束说明

> 以下为引入 Debug4j 依赖的**目标应用**所需满足的条件。

| 要求 | 说明 |
|------|------|
| **JDK 版本** | **JDK 17+**（推荐 `eclipse-temurin:17.0.13_11-jdk`）<br/>若使用 JDK 8，请使用 [debug4j-jdk8](https://github.com/ifeng113/debug4j-jdk8) 分支 |
| **基础镜像** | 容器化部署必须使用 **JDK** 镜像（非 JRE），因为需要运行时编译能力 |
| **网络要求** | 目标应用需能够访问 Server 端的通信端口（默认 7988） |

---

## 🚀 快速开始

### 安装服务端

```bash
# 拉取 Docker 镜像
docker pull k4ln/debug4j-server:0.2.1

# 启动服务端（基础模式）
docker run --net=host -d --name debug4j-server k4ln/debug4j-server:0.2.1

# 启动服务端（设置通信密钥和 API 密钥）
docker run --net=host -d --name debug4j-server k4ln/debug4j-server:0.2.1 \
    --debug4j.key=k4ln --sa-token.http-basic='k4ln:123456'
```

### 端口说明

| 端口 | 用途 | 配置项 |
|------|------|--------|
| **7987** | API 及 Web 调试管理页面 | 固定端口 |
| **7988** | Server 与被调试应用的通信端口 | `--debug4j.socket-port` |
| **33000-34000** | 默认代理开放端口区段 | `--debug4j.min-proxy-port` / `--debug4j.max-proxy-port` |

启动后访问 **[http://localhost:7987](http://localhost:7987)** 进入调试管理页面。

---

### Java 应用集成

在您的项目中添加以下依赖：

```xml
<dependency>
    <groupId>io.github.ifeng113</groupId>
    <artifactId>debug4j-daemon</artifactId>
    <version>0.2.1</version>
</dependency>
```

在应用程序中启动 Debug4j：

```java
Debug4jDaemon.start(true, "demo1-daemon", "com.k4ln", "127.0.0.1", 7988, "k4ln", debug4jCommand, true);
```

> 📎 完整示例请参考 [debug4j-demo1](https://github.com/ifeng113/debug4j/tree/master/debug4j-demo1)

---

### Spring Boot 项目集成

添加以下依赖：

```xml
<dependency>
    <groupId>io.github.ifeng113</groupId>
    <artifactId>debug4j-spring-boot-starter</artifactId>
    <version>0.2.1</version>
</dependency>
```

在 `application.yml` 中配置：

```yaml
debug4j:
  package-name: com.k4ln
  host: 192.168.1.13
  port: 7988
  key: k4ln
```

> 📎 完整示例请参考 [debug4j-demo2](https://github.com/ifeng113/debug4j/tree/master/debug4j-demo2)

---

## 📁 项目结构

```
debug4j/
├── debug4j-boot/                    # Boot 启动模块
├── debug4j-common/                  # 通用工具类
├── debug4j-core/                    # 核心调试引擎
│   ├── attach/                      # 类加载与字节码增强
│   ├── client/                      # Socket 客户端
│   └── proxy/                       # 代理服务
├── debug4j-daemon/                  # 守护进程模块
├── debug4j-demo1/                   # 普通 Java 应用示例
├── debug4j-demo2/                   # Spring Boot 应用示例
├── debug4j-packing/                 # 打包模块
├── debug4j-server/                  # 服务端模块
│   ├── controller/                  # REST API 控制器
│   ├── service/                     # 业务服务层
│   ├── socket/                      # Socket 服务端
│   └── config/                      # 配置类
└── debug4j-spring-boot-starter/     # Spring Boot Starter
```

---

## 💡 使用场景

| 场景 | 说明 |
|------|------|
| 🔍 **线上问题排查** | 通过代理穿透远程连接生产环境，实时查看日志和调试代码 |
| 🔄 **热更新调试** | 在不重启应用的情况下修改源码，快速验证修复方案 |
| 📊 **性能分析** | 通过 JVM 监控功能分析内存使用和性能瓶颈（Heap Dump / JFR） |
| ⚙️ **配置管理** | 动态修改系统参数、Spring 配置，无需重启应用即可生效 |
| 🛠️ **组件管理** | 一键安装和管理调试工具组件（OpenSSH, Arthas） |
| 🔗 **网络代理** | 访问容器内部任意网络资源，突破网络隔离限制 |

---

## ⚠️ 使用限制与注意事项

1. **类签名限制**：代码热更新或字节码热更新**无法修改类的字段名或方法名**（即类签名）
2. **Agent 兼容性**：使用 Agent（如 ByteBuddy）可能修改字节码，导致源码热更新和字节码热更新功能不可用。推荐尽量避免使用 Agent 或调整相关配置
3. **字节码版本兼容性**：确保用于热更新的类文件编译版本与目标 JVM 兼容
4. **代码行补丁**：使用第三方工具类时，请使用**全路径**，避免重名类导致编译失败
5. **应用集成**：应用集成必须使用 **JDK** 作为基础镜像，推荐 `eclipse-temurin:17.0.13_11-jdk`。如需开启远程调试，在 Java 启动时手动配置：
   ```bash
   -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
   ```

---

## 🙏 致谢

- [Smart-Socket](https://github.com/smartboot/smart-socket) — 高性能 Socket 通信框架
- [Sa-Token](https://github.com/dromara/sa-token) — 轻量级权限认证框架

---

## 📖 官方文档

> 🌐 访问 **[https://debug4j.xyz](https://debug4j.xyz)** 获取完整的产品文档和技术指南。

官方文档站点提供了比 README 更全面、更详细的内容，涵盖：

| 模块 | 说明 |
|------|------|
| 🚀 **快速开始** | 服务端部署、应用集成、环境配置等详细步骤 |
| 🏗️ **架构设计** | 三端架构详解、通信协议、安全模型 |
| 🔗 **代理穿透** | 端口映射、协议支持、快捷配置等操作指南 |
| 📝 **源码管理** | 源码获取、在线编辑、热更新、行级补丁 |
| ⚙️ **进程管理** | 系统参数、进程重载、对象增强、JVM 监控 |
| 📋 **日志管理** | 日志等级、实时观测、日志检索 |
| 🧩 **组件增强** | 文件管理、OpenSSH、Arthas 集成 |
| 🔧 **配置参考** | 所有配置项说明、API 接口文档 |
| ❓ **常见问题** | 使用过程中的常见问题与解决方案 |

> 💡 提示：文档站持续更新中，建议收藏以便随时查阅最新内容。

---

<p align="center">
  <strong>QQ 群：1017333395</strong>
</p>

<p align="center">
  <img src="qq.png" width="200" alt="QQ群二维码" />
</p>

<p align="center">
  💡 提示：如果您在使用过程中有任何问题或建议，欢迎提出 <a href="https://github.com/ifeng113/debug4j/issues">Issue</a>！
</p>

<p align="center">
  📖 完整文档请访问 <a href="https://debug4j.xyz">https://debug4j.xyz</a>
</p>

---

<p align="center">
  <sub>Apache License 2.0 © 2024 debug4j</sub>
</p>

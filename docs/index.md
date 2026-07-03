---
layout: home

title: Debug4j
titleTemplate: Java 远程调试工具

hero:
  name: Debug4j
  text: 让异网环境的调试<br>像本地开发一样简单
  tagline: 一款开源的 Java 远程调试工具 —— 生产环境、容器环境、K8s 集群的"类本地化"调试方案
  image:
    src: /logo.png
    alt: Debug4j Logo
  actions:
    - theme: brand
      text: 🚀 快速开始
      link: /guide/getting-started
    - theme: alt
      text: 📖 功能介绍
      link: /features/proxy-tunnel

features:
  - icon: 🚇
    title: 代理穿透
    details: 仿 frp 的 TCP 隧道，SSH/JDWP/JMX/Redis/MySQL —— 容器内网任意 TCP 端口映射到本地，突破网络隔离。
    link: /features/proxy-tunnel
    linkText: 了解更多

  - icon: 🔥
    title: 源码热更新
    details: 在线编辑源码 → 即时编译 → Instrumentation retransform 热更新。支持内部类、行级补丁，无需重启应用。
    link: /features/source-management
    linkText: 了解更多

  - icon: 🛠️
    title: 进程管理
    details: JVM 参数/Spring 配置/Nacos 配置动态修改，进程重载（Reload/Restart），对象增强，Heap Dump，JFR 飞行记录。
    link: /features/process-management
    linkText: 了解更多

  - icon: 📜
    title: 日志管理
    details: 日志级别动态调整（Logback/Log4j2），stdout 复制与关键词过滤，实时 tail，全目录 zgrep 检索。
    link: /features/log-management
    linkText: 了解更多

  - icon: 📦
    title: 组件增强
    details: SFTP 文件管理，一键安装 OpenSSH / Arthas，HTTP(S) 正向代理。让容器拥有 IDE 般的文件访问能力。
    link: /features/component-enhancement
    linkText: 了解更多

  - icon: 🏛️
    title: 双模式架构
    details: 线程模式 + 进程模式混合架构。JDWP 场景下代理流量走独立进程，避免阻塞；普通调试走线程模式，资源开销极低。
    link: /guide/architecture
    linkText: 了解架构
---

## 快速上手

仅需两步，即可开始远程调试：

### 1. 启动 Server 端

```bash
docker run --net=host -d --name debug4j-server k4ln/debug4j-server:0.2.3 \
    --debug4j.key=k4ln --sa-token.http-basic='k4ln:123456'
```

### 2. 在应用中引入 Starter

```xml
<dependency>
    <groupId>io.github.ifeng113</groupId>
    <artifactId>debug4j-spring-boot-starter</artifactId>
    <version>0.2.3</version>
</dependency>
```

```yaml
# application.yml
debug4j:
  package-name: com.yourcompany
  host: 192.168.1.100   # Server 地址
  port: 7988             # Server 通信端口
  key: k4ln              # 认证密钥
```

::: tip 就这样？
启动应用，打开 `http://localhost:7987`，选择你的进程节点 —— 开始调试吧！🎉
:::

::: warning 已知问题
本地IDE运行时，如果获取源码报错 `javassist.NotFoundException`，可尝试手动引入依赖：

```groovy
implementation 'org.javassist:javassist:3.30.2-GA'
```

此问题会在下个版本（0.2.4）修复。
:::

---

## 架构总览

```
┌──────────────┐     HTTP     ┌──────────────────┐    TCP长连接    ┌──────────────────┐
│   🌐 浏览器    │◄───────────►│    🖥️ Server 端    │◄──────────────►│   📦 Client 端    │
│  (管理界面)    │             │  API + Web 管理    │               │  (调试功能引擎)    │
└──────────────┘             │  端口 7987/7988   │               └────────┬─────────┘
                              └──────────────────┘                        │
                                      ▲                                  │
                                      │        TCP长连接                  │  TCP长连接
                                      ▼                                  ▼
                              ┌──────────────────┐              ┌──────────────────┐
                              │  🧩 Proxy Client  │◄────────────►│   🧑‍💻 用户进程     │
                              │  (流量代理引擎)    │              │  (目标Java应用)    │
                              └──────────────────┘              └──────────────────┘
```

---

## 技术栈

| 技术 | 用途 |
|------|------|
| **Spring Boot** | 服务端框架 + Starter 自动配置 |
| **Smart-socket** | 高性能 NIO 异步 Socket 通信 |
| **ByteBuddy** | 字节码工具、Agent 安装、方法插桩 |
| **Javassist** | 字节码操作（行补丁、类签名获取） |
| **jadx** | 字节码反编译为 Java 源码 |
| **Vue 3 + CodeMirror 6** | 前端在线编辑器与交互界面 |

---

## 交流与贡献

<p align="center">
  <strong>QQ 群：1017333395</strong>
</p>

<p align="center">
  <img src="/qq.png" width="200" alt="QQ群二维码" />
</p>

<p align="center">
  💡 如果您在使用过程中有任何问题或建议，欢迎提出 <a href="https://github.com/ifeng113/debug4j/issues">Issue</a>！
</p>
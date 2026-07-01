# 快速开始

## 🚀 快速体验

想立刻感受 Debug4j 的魅力？我们提供了公网调试服务器，无需部署服务端，直接连接即可开始调试！

### 公网调试服务器

| 项目 | 地址 |
|------|------|
| **服务器地址** | `122.152.214.33` |
| **API 及 Web 管理页面** | `http://122.152.214.33:7987` |
| **通信端口** | `7988` |

### 体验步骤

只需在您的应用中引入 Debug4j 依赖，指向公网服务器即可：

```yaml
debug4j:
  package-name: com.yourcompany
  host: 122.152.214.33   # 公网 Server 地址
  port: 7988             # Server 通信端口
  key: k4ln              # 认证密钥
```

启动应用后，打开浏览器访问 **http://122.152.214.33:7987**，输入 HTTP Basic 认证信息，选择您的应用节点，即可开始远程调试！

> **温馨提示**：公网服务器仅用于快速体验和功能验证，请勿用于生产环境或传输敏感数据。如需正式使用，建议参考下方文档部署私有服务端。

---

## 安装服务端

### Docker 部署（推荐）

```bash
# 拉取 Docker 镜像
docker pull k4ln/debug4j-server:0.2.3

# 基础模式启动
docker run --net=host -d --name debug4j-server k4ln/debug4j-server:0.2.3

# 设置通信密钥和 API 密钥
docker run --net=host -d --name debug4j-server k4ln/debug4j-server:0.2.3 \
    --debug4j.key=k4ln --sa-token.http-basic='k4ln:123456'
```

### 端口说明

| 端口 | 用途 | 配置项 |
|------|------|--------|
| **7987** | API 及 Web 调试管理页面 | 固定端口 |
| **7988** | Server 与被调试应用的通信端口 | `--debug4j.socket-port` |
| **33000~34000** | 默认代理开放端口区段 | `--debug4j.min-proxy-port` / `--debug4j.max-proxy-port` |

启动后访问 **http://localhost:7987** 进入调试管理页面。

---

## Java 应用集成

### Maven

```xml
<dependency>
    <groupId>io.github.ifeng113</groupId>
    <artifactId>debug4j-daemon</artifactId>
    <version>0.2.3</version>
</dependency>
```

在应用程序中启动 Debug4j：

```java
Debug4jDaemon.start(true, "demo1-daemon", "com.k4ln", "127.0.0.1", 7988, "k4ln", debug4jCommand, true);
```

### Spring Boot 项目集成

```xml
<dependency>
    <groupId>io.github.ifeng113</groupId>
    <artifactId>debug4j-spring-boot-starter</artifactId>
    <version>0.2.3</version>
</dependency>
```

配置 `application.yml`：

```yaml
debug4j:
  package-name: com.yourcompany   # 包名过滤
  host: 192.168.1.100            # Server 地址
  port: 7988                     # Server 通信端口
  key: k4ln                      # 认证密钥
```

### Gradle

```groovy
implementation 'io.github.ifeng113:debug4j-spring-boot-starter:0.2.3'
```

---

## 约束说明

| 要求 | 说明 |
|------|------|
| **JDK 版本** | JDK 17+（推荐 `eclipse-temurin:17.0.13_11-jdk`） |
| **基础镜像** | 容器化部署须使用 **JDK** 镜像（非 JRE），需要运行时编译能力 |
| **网络要求** | 目标应用需能够访问 Server 端的通信端口（默认 7988） |

---

## 验证部署

1. 启动 Server 端（Docker）
2. 启动您的应用（包含 debug4j 依赖）
3. 访问 `http://localhost:7987`
4. 输入 HTTP Basic 用户名密码
5. 在左侧面板选择您的应用节点
6. 开始调试！🎉
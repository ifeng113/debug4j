# 代理穿透

## 概述

通过 debug4j 可直接访问用户进程所在的网络环境（容器内网），无需额外暴露端口。整体架构类似 frp 的 TCP 隧道。

## 架构

```
外部客户端 ──TCP──→ SocketTFProxyServer（server 端监听端口）
                   → SocketServer（内部控制 socket）
                   → SocketClient（core 端）
                   → SocketTFProxyClient（连接到目标端口）
                   → 目标应用
```

## 支持的协议与场景

| 场景 | 说明 |
|------|------|
| 🔌 **JDWP 远程调试端口** | 通过代理穿透，IDE 可直接连接容器内的调试端口 |
| 🔑 **SSH/SFTP 端口** | 通过 Server 端代理映射，直接 SSH 登录容器环境 |
| 🔬 **Arthas Tenant/Web-Console** | Tenant 可通过 CMD 直接连接，Web-Console 在浏览器中操作 |
| 🗄️ **中间件资源** | 访问用户进程连接的 MySQL、Redis 等服务资源 |
| 🌐 **任意可达端口** | 用户进程网络可达的任意端口均可代理穿透 |

## 示例

用户进程环境 22 端口为 SSH Server，通过 Server 创建代理映射：

```
用户进程:22 → Server:30022
```

使用 SSH Client 访问 `Server:30022`，TCP 流量就能自动穿透转发到用户进程的 22 端口。

```bash
ssh -p 30022 root@server-ip
```

## 技术实现

### SocketTFProxyServer（服务端）

监听外部端口（如 `30022`），外部 TCP 连接数据被包装为 **PROXY 协议包**（`ProtocolTypeEnum.PROXY`，编码 `0x0010`）发送给 Client。

### SocketTFProxyClient（客户端）

接收 PROXY 数据包，解析 `targetHost` + `targetPort`，建立本地 TCP 连接并双向转发。

### 透传解码器

Proxy Client 与目标端口之间的数据传输使用 `TFProtocolDecoder`，**原始字节直接透传**，不附加任何协议头：

```java
public class TFProtocolDecoder implements Protocol<byte[]> {
    @Override
    public byte[] decode(ByteBuffer readBuffer, AioSession session) {
        byte[] body = new byte[readBuffer.remaining()];
        readBuffer.get(body);
        readBuffer.mark();
        return body;
    }
}
```

### HTTP 正向代理

除了 TCP 隧道，debug4j 还内置了 `Debug4jHttpProxy` 实现完整的 HTTP/HTTPS 正向代理：

- 在用户进程内监听指定端口
- 处理 HTTP CONNECT 方法，建立 HTTPS 隧道
- 处理标准 HTTP 请求（GET/POST 等）
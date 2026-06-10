# 通信协议

> 版本号：1 | 基于 smart-socket NIO 框架实现

---

## 1. 协议概述

Debug4j 采用 **自定义二进制协议** 在 Server 端与 Client 端之间通信。协议基于 TCP，使用 **长度前缀（Length-Prefixed）** 的消息格式。

---

## 2. 消息结构

### 2.1 完整消息格式

一条完整的 TCP 消息由 **4 字节消息长度前缀 + 12 字节协议头 + N 字节数据体** 组成：

```
┌──────────────────────────────────────────────────────────────┐
│ 消息总长度（4字节）                                              │
├──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┤
│ 长度  │ 版本 │ 功能 │ 分包  │ 总包  │ 包序  │ 客户  │ 数据  │
│ 前缀  │ 号   │ ID   │ 标识  │ 数   │ 号   │ 端ID  │ 体   │
│ 4B   │ 1B   │ 2B   │ 1B   │ 2B   │ 2B   │ 4B   │ N B  │
├──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┤
│◄───── 4B ────►◄─────────── 12B 协议头 ──────────►◄─── NB ──►│
│◄──────────────────── 16 + N 字节 ────────────────────────────►│
└──────────────────────────────────────────────────────────────┘
```

### 2.2 字段说明

| 字段 | 字节数 | 说明 |
|------|--------|------|
| **消息长度** | 4 字节（int） | 数据体（body）的长度，**不包括** 4 字节长度前缀本身和 12 字节协议头 |
| **版本号** | 1 字节 | 协议版本号，当前固定为 `1` |
| **功能 ID** | 2 字节（short） | 功能类型编码，见功能类型表。以大端序（Big-Endian）存储 |
| **分包标识** | 1 字节 | `0`=非分包数据（单包），`1`=分包数据（多包中的一包） |
| **总包数** | 2 字节（short） | 分包时数据被拆分的总包数，大端序 |
| **包序号** | 2 字节（short） | 分包时当前包的序号（从 1 开始），大端序 |
| **客户端 ID** | 4 字节（int） | 当功能类型为 `PROXY` 时有意义，用于标识代理连接的对端 |
| **数据体** | N 字节 | 传输内容，通常是 JSON 序列化的业务数据 |

### 2.3 最大单包数据体

```
READ_BUFFER_SIZE = 4096       // smart-socket 读缓冲区大小
BUFFER_LENGTH    = 4          // 消息长度前缀字节数
BUFFER_HEADER    = 12         // 协议头字节数 = 1+2+1+2+2+4
maxBodyLength    = 4096 - 4 - 12 = 4080  // 单包最大数据体
```

---

## 3. 功能类型

| 枚举值 | 编码 | 说明 |
|--------|------|------|
| `HEART` | `0x0000` | 心跳检测 |
| `AUTH` | `0x0001` | 身份认证 |
| `COMMAND` | `0x0002` | 业务指令 |
| `PROXY` | `0x0010` | TCP 代理流量 |
| `FILE` | `0x0020` | 文件传输 |

### 心跳机制

- Server 端通过 smart-socket 内置的 `HeartPlugin` 发送心跳
- 间隔：**5 秒**
- 容错：连续 **3 次**未收到心跳响应则判定连接断开，触发断开回调清理 Session

---

## 4. 分包机制

当消息数据体长度超过 **4080 字节**时，发送端自动将数据拆分为多个数据包发送：

### 4.1 分包发送

```java
// V2 版本使用整数运算，避免浮点精度问题
int subcontractCount = (body.length + maxBodyLength - 1) / maxBodyLength;
for (int i = 0; i < body.length; i += maxBodyLength) {
    int bodyLength = Math.min(maxBodyLength, body.length - i);
    byte[] simple = new byte[bodyLength];
    System.arraycopy(body, i, simple, 0, bodyLength);
    send(session, SocketProtocol.builder()
            .protocolType(protocolType)
            .subcontract(true)
            .subcontractCount(subcontractCount)
            .subcontractIndex(i / maxBodyLength + 1)
            .clientId(clientId)
            .body(simple)
            .build());
}
```

### 4.2 分包接收

- 接收端通过 `clientId` 缓存分包数据
- 当收到的包序号等于总包数时，表示全部分包已收齐
- 将缓存的数据按序组装为完整消息后处理

---

## 5. 协议编码/解码

### 5.1 编码

协议编码将消息头各字段按字节分布写入字节数组：

```
字节索引分布：
[0]      = version（1字节）
[1..2]   = protocolType code（2字节）
[3]      = subcontract（1字节，0或1）
[4..5]   = subcontractCount（2字节，大端序 short）
[6..7]   = subcontractIndex（2字节，大端序 short）
[8..11]  = clientId（4字节，大端序 int）
```

发送总字节数 = 4（长度前缀）+ 12（协议头）+ N（数据体）= 16 + N。

### 5.2 解码

解码器实现 smart-socket 的 `Protocol<SocketProtocol>` 接口，通过长度前缀判断是否收齐完整消息：

1. 读取 4 字节获取数据体长度
2. 计算完整消息长度：`dataLength + BUFFER_HEADER`
3. 若缓冲区数据不足则标记复位，等待更多数据（半包处理）
4. 读取协议头各字段，还原为消息对象
5. 读取数据体

### 5.3 发送方式

所有协议数据均通过 `writeBuffer.writeAndFlush(data)` **立即推送**，确保实时性。

---

## 6. 文件传输协议

文件传输使用独立的 `FILE` 协议类型（`0x0020`），始终使用 **分包模式**：

- 接收端通过 `RandomAccessFile` 逐块写入，最后通过 `Files.move()` 移动到目标路径
- 上传状态使用 `TimedCache` 管理，10 分钟无操作自动清理

---

## 7. 透传转发协议

透明转发（Proxy 隧道）的数据不经过二进制协议解码，直接传输原始字节。Proxy Client 与目标端口之间建立 TCP 连接后，原始 TCP 流量直接透传，不附加任何协议头。

---

## 8. 身份认证流程

```
Client                     Server
  │                          │
  ├── AUTH(key) ────────────►│  // 发送认证密钥
  │                          │  // 服务端校验 Key
  │◄─── (连接继续/断开) ─────┤  // Key 正确则建立 Session
  │                          │
  ├── COMMAND(INFO) ────────►│  // 发送注册信息
  │                          │  // {application, packageName, pid, ip, mode, jdwpPort...}
  │                          │  // Server 记录到 infoMessageMap
  │          ...             │
  ├── HEART ────────────────►│  // 心跳（5s）
  │◄─── HEART_ACK ──────────┤
```

---

## 9. 命令体系（Command 层）

业务指令在 `COMMAND`（`0x0002`）协议类型之上封装了一层命令层：

### 9.1 命令模型

```java
@Builder
public class Command<T> {
    private CommandTypeEnum command;  // 指令类型
    private T data;                   // 指令数据（JSON 反序列化为具体类型）
}
```

### 9.2 命令类型枚举

| 分类 | 编码 | 方向 | 说明 |
|------|------|------|------|
| **LOG** | `0x0000` | Client→Server | 日志传输 |
| **INFO** | `0x0001` | Client→Server | 注册信息 |
| **PROXY_OPEN** | `0x0011` | Server→Client | 开启代理通道 |
| **PROXY_CLOSE** | `0x0012` | Server→Client | 关闭代理通道 |
| **ATTACH_REQ_CLASS_ALL** | `0x0101` | Server→Client | 请求所有已加载类列表 |
| **ATTACH_REQ_CLASS_SOURCE** | `0x0102` | Server→Client | 请求类源码（反编译） |
| **ATTACH_REQ_CLASS_SOURCE_LINE** | `0x0103` | Server→Client | 请求类源码及行号 |
| **ATTACH_REQ_CLASS_RELOAD** | `0x0104` | Server→Client | 请求字节码重载 |
| **ATTACH_REQ_CLASS_RELOAD_JAVA** | `0x0105` | Server→Client | 请求源码编译重载 |
| **ATTACH_REQ_CLASS_RELOAD_JAVA_LINE** | `0x0106` | Server→Client | 请求行代码补丁重载 |
| **ATTACH_REQ_CLASS_RESTORE** | `0x0107` | Server→Client | 请求类还原（恢复原始字节码） |
| **ATTACH_REQ_TASK** | `0x0201` | Server→Client | 请求任务列表 |
| **ATTACH_REQ_TASK_OPEN** | `0x0202` | Server→Client | 开启日志监听任务 |
| **ATTACH_REQ_TASK_CLOSE** | `0x0203` | Server→Client | 关闭日志监听任务 |
| **ATTACH_REQ_PROCESS_ARG** | `0x0301` | Server→Client | 请求进程参数 |
| **ATTACH_REQ_PROCESS_RELOAD** | `0x0302` | Server→Client | 请求进程重载 |
| **ATTACH_REQ_PROCESS_ADJUSTMENT** | `0x0303` | Server→Client | 请求进程调整 |
| **ATTACH_RESP_CLASS_ALL** | `0x0901` | Client→Server | 响应所有类列表 |
| **ATTACH_RESP_CLASS_SOURCE** | `0x0902` | Client→Server | 响应类源码 |
| **ATTACH_RESP_CLASS_SOURCE_LINE** | `0x0903` | Client→Server | 响应类源码及行号 |
| **ATTACH_RESP_TASK** | `0x0904` | Client→Server | 响应任务列表 |
| **ATTACH_RESP_TASK_DETAILS** | `0x0905` | Client→Server | 响应任务详情 |
| **ATTACH_RESP_PROCESS_ARG_DETAILS** | `0x0906` | Client→Server | 响应进程详情 |
| **ATTACH_RESP_PROCESS_ADJUSTMENT_RESULT** | `0x0907` | Client→Server | 响应进程调整结果 |

### 9.3 命令编码规则

```
编码值分配：
- 0x01xx：类操作域（请求）
- 0x02xx：任务（日志监听）域
- 0x03xx：进程操作域
- 0x09xx：响应域（所有响应的最高 byte 均为 0x09）
```

---

## 10. 数据模型定义

### 10.1 SocketProtocol（协议层）

```java
@Builder
@Data
public class SocketProtocol {
    private Integer version = 1;           // 协议版本
    private ProtocolTypeEnum protocolType; // 协议类型
    private Boolean subcontract;           // 是否分包
    private Integer subcontractCount = 1;  // 总包数
    private Integer subcontractIndex = 1;  // 当前包序号
    private Integer clientId;              // 客户端ID（PROXY时有效）
    private byte[] body;                   // 数据体
}
```

### 10.2 Command（命令层）

```java
@Builder
@Data
public class Command<T> {
    private CommandTypeEnum command;  // 命令类型
    private T data;                   // 命令数据（泛型，JSON序列化）
}
```

---

## 11. 通信消息示例

### 11.1 心跳消息

```
[4B:0x0000] [0x01] [0x00 0x00] [0x00] [0x00 0x01] [0x00 0x01] [0x00 0x00 0x00 0x00]
 └─len=0─┘  └v1─┘└─HEART─┘  └单包─┘└─总包=1─┘└─序号=1─┘└─── clientId=0 ────┘
```

### 11.2 AUTH 认证消息

```
[4B:bodyLen] [0x01] [0x00 0x01] [0x00] [0x00 0x01] [0x00 0x01] [0x00 0x00 0x00 0x00] [JSON:key]
              └v1─┘└─AUTH(0x0001)┘└─单包─┘└─总包=1─┘└─序号=1─┘└─── clientId=0 ────┘└─认证Key─┘
```

### 11.3 COMMAND 请求（请求类列表）

```
[4B:bodyLen] [0x01] [0x00 0x02] [0x00] [0x00 0x01] [0x00 0x01] [0x00 0x00 0x00 0x00] [{"command":"ATTACH_REQ_CLASS_ALL"}]
              └v1─┘└─COMMAND─┘└─单包─┘└─总包=1─┘└─序号=1─┘└─── clientId=0 ────┘└── JSON 命令数据 ──┘
```

---

## 12. 与 Smart-Socket 框架的集成

### 12.1 服务端

```java
AioQuickServer server = new AioQuickServer()
    .setProtocol(SocketProtocolDecoder.class)  // 注册协议解码器
    .setHandler(messageHandler)               // 注册消息处理器
    .setHeartPlugin(new HeartPlugin(5, 3));   // 5秒心跳，3次容错
```

### 12.2 客户端

```java
AioQuickClient client = new AioQuickClient()
    .setProtocol(SocketProtocolDecoder.class)  // 注册协议解码器
    .setHandler(messageHandler);               // 注册消息处理器
```

### 12.3 透传客户端（透明转发）

```java
// 使用透传解码器，不解析协议头，直接透传原始字节
AioQuickClient client = new AioQuickClient()
    .setProtocol(TFProtocolDecoder.class)
    .setHandler(tunnelHandler);
```
# API 参考

> **通用响应格式（`ApiResponse<T>`）**：
> ```json
> {
>   "code": 0,        // 0 成功，非零失败
>   "message": "success",
>   "data": T         // 业务数据，类型见各接口
> }
> ```

> **鉴权方式**：HTTP Basic Auth，`Authorization: Basic base64(username:password)`。

> **Base URL**：由前端配置指定（默认 `http://server-ip:7987`）。

---

## 目录

- [一、认证](#一、认证)
- [二、节点管理](#二、节点管理)
- [三、日志监控（SSE 流式）](#三、日志监控-sse-流式)
- [四、日志监听任务管理](#四、日志监听任务管理)
- [五、源码管理](#五、源码管理)
- [六、代理穿透](#六、代理穿透)
- [七、进程参数](#七、进程参数)
- [八、进程重载](#八、进程重载)
- [九、通用业务接口（核心）](#九、通用业务接口-核心)
- [十、文件上传下载](#十、文件上传下载)
- [附录：数据模型](#附录-数据模型)

---

## 一、认证

### 1.1 登录

```
GET /manage/clients
Headers: Authorization: Basic <base64(username:password)>
```

复用节点列表查询接口验证身份。登录成功后将 `base64(username:password)` 作为 token。

**响应** `ApiResponse<List<Client>>`

**示例响应**
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "clientSessionId": "session-xxx",
      "applicationName": "my-app",
      "packageName": "com.example",
      "socketClientHost": "host-1",
      "socketClientIp": "192.168.1.100",
      "socketClientOutletIp": "10.0.0.1",
      "uniqueId": "uuid-xxx",
      "pid": 12345,
      "jdwpPort": 5005,
      "debug4jMode": "thread",
      "rootUniqueId": null,
      "reloadMode": "Restart"
    }
  ]
}
```

---

## 二、节点管理

### 2.1 获取节点列表

```
GET /manage/clients
```

**响应** `ApiResponse<List<Client>>`（字段同上）

---

## 三、日志监控（SSE 流式）

### 3.1 SSE 日志流

```
GET /attach/task?path={filePath}&sessionId={clientSessionId}&token={base64token}&loginId={loginId}
```

前端通过 `EventSource` 建立 SSE 连接，服务器持续推送日志行。

**推送数据**
```json
{
  "line": "[2026-06-05 10:00:00] INFO [main] com.example.MyClass - this is a log line"
}
```

---

## 四、日志监听任务管理

### 4.1 打开日志监听（保活）

```
POST /attach/task/open
Content-Type: application/json
```

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| clientSessionId | String | 客户端会话 ID |
| filePath | String | 日志文件路径 |
| loginId | String | 登录设备 ID |

**示例**
```json
{
  "clientSessionId": "session-xxx",
  "filePath": "/app/logs/app.log",
  "loginId": "device_xxx_abc123"
}
```

**响应** `ApiResponse<Object>`

### 4.2 关闭日志监听

```
POST /attach/task/close
Content-Type: application/json
```

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| clientSessionId | String | 客户端会话 ID |
| filePath | String | 日志文件路径 |
| loginId | String | 登录设备 ID |

**响应** `ApiResponse<Object>`

### 4.3 获取监听任务列表

```
POST /attach/task
Content-Type: application/json
```

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| clientSessionId | String | 客户端会话 ID |

**响应** `ApiResponse<List<TaskInfo>>`

**TaskInfo 字段**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| reqId | String | 请求 ID |
| filePath | String | 日志文件路径 |
| loginId | String | 登录设备 ID，可为 null |
| initReadLine | int | 初始读取行数 |
| lastListenTime | long | 最后监听时间戳 |

**示例响应**
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "reqId": "req-001",
      "filePath": "/app/logs/app.log",
      "loginId": "device_xxx",
      "initReadLine": 0,
      "lastListenTime": 1717000000000
    }
  ]
}
```

---

## 五、源码管理

### 5.1 获取类名列表

```
POST /attach/class
Content-Type: application/json
```

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| clientSessionId | String | 客户端会话 ID |
| packageName | String | 可选，包名过滤 |

**示例**
```json
{
  "clientSessionId": "session-xxx",
  "packageName": "com.example"
}
```

**响应** `ApiResponse<List<String>>`

```json
{
  "code": 0,
  "message": "success",
  "data": ["com.example.MyClass", "com.example.YourClass"]
}
```

### 5.2 获取类源码

```
POST /attach/source
Content-Type: application/json
```

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| clientSessionId | String | 客户端会话 ID |
| className | String | 全路径类名 |
| sourceCodeType | String | 源码类型枚举：`originalClassFile` / `agentTransformClassByteCode` / `agentTransformClassBuffer` / `attachClassByteCode` |

**示例**
```json
{
  "clientSessionId": "session-xxx",
  "className": "com.example.MyClass",
  "sourceCodeType": "attachClassByteCode"
}
```

**响应** `ApiResponse<AttachClassSourceRespVO>`

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "classSource": "package com.example;\n\npublic class MyClass {\n  ...\n}",
    "classMethods": ["public void sayHello()"],
    "byteCodeType": "attachClassByteCode",
    "status": true,
    "lineNumbers": [1, 2, 5, 10]
  }
}
```

### 5.3 更新源码（热更新）

```
POST /attach/reload/source
Content-Type: application/json
```

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| clientSessionId | String | 客户端会话 ID |
| className | String | 全路径类名 |
| sourceCode | String | 新的源码内容 |

**示例**
```json
{
  "clientSessionId": "session-xxx",
  "className": "com.example.MyClass",
  "sourceCode": "package com.example;\n\npublic class MyClass {\n  public void newMethod() {}\n}"
}
```

**响应** `ApiResponse<AttachClassSourceRespVO>`

### 5.4 上传字节码更新

```
POST /attach/reload/class
Content-Type: multipart/form-data
```

**FormData 参数**

| 字段 | 说明 |
|------|------|
| file | .class 字节码文件 |
| clientSessionId | 客户端会话 ID |
| className | 全路径类名 |

**响应** `ApiResponse<AttachClassSourceRespVO>`

### 5.5 还原源码

```
POST /attach/restore
Content-Type: application/json
```

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| clientSessionId | String | 客户端会话 ID |
| className | String | 全路径类名 |

**响应** `ApiResponse<AttachClassSourceRespVO>`

### 5.6 获取方法源码行

```
POST /attach/source/method-line
Content-Type: application/json
```

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| clientSessionId | String | 客户端会话 ID |
| className | String | 全路径类名 |
| lineMethodName | String | 方法签名 |

**示例**
```json
{
  "clientSessionId": "session-xxx",
  "className": "com.example.MyClass",
  "lineMethodName": "public void sayHello()"
}
```

**响应** `ApiResponse<AttachClassSourceRespVO>`（含 `lineNumbers`）

### 5.7 方法行补丁

```
POST /attach/patch/method-line
Content-Type: application/json
```

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| clientSessionId | String | 客户端会话 ID |
| className | String | 全路径类名 |
| lineMethodName | String | 方法签名 |
| lineNumber | int | 目标源码行号 |
| sourceCode | String | 补丁代码 |

**示例**
```json
{
  "clientSessionId": "session-xxx",
  "className": "com.example.MyClass",
  "lineMethodName": "public void sayHello()",
  "lineNumber": 15,
  "sourceCode": "System.out.println(\"hello patched\");"
}
```

**响应** `ApiResponse<AttachClassSourceRespVO>`

---

## 六、代理穿透

### 6.1 获取代理映射列表

```
GET /proxy/details?clientSessionId={clientSessionId}
```

**响应** `ApiResponse<List<ProxyMapping>>`

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "remark": "JDWP",
      "serverPort": 5005,
      "clientSessionId": "session-xxx",
      "remoteHost": "127.0.0.1",
      "remotePort": 5005,
      "allowNetworks": ["0.0.0.0/0"],
      "clientOutletIps": [],
      "status": "active"
    }
  ]
}
```

### 6.2 创建代理映射

```
POST /proxy
Content-Type: application/json
```

**请求参数**

| 字段 | Java 类型 | 必填 | 说明 |
|------|-----------|------|------|
| remark | String | 否 | 备注 |
| serverPort | int | 是 | 代理服务端口 |
| clientSessionId | String | 是 | 客户端会话 ID |
| remoteHost | String | 是 | 远程主机 |
| remotePort | int | 是 | 远程端口 |
| allowNetworks | `List<String>` | 否 | 允许网段 |
| clientOutletIps | `List<String>` | 否 | 出口 IP 列表 |

**示例**
```json
{
  "remark": "jmx server",
  "serverPort": 33010,
  "clientSessionId": "session-xxx",
  "remoteHost": "127.0.0.1",
  "remotePort": 33010,
  "allowNetworks": ["0.0.0.0/0"],
  "clientOutletIps": []
}
```

**响应** `ApiResponse<ProxyMapping>`

### 6.3 删除/停止代理映射

```
POST /proxy/remove
Content-Type: application/json
```

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| remoteHost | String | 远程主机 |
| remotePort | int | 远程端口 |
| clientSessionId | String | 客户端会话 ID |

**响应** `ApiResponse<ProxyMapping>`

---

## 七、进程参数

### 7.1 获取进程参数

```
POST /process/args
Content-Type: application/json
```

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| clientSessionId | String | 客户端会话 ID |

**示例**
```json
{
  "clientSessionId": "session-xxx"
}
```

**响应** `ApiResponse<ProcessArgsVO>`

**ProcessArgsVO 字段**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| jvmArgs | `List<String>` | JVM 参数（如 `-Xmx512m`） |
| programArgs | `List<String>` | 程序参数（如 `--server.port=8080`） |
| properties | `List<String>` | 系统属性（格式 `key=value`） |
| envs | `List<String>` | 环境变量（格式 `KEY=VALUE`） |
| hookArgs | `Map<String, List<String>>` | 钩子参数/Spring 配置，按分类聚合 |

**示例响应**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "jvmArgs": ["-Xmx512m", "-Xms256m"],
    "programArgs": ["--server.port=8080"],
    "properties": ["user.dir=/app", "java.version=17"],
    "envs": ["JAVA_HOME=/usr/lib/jvm/java-17"],
    "hookArgs": {
      "spring.datasource": ["url=jdbc:mysql://localhost:3306/db"]
    }
  }
}
```

---

## 八、进程重载

### 8.1 重载进程

```
POST /process/reload
Content-Type: application/json
```

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| clientSessionId | String | 主进程会话 ID |
| removeJvmArgs | `List<String>` | 可选，移除的 JVM 参数 |
| addJvmArgs | `List<String>` | 可选，添加的 JVM 参数 |
| removeProgramArgs | `List<String>` | 可选，移除的程序参数 |
| addProgramArgs | `List<String>` | 可选，添加的程序参数 |
| removeProperties | `List<String>` | 可选，移除的系统属性 |
| addProperties | `List<String>` | 可选，添加的系统属性 |
| coverEnvs | `List<String>` | 可选，覆盖的环境变量 |

**示例（Reload 模式）**
```json
{
  "clientSessionId": "session-xxx",
  "removeProgramArgs": ["--server.port=8080"],
  "addProgramArgs": ["--server.port=9090"],
  "removeProperties": ["user.timezone=UTC"],
  "addProperties": ["user.timezone=Asia/Shanghai"]
}
```

**示例（Restart 模式）**
```json
{
  "clientSessionId": "session-xxx",
  "removeJvmArgs": ["-Xmx512m"],
  "addJvmArgs": ["-Xmx1024m"],
  "removeProgramArgs": [],
  "addProgramArgs": [],
  "coverEnvs": ["JAVA_OPTS=-Xmx2g"]
}
```

**响应** `ApiResponse<ProcessArgsVO>`

---

## 九、通用业务接口（核心）

这是项目的核心通用接口，通过 `adjustmentType` 字段区分不同业务操作。

```
POST /process/adjustment
Content-Type: application/json
```

**通用请求体**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| clientSessionId | String | 客户端会话 ID |
| adjustmentType | String | 类型（见下方各小节） |
| adjustmentContent | Object | 业务数据，根据类型变化 |

**通用响应** `ApiResponse<AdjustmentResponse>`

> `AdjustmentResponse.adjustmentResult` 和 `adjustmentExtendResult` 字段的含义随 `adjustmentType` 变化，见具体小节。

---

### 9.1 系统属性 / 钩子参数修改

**adjustmentType**: `property` / `property_hook`

**请求示例（修改系统属性）**
```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "property",
  "adjustmentContent": {
    "user.timezone": "Asia/Shanghai"
  }
}
```

---

### 9.2 日志等级管理

**adjustmentType**: `log`

#### 查询日志配置

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "log",
  "adjustmentContent": {}
}
```

**响应** `adjustmentResult` 为 `Map<String, String>`

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "adjustmentResult": {
      "root": "configuredLevel=INFO,effectiveLevel=INFO",
      "com.example": "configuredLevel=DEBUG,effectiveLevel=DEBUG"
    }
  }
}
```

#### 修改日志等级

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "log",
  "adjustmentContent": {
    "com.example.MyClass": "DEBUG"
  }
}
```

---

### 9.3 日志重放管理

**adjustmentType**: `log_replay`

#### 添加重放规则

**请求参数（adjustmentContent）**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| logReplayInfo | String | LogReplayInfo 的 JSON 字符串 |

**LogReplayInfo 内层字段**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| matchType | String | `CONTAIN`（包含,大小写敏感）或 `REGEX`（正则） |
| matchString | String | 匹配字符串；REGEX 模式需 Base64 编码 |
| logFileName | String | 目标日志文件名（添加可选，删除必填） |
| operationType | String | `ADD`（添加）或 `REMOVE`（删除） |

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "log_replay",
  "adjustmentContent": {
    "logReplayInfo": "{\"matchType\":\"CONTAIN\",\"matchString\":\"ERROR\",\"operationType\":\"ADD\"}"
  }
}
```

#### 查询重放列表

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "log_replay",
  "adjustmentContent": {}
}
```

**响应** `adjustmentExtendResult.logReplayInfos`

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "adjustmentExtendResult": {
      "logReplayInfos": [
        {
          "logFileName": "app.log",
          "matchString": "ERROR",
          "matchType": "CONTAIN",
          "operationType": "ADD"
        }
      ]
    }
  }
}
```

---

### 9.4 对象名称发现

**adjustmentType**: `obj_info_discovery`

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| objType | String | `hook`（Spring Bean）或 `normal`（普通对象） |
| objPackageName | String | 可选，包名过滤 |

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "obj_info_discovery",
  "adjustmentContent": {
    "objType": "hook",
    "objPackageName": "com.example"
  }
}
```

**响应** `adjustmentExtendResult.objNames`

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "adjustmentExtendResult": {
      "objNames": ["demo2Controller", "userService"]
    }
  }
}
```

---

### 9.5 对象信息获取

**adjustmentType**: `obj_info`

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| objName | String | 对象名称 |
| objType | String | `hook` 或 `normal` |
| objTypeParam | String | 对象类型参数 |
| fieldValues | String | 逗号分隔的属性名列表 |

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "obj_info",
  "adjustmentContent": {
    "objName": "demo2Controller",
    "objType": "hook",
    "objTypeParam": "1",
    "fieldValues": "name,age"
  }
}
```

**响应** `adjustmentExtendResult` 包含 `fieldInfo`（`List<FieldInfo>`）和 `methodInfo`（`List<MethodInfo>`）

---

### 9.6 对象属性修改

**adjustmentType**: `obj_field`

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| objName | String | 对象名称 |
| objType | String | 对象类型 |
| objTypeParam | String | 对象类型参数 |
| fieldInfo | String | FieldInfo 的 JSON 字符串 |

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "obj_field",
  "adjustmentContent": {
    "objName": "demo2Controller",
    "objType": "hook",
    "objTypeParam": "1",
    "fieldInfo": "{\"fieldName\":\"name\",\"fieldType\":\"java.lang.String\",\"fieldValue\":\"newValue\",\"isFinal\":false,\"isStatic\":false,\"signature\":\"...\"}"
  }
}
```

---

### 9.7 对象方法执行

**adjustmentType**: `obj_method`

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| objName | String | 对象名称 |
| objType | String | 对象类型 |
| objTypeParam | String | 对象类型参数 |
| methodInfo | String | MethodInfo 的 JSON 字符串 |

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "obj_method",
  "adjustmentContent": {
    "objName": "demo2Controller",
    "objType": "hook",
    "objTypeParam": "2",
    "methodInfo": "{\"argTypeList\":[\"java.lang.String\"],\"argValues\":[\"hello\"],\"isStatic\":false,\"methodName\":\"sayHello\",\"returnType\":\"java.lang.String\",\"signature\":\"...\"}"
  }
}
```

**响应** `adjustmentExtendResult.returnValue` — 方法返回值的 JSON 字符串

---

### 9.8 对象方法监控

**adjustmentType**: `obj_trace`

通过 `adjustmentContent.traceType` 区分子操作。

#### 安装监控（install）

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "obj_trace",
  "adjustmentContent": {
    "traceType": "install",
    "objName": "demo2Controller",
    "objType": "hook",
    "objTypeParam": "1",
    "methodInfo": "{\"argTypeList\":[],\"argValues\":[],\"isStatic\":false,\"methodName\":\"getInfo\",\"returnType\":\"java.lang.String\",\"signature\":\"...\"}"
  }
}
```

#### 卸载监控（uninstall）

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "obj_trace",
  "adjustmentContent": {
    "traceType": "uninstall",
    "objName": "demo2Controller",
    "objType": "hook",
    "objTypeParam": "1",
    "methodGeneric": "public java.lang.String com.example.DemoController.getInfo()"
  }
}
```

---

### 9.9 JVM 监控 - 文件列表

**adjustmentType**: `jvm_list`

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "jvm_list"
}
```

**响应** `adjustmentResult` 为 `Map<String, String>`，`adjustmentExtendResult.isExist` 标识 JFR 是否运行中。

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "adjustmentResult": {
      "//absolutePath": "/app/logs",
      "heapdump.hprof": "📦:256.00 MB | 🕒:2026-06-05T10:00:00",
      "recording.jfr": "📦:15.00 MB | 🕒:2026-06-05T09:00:00"
    },
    "adjustmentExtendResult": {
      "isExist": false
    }
  }
}
```

---

### 9.10 JVM 监控 - 生成 Heap Dump

**adjustmentType**: `jvm_heap`

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "jvm_heap"
}
```

---

### 9.11 JVM 监控 - JFR 飞行记录

#### 开始 JFR

**adjustmentType**: `jvm_jfr_start`

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| maxSeconds | String | 最大时长（秒） |
| maxSize | String | 最大大小（字节） |

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "jvm_jfr_start",
  "adjustmentContent": {
    "maxSeconds": "3600",
    "maxSize": "20971520"
  }
}
```

#### 结束 JFR

**adjustmentType**: `jvm_jfr_end`

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "jvm_jfr_end"
}
```

**响应** `adjustmentResult` 包含结束信息。

---

### 9.12 文件管理 - 文件列表

**adjustmentType**: `file_list`

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| fileDir | String | 目录路径 |
| createIfNotExist | Boolean | 可选，为 true 时创建目录 |

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "file_list",
  "adjustmentContent": {
    "fileDir": "/app/logs"
  }
}
```

**响应** `adjustmentResult` 为 `Map<String, String>`：
- `//absolutePath` — 当前绝对路径
- 文件名 → 文件信息（`📦:大小 | 🕒:时间`，目录以 `📁` 结尾）

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "adjustmentResult": {
      "//absolutePath": "/app/logs",
      "app.log": "📦:1024.00 KB | 🕒:2026-06-05T10:00:00",
      "subdir": "📁"
    }
  }
}
```

---

### 9.13 文件管理 - 删除文件

**adjustmentType**: `file_remove`

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| fileAbsolutePath | String | 文件绝对路径 |

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "file_remove",
  "adjustmentContent": {
    "fileAbsolutePath": "/app/logs/old.log"
  }
}
```

---

### 9.14 日志检索

**adjustmentType**: `file_reader`

**请求参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| filePath | String | 搜索目录 |
| matchString | String | 关键词（REGEX 模式需 Base64 编码） |
| childPath | String | `true`/`false` 是否递归子目录 |
| matchSize | String | 最大匹配条数 |
| filenameFilter | String | 文件名过滤（如 `*.log`） |
| matchType | String | `CONTAIN` 或 `REGEX` |
| beforeSize | String | 命中前行数 |
| afterSize | String | 命中后行数 |

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "file_reader",
  "adjustmentContent": {
    "filePath": "/app/logs",
    "matchString": "ERROR",
    "childPath": "true",
    "matchSize": "1000",
    "filenameFilter": "*.log",
    "matchType": "CONTAIN",
    "beforeSize": "2",
    "afterSize": "2"
  }
}
```

**响应** `adjustmentExtendResult.content` 为 `List<String>`

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "adjustmentExtendResult": {
      "content": [
        "[2026-06-05 10:00:00] ERROR [main] com.example.MyClass - error message"
      ]
    }
  }
}
```

---

### 9.15 组件管理 - 查询模块状态

**adjustmentType**: `module_status`

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "module_status"
}
```

**响应** `adjustmentResult` 为 `AdjustmentModuleResult`

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "adjustmentResult": {
      "ssh": "22",
      "arthas": "8563",
      "http": "7980",
      "sftp": "22",
      "//errMsg": ""
    }
  }
}
```

---

### 9.16 组件安装

**adjustmentType**: `module_ssh` / `module_arthas` / `module_proxy`

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "module_ssh",
  "adjustmentContent": {
    "status": "enable"
  }
}
```

**响应** `adjustmentResult` 为 `AdjustmentModuleResult`（含 `//errMsg`）

---

### 9.17 SFTP 连接

#### 开启 SFTP

**adjustmentType**: `sftp_open`

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "sftp_open",
  "adjustmentContent": {
    "username": "root",
    "password": "password"
  }
}
```

#### 关闭 SFTP

**adjustmentType**: `sftp_close`

```json
{
  "clientSessionId": "session-xxx",
  "adjustmentType": "sftp_close"
}
```

---

## 十、文件上传下载

### 10.1 文件上传

```
POST /process/adjustment/upload
Content-Type: multipart/form-data
```

**FormData 参数**

| 字段 | 说明 |
|------|------|
| file | 上传的文件，支持多个同名 `file` 字段 |
| clientSessionId | 客户端会话 ID |
| fileDir | 目标目录 |

**响应** `ApiResponse<Object>`

### 10.2 文件下载

```
GET /process/adjustment/download/get?clientSessionId={clientSessionId}&fileAbsolutePath={fileAbsolutePath}
```

**查询参数**

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| clientSessionId | String | 客户端会话 ID |
| fileAbsolutePath | String | 文件绝对路径 |

---

## 附录：数据模型

### Client

```java
public class Client {
    private String clientSessionId;
    private String applicationName;
    private String packageName;
    private String socketClientHost;
    private String socketClientIp;
    private String socketClientOutletIp;
    private String uniqueId;
    private int pid;
    private int jdwpPort;
    private String debug4jMode;    // thread / process / agent / attach / docker
    private String rootUniqueId;   // 子进程时不为 null
    private String reloadMode;     // Restart / Reload
}
```

### ProxyMapping

```java
public class ProxyMapping {
    private String remark;
    private int serverPort;
    private String clientSessionId;
    private String remoteHost;
    private int remotePort;
    private List<String> allowNetworks;
    private List<String> clientOutletIps;
    private String status;         // active / inactive
}
```

### AttachClassSourceRespVO

```java
public class AttachClassSourceRespVO {
    private String classSource;                    // 源码内容
    private List<String> classMethods;             // 类方法列表
    private String byteCodeType;                   // original / agentOnlyJavassist / agentWithByteBuddy
    private boolean status;
    private List<Integer> lineNumbers;             // 行号列表
}
```

### AdjustmentResponse

```java
public class AdjustmentResponse {
    private Object adjustmentResult;
    private AdjustmentExtendResult adjustmentExtendResult;
}

public class AdjustmentExtendResult {
    private List<String> content;                 // 日志检索内容
    private List<LogReplayInfo> logReplayInfos;    // 日志重放信息
    private List<FieldInfo> fieldInfo;             // 对象属性
    private List<MethodInfo> methodInfo;           // 对象方法
    private List<String> objNames;                 // 对象名称列表
    private String returnValue;                    // 方法返回值(JSON)
    private Boolean isExist;                       // 是否存在
}
```

### LogReplayInfo

```java
public class LogReplayInfo {
    private String logFileName;
    private String matchString;
    private String matchType;       // CONTAIN / REGEX
    private String operationType;   // ADD / REMOVE
}
```

### FieldInfo

```java
public class FieldInfo {
    private String fieldName;
    private String fieldType;
    private String fieldValue;
    private boolean isFinal;
    private boolean isStatic;
    private String signature;
}
```

### MethodInfo

```java
public class MethodInfo {
    private List<String> argTypeList;
    private List<Object> argValues;
    private boolean isStatic;
    private String methodName;
    private String returnType;
    private String signature;
}
```

### AdjustmentModuleResult

```java
public class AdjustmentModuleResult {
    private String ssh;
    private String arthas;
    private String http;
    private String sftp;
    private String //errMsg;       // 错误信息
}
```

### SystemParam

```java
public class SystemParam {
    private String key;
    private String value;
    private String description;
    private boolean refreshScope;
    private String source;         // SYSTEM / ENV / SPRING / JVM / PROGRAM
}
```

### SourceCodeTypeEnum / ByteCodeTypeEnum

```java
public enum SourceCodeTypeEnum {
    originalClassFile,
    agentTransformClassByteCode,
    agentTransformClassBuffer,
    attachClassByteCode
}

public enum ByteCodeTypeEnum {
    original,
    agentOnlyJavassist,
    agentWithByteBuddy
}
```

---

## 接口路径速查表

| 路径 | 方法 | 说明 |
|------|------|------|
| `/manage/clients` | GET | 获取节点列表 / 登录鉴权 |
| `/attach/task/open` | POST | 打开日志监听 |
| `/attach/task/close` | POST | 关闭日志监听 |
| `/attach/task` | POST | 获取监听任务列表 |
| `/attach/task` | GET (SSE) | 日志流式监听 |
| `/attach/class` | POST | 获取类名列表 |
| `/attach/source` | POST | 获取类源码 |
| `/attach/reload/source` | POST | 更新源码 |
| `/attach/reload/class` | POST (multipart) | 上传字节码更新 |
| `/attach/restore` | POST | 还原源码 |
| `/attach/source/method-line` | POST | 获取方法源码行 |
| `/attach/patch/method-line` | POST | 方法行补丁 |
| `/proxy/details` | GET | 获取代理映射列表 |
| `/proxy` | POST | 创建代理映射 |
| `/proxy/remove` | POST | 删除代理映射 |
| `/process/args` | POST | 获取进程参数 |
| `/process/reload` | POST | 进程重载 |
| `/process/adjustment` | POST | 通用业务（核心） |
| `/process/adjustment/upload` | POST (multipart) | 文件上传 |
| `/process/adjustment/download/get` | GET | 文件下载 |
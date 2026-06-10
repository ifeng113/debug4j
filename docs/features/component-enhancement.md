# 组件增强

## 概述

提供非用户进程业务的外部操作增强，包括文件管理、组件管理和代理服务。

## SFTP 文件管理

基于 **Apache MINA SSHD** 的 SFTP 子系统：

```java
SshServer sshd = SshServer.setUpDefaultServer();
sshd.setPort(2222);
sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
sshd.setPasswordAuthenticator((u, p, s) -> "root".equals(u) && "123456".equals(p));
sshd.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
sshd.start();
```

| 功能 | 说明 |
|------|------|
| 文件列表 | 目录树导航，可视化浏览 |
| 文件上传 | 通过 Server 上传文件到目标进程环境 |
| 文件下载 | 从目标进程环境下载文件 |
| 文件删除 | 删除目标进程环境中的文件 |

> **注意**：只支持 SFTP，不支持交互式 SSH Shell。原因是 MINA SSHD 的 ProcessShell 在 Windows（CMD/PowerShell 格式错乱）和 Linux（复杂命令失败）下兼容性差。未来可考虑集成 OpenSSH 实现更完整的终端支持。

## 组件管理

### OpenSSH

自动检测并安装 OpenSSH 服务：
- 检测本地是否已存在 OpenSSH 服务
- 通过 `Debug4jResourceExtractor` 解压安装资源
- 使用 `ProcessBuilder` 执行安装脚本

### Arthas

一键安装并开启 Arthas Telnet / Web Console 服务：
- 自动下载 Arhtas 并解压
- 开启 Telnet 端口（默认 3658）
- 开启 Web Console 端口（默认 8563）
- 支持 `monitor` / `watch` / `trace` 等高级诊断命令
- 与 debug4j 的方法追踪互补（debug4j 不支持方法体内部链路追踪，Arthas 可以）

## HTTP(S) 代理

`Debug4jHttpProxy` 实现完整的 HTTP/HTTPS 正向代理：
- 在用户进程内监听指定端口
- 处理 HTTP CONNECT 方法建立 HTTPS 隧道
- 处理标准 HTTP 请求中继
- 与 TCP 隧道（SocketTFProxy）互补
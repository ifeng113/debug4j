# Debug4j

**Debug4j** is an efficient and convenient Java debugging tool designed for remote server-side debugging. It creatively enables interactive and intuitive visual remote code modification and debugging, featuring easy deployment and rapid integration.

### Supported Versions
- This repository supports JDK 17 and above.
- If you are using JDK 8, please refer to [debug4j-jdk8](https://github.com/ifeng113/debug4j-jdk8).

---

## Features

- **Proxy Penetration Service**: Enables remote proxy penetration in isolated environments (e.g., Docker, Kubernetes).
- **JWDP Remote Debugging**: Convenient configuration for remote debugging.
- **Log File Monitoring**: Real-time retrieval of application logs.
- **Program Class and Source Code Access**: Dynamic loading and parsing of target program classes and source code.
- **Source Code Hot Update**: Dynamic runtime source code updates.
- **Bytecode Hot Update**: Direct modification and update of bytecode.
- **Line-Level Code Insertion**: Quick insertion of debugging code.
- **Code Reversion**: Restore to the state before updates.

---

## Quick Start

### Server Installation

1. Pull the Docker image:
   ```bash
   docker pull k4ln/debug4j-server:0.0.1_api
   ```

2. Start the server:
   ```bash
   docker run --net=host -d --name debug4j-server k4ln/debug4j-server:0.0.1_api
   ```

3. Set the communication key and API key:
   ```bash
   docker run --net=host -d --name debug4j-server k4ln/debug4j-server:0.0.1_api \
       --debug4j.key=k4ln --sa-token.http-basic='k4ln:123456'
   ```

   - `--debug4j.key`: Sets the communication key.
   - `--sa-token.http-basic`: Sets the API communication key.

> Refer to the API documentation at [Debug4j.postman_collection.json](https://github.com/ifeng113/debug4j/blob/master/src/main/resources/Debug4j.postman_collection.json) (Web management page is under development).

---

### Java Application Integration

Add the following dependency to your project:
```xml
<dependency>
    <groupId>io.github.ifeng113</groupId>
    <artifactId>jdk8-debug4j-daemon</artifactId>
    <version>0.0.1</version>
</dependency>
```

Start Debug4j in your application:
```java
Debug4jDaemon.start(true, "demo1-daemon", "com.k4ln", "192.168.1.13", 7988, "k4ln");
```

For example code, refer to [debug4j-demo1](https://github.com/ifeng113/debug4j/tree/master/debug4j-demo1).

---

### Spring Boot Integration

Add the following dependency to your project:
```xml
<dependency>
    <groupId>io.github.ifeng113</groupId>
    <artifactId>jdk8-debug4j-spring-boot-starter</artifactId>
    <version>0.0.1</version>
</dependency>
```

Configure Debug4j in `application.yml`:
```yaml
debug4j:
  package-name: com.k4ln
  host: 192.168.1.13
  port: 7988
  key: k4ln
```

For example code, refer to [debug4j-demo2](https://github.com/ifeng113/debug4j/tree/master/debug4j-demo2).

---

## Limitations and Notes

1. **Class Signature Restrictions**:
   - Code hot updates or bytecode hot updates cannot modify field names or method names (i.e., class signatures).
   - JVM supports adding methods and variables but does not support removal. Debug4j currently supports changes only within method bodies.

2. **Agent Compatibility Issues**:
   - Using agents (e.g., ByteBuddy) may modify bytecode, making source code hot updates and bytecode hot updates unavailable.
   - In JDK 8 environments, source-level debugging features such as line-level code insertion may not be available due to source code decompilation limitations.
   - It is recommended to avoid using agents or adjust related configurations.

3. **Bytecode Version Compatibility**:
   - Ensure that the class file used for hot updates is compatible with the target JVM version.

4. **Code Line Patch Notes**:
   - When using third-party utility classes, use the full path to avoid compilation errors caused by class name conflicts.

   Example:
   ```json
   {
       "clientSessionId": "aioSession-1341587928",
       "className": "com.k4ln.demo.Demo1DaemonMain",
       "lineMethodName": "logNumber",
       "lineNumber": 24,
       "sourceCode": "log.info(\"com.alibaba.fastjson2.JSON.toJSONString(patch13)\");"
   }
   ```

---

## Acknowledgments

- [Smart-Socket](https://github.com/smartboot/smart-socket)
- [Sa-Token](https://github.com/dromara/sa-token)


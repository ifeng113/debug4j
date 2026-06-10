# Spring 集成

## 概述

debug4j 通过 `debug4j-spring-boot-starter` 模块为 Spring Boot 应用提供开箱即用的自动配置，深度集成 Spring Environment、Bean 容器、@RefreshScope 等特性。

## 自动配置

```java
@ConditionalOnProperty(prefix = "debug4j", value = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(Debug4jProperties.class)
public class Debug4jAutoConfiguration { ... }
```

通过 SPI 注册：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 配置属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `debug4j.enabled` | Boolean | `true` | 是否启用 |
| `debug4j.proxy` | Boolean | `true` | 是否启用代理模式 |
| `debug4j.reload-mode` | ReloadMode | `Restart` | 进程重载模式 |
| `debug4j.developer` | Boolean | `false` | 开发者模式 |
| `debug4j.application` | String | — | 应用名称 |
| `debug4j.package-name` | String | `""` | 包名过滤 |
| `debug4j.host` | String | — | Server 主机 |
| `debug4j.port` | Integer | — | Server 端口 |
| `debug4j.key` | String | — | 认证密钥 |

## 配置动态刷新

动态修改 Spring Environment 中的配置属性，支持 `@RefreshScope` Bean 自动更新。

### 核心机制

```java
adjustmentProperties(environment, adjustmentProperties)
  1. 获取 MutablePropertySources
  2. 将 "debug4jDynamicProperty" 源插入首位（最高优先级）
  3. 通过反射发布 Spring Cloud RefreshEvent
     → @RefreshScope Bean 自动刷新
     → 无 Spring Cloud 时，属性立即生效但 Bean 不会重建
```

### 配置优先级

Debug4j 注入的 `MapPropertySource` 置于 `PropertySources` 首位，在与 Spring Cloud Config 配合时：

| allow-override | override-none | 最终优先级 |
|---|---|---|
| true | false | **debug4j >** commandLineArgs > systemProperties > application.yaml > bootstrap.yaml |
| true / false | true | commandLineArgs > systemProperties > application.yaml > bootstrap.yaml > **debug4j** |

### Bootstrap 阶段集成

通过 `Debug4jPropertySourceLocator`（实现 `PropertySourceLocator`）在 Spring Cloud Bootstrap 阶段注入：

```java
@Order(-1)  // 最高优先级
public class Debug4jPropertySourceLocator implements PropertySourceLocator {
    @Override
    public PropertySource<?> locate(Environment environment) {
        return new MapPropertySource(SOURCE_NAME, new HashMap<>(SOURCE_DATA));
    }
}
```

## 扩展钩子系统

钩子系统使 debug4j 通过统一接口操作 Spring Bean，无需硬编码 Spring 依赖：

| 钩子类型 | 功能 | 实现 |
|----------|------|------|
| `HOOK_ARGS` | 读取所有 Spring 配置 | `PropertySourcesHandler.getAllProperties2()` |
| `HOOK_ARGS_ADJUSTMENT` | 动态调整配置 | `PropertySourcesHandler.adjustmentProperties()` |
| `HOOK_OBJ_DISCOVERY` | 发现所有 Spring Bean | `SpringBeanHandler.discovery` |
| `HOOK_OBJ` | 获取指定 Spring Bean | `SpringBeanHandler.getBean()` + AOP 解包 |

**AOP 代理解包：** 使用 `AopProxyUtils.getSingletonTarget(bean)` 获取被 AOP 代理的真实对象。

## 进程重载

### Reload 模式

```java
// 在当前进程中重新加载 Spring 上下文
extendedHookMap.put(RELOAD_CLOSE, h -> SpringApplication.exit(context, () -> 0));
extendedHookMap.put(RELOAD_START, h -> SpringApplication.run(mainClass, newArgs));
```

### Restart 模式

```java
// 新建子进程：保留原 JVM 参数 + 追加新参数，主进程继续保持运行
List<String> command = new ArrayList<>();
command.add(javaBin);
command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
command.add("-jar");
command.add(jarPath);
ProcessBuilder pb = new ProcessBuilder(command);
pb.inheritIO();
pb.start();
```
# ByteBuddy 字节码增强

## 概述

debug4j 深度使用 **ByteBuddy** 进行字节码操作，涵盖 Instrumentation 获取、方法追踪插桩、jadx 补丁等多个核心场景。同时结合 **Javassist** 实现精确的行级代码插入。

## 获取 Instrumentation

```java
// Debugger.java — 应用启动入口
instrumentation = ByteBuddyAgent.install();
```

`ByteBuddyAgent.install()` 内部使用 Attach API 将自身加载到当前 JVM，获取 `java.lang.instrument.Instrumentation` 实例。这是所有字节码操作的基础。

## jadx 反编译器字段排序补丁

为了获得可靠的源码反编译结果，Debugger 使用 ByteBuddy 运行时修补 jadx：

```java
new AgentBuilder.Default()
    .type(named("jadx.core.dex.visitors.ExtractFieldInit"))
    .transform((builder, type, cl, module, pd) ->
        builder.method(named("applyFieldsOrder"))
               .intercept(MethodDelegation.to(ApplyFieldsOrderPatch.class)))
    .installOn(inst);
```

通过 `MethodDelegation` 替换 `ExtractFieldInit.applyFieldsOrder()` 的实现，修复字段排序保证源码行号稳定。

## 方法追踪插桩

### 安装追踪器

```java
public static synchronized void install(Instrumentation inst, String className, String methodInfo) {
    ResettableClassFileTransformer transformer = transformerMap.get(className);
    if (transformer == null) {
        transformer = new AgentBuilder.Default()
                .ignore(ElementMatchers.none())
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .type(hasSuperType(named(className))
                        .and(not(isInterface())).and(not(isAnnotation())).and(not(isEnum())))
                .transform((builder, type, cl, module, pd) ->
                    builder.visit(Advice.to(Debug4jTraceLogAdvice.class)
                        .on(isMethod().and(not(isConstructor()))))
                ).installOn(inst);
        transformerMap.put(className, transformer);
    }
    classNameMethodMap.computeIfAbsent(className, k -> new ArrayList<>()).add(methodInfo);
}
```

### Advice 逻辑

进入方法时记录参数与 Trace ID，退出时计算耗时：

```java
@Advice.OnMethodEnter
public static Debug4jTraceInfo enter(@Advice.Origin Method method, @Advice.AllArguments Object[] args) {
    if (classNameMethodMap.get(...).contains(method.toGenericString())) {
        return Debug4jTraceInfo.builder()
                .ignore(false).start(System.nanoTime())
                .method(methodName).args(printArgs(args))
                .traceId(Debug4jTraceSafePrinter.getTraceId())  // ThreadLocal Trace ID
                .build();
    }
    return Debug4jTraceInfo.builder().build();  // 不匹配时跳过
}

@Advice.OnMethodExit(onThrowable = Throwable.class)
public static void exit(@Advice.Enter Debug4jTraceInfo traceInfo,
                        @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object ret,
                        @Advice.Thrown Throwable thrown) {
    if (traceInfo != null && !traceInfo.isIgnore()) {
        traceInfo.setEnd(System.nanoTime());
        // 记录返回值或异常
        traceInfo.print();
    }
}
```

### 安全打印

`Debug4jTraceSafePrinter` 负责安全的参数/返回值打印：
- 过滤 `InputStream` / `Socket` / `Thread` / `ClassLoader` 等禁止类型
- 长字符串截断（最多 100 字符）
- ThreadLocal Trace ID 支持调用链跟踪

### 卸载追踪

```java
public static synchronized void uninstall(Instrumentation inst, String className, String methodInfo) {
    List<String> list = classNameMethodMap.getOrDefault(className, new ArrayList<>());
    list.remove(methodInfo);
    if (list.isEmpty() && transformerMap.get(className) != null) {
        transformerMap.get(className).reset(inst, AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
        transformerMap.remove(className);
        classNameMethodMap.remove(className);
    }
}
```

只有全部方法都取消追踪后，才会真正重置 transformer。

## Javassist 行补丁

除了 ByteBuddy 的类级别插桩，debug4j 使用 Javassist 进行方法体内部的精确代码插入：

```java
CtMethod method = ctClass.getDeclaredMethods()[index];
method.insertAt(lineNumber, "/* 补丁代码 */");
```

| 技术 | 适用场景 |
|------|----------|
| **ByteBuddy** | 类级别的全局插桩（方法追踪、监控），动态安装/卸载 |
| **Javassist** | 方法体级别的精确代码插入（行补丁），更贴近源码编辑 |
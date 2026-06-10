# 源码管理

## 概述

提供进程级源代码的在线获取、编辑、编译与热更新能力。通过浏览器即可直接修改远程进程的任意代码并**立即生效**，无需重启应用。

## 功能清单

| 子功能 | 说明 |
|--------|------|
| **源码获取** | 通过 jadx 反编译器从字节码还原 Java 源码 |
| **源码编辑更新** | 在线编辑源码 → 编译 → retransform 热更新 |
| **字节码上传更新** | 直接上传 .class 文件进行热更新 |
| **行号获取** | 匹配字节码行号表与反编译源码行号 |
| **行代码补丁** | 在指定行首快速插入代码，不改变行号 |

## 技术实现

### 源码获取

```
class 字节码 → 写入临时文件
             → JadxArgs（debugInfo=true, respectBytecodeAccModifiers=false）
             → JadxDecompiler 加载 → 获取 JavaClass 源码文本
```

debug4j 使用 **jadx** 作为反编译器。启动时通过 ByteBuddy 修补 jadx 的 `ExtractFieldInit.applyFieldsOrder()` 方法，修复字段排序问题以保证反编译结果可编译。

### 在线编译 + 热更新

```
JavaSourceFileObject（源码内存对象）
  → JavaCompiler.CompilationTask（编译任务）
    → JavaClassFileManager（拦截输出流到内存）
      → JavaClassFileObject（捕获编译产物）
        → ResourceClassLoader（defineClass 加载）
```

1. 用户编辑源码后提交
2. **JavaParser** 解析源码并自动修复泛型数组等兼容性问题
3. **javax.tools.JavaCompiler** 在线编译为字节码（完全在内存中完成）
4. 编译结果包含外部类 + 所有内部类的字节码
5. 通过 `Instrumentation.retransformClasses()` 一次性应用

### 内部类处理

| 功能 | 模式 |
|------|------|
| 源码获取 | **合并模式** — 外部类与内部类一起反编译 |
| 源码更新 | **合并模式** — 合并编译，同时更新 |
| 字节码更新 | **分离模式** — 每个类独立操作 |
| 行号/补丁 | **分离模式** — 针对单个类 |

> **内部类预加载**：JVM 启动时不会自动加载内部类，需要通过 `Class.forName("Outer$Inner")` 主动加载。

### 行代码补丁

使用 **javassist** 在目标方法的指定行首插入代码：

```java
CtMethod method = ctClass.getDeclaredMethods()[index];
method.insertAt(lineNumber, "/* 补丁代码 */");
```

补丁特点：
- 不会改变原始行号
- 类引用建议使用**全限定名**防止冲突
- 不可修改类签名（字段名、方法签名等）

### 三级字节码捕获

为了正确处理 Agent 修改过的类，debug4j 定义了三个字节码来源：

| 类型 | 来源 |
|------|------|
| **原始字节码** | 从 ClassLoader.getResource() 加载 |
| **Agent 修改（Javassist）** | ClassPool 转换后获取 |
| **Agent 修改（ByteBuddy）** | 拦截 retransform 获取 |

通过对比 MD5 哈希自动选择最可靠的反编译策略。
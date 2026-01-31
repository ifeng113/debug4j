package com.k4ln.debug4j.core.attach;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.k4ln.debug4j.common.protocol.command.CommandTypeEnum;
import com.k4ln.debug4j.common.protocol.command.message.enums.ByteCodeTypeEnum;
import com.k4ln.debug4j.common.protocol.command.message.enums.SourceCodeTypeEnum;
import com.k4ln.debug4j.common.utils.FileUtils;
import com.k4ln.debug4j.core.Debugger;
import com.k4ln.debug4j.core.attach.dto.ByteCodeInfo;
import com.k4ln.debug4j.core.attach.dto.MethodLineInfo;
import com.k4ln.debug4j.core.attach.dto.ObjMethodInfo;
import com.k4ln.debug4j.core.attach.dto.SourceCodeInfo;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.JavaMethod;
import jadx.core.dex.instructions.args.ArgType;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.SignatureAttribute;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class Debug4jAttachOperator {

    /**
     * className -> ByteCodeInfo
     */
    @Getter
    private static final Map<String, ByteCodeInfo> realByteCodeMap = new ConcurrentHashMap<>();

    /**
     * className -> classURL
     */
    @Getter
    private static final Map<String, Map<String, URL>> classURLMap = new ConcurrentHashMap<>();

    /**
     * className -> method
     */
    @Getter
    private static final Map<String, List<String>> classMethodMap = new ConcurrentHashMap<>();

    /**
     * className -> methodInfo
     */
    @Getter
    private static final Map<String, List<ObjMethodInfo>> classMethodInfoMap = new ConcurrentHashMap<>();

    /**
     * 获取所有class名称
     *
     * @param instrumentation
     * @param configPackageName
     * @param packageName
     * @return
     */
    public static List<String> getAllClass(Instrumentation instrumentation, String configPackageName, String packageName) {
        List<String> classes = new ArrayList<>();
        for (Class allLoadedClass : instrumentation.getAllLoadedClasses()) {
            if (!allLoadedClass.getName().contains("$") // 过滤代理类及内部类
                    && (StrUtil.isBlank(packageName) ? allLoadedClass.getName().startsWith(configPackageName) : allLoadedClass.getName().startsWith(packageName))) {
                classes.add(allLoadedClass.getName());
            }
        }
        return classes;
    }

    /**
     * 获取源码
     *
     * @param instrumentation
     * @param className
     * @return
     */
    public static SourceCodeInfo getClassSource(Instrumentation instrumentation, String className, SourceCodeTypeEnum sourceCodeType) {
        ByteCodeInfo byteCodeInfo = getClassByteCodeInfo(instrumentation, className);
        String classSource = jadxByteCodeToSourceWithInner(instrumentation, className, sourceCodeType);
        return SourceCodeInfo.builder()
                .byteCodeType(byteCodeInfo != null ? byteCodeInfo.getAttachClassByteCodeType() : null)
                .classSource(classSource)
                .classMethods(classMethods(instrumentation, className))
                .build();
    }

    /**
     * 获取原始字节码
     *
     * @param instrumentation
     * @param className
     * @return
     */
    public synchronized static ByteCodeInfo getClassByteCodeInfo(Instrumentation instrumentation, String className) {
        ByteCodeInfo byteCodeInfo = realByteCodeMap.get(className);
        if (byteCodeInfo == null) {
            byteCodeInfo = new ByteCodeInfo();
            // originalClassFileByteCode
            try {
                Class<?> clazz = Class.forName(className);
                URL resource = clazz.getClassLoader().getResource(clazz.getName().replace('.', '/') + ".class");
                assert resource != null;
                byte[] bytes;
                try (InputStream in = resource.openStream()) {
                    bytes = in.readAllBytes();
                }
                byteCodeInfo.setOriginalClassFileByteCode(bytes);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            // agentTransformClassByteCode
            try {
                ClassPool pool = ClassPool.getDefault();
                CtClass ctClass = pool.getCtClass(className);
                byteCodeInfo.setAgentTransformClassByteCode(ctClass.toBytecode());
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            // agentTransformClassBufferByteCode
            try {
                CompletableFuture<ByteCodeInfo> future = new CompletableFuture<>();
                Debug4jClassFileTransformer debug4jClassFileTransformer = new Debug4jClassFileTransformer(className,
                        CommandTypeEnum.ATTACH_REQ_CLASS_SOURCE, null, null, future, byteCodeInfo);
                reTransformer(instrumentation, className, debug4jClassFileTransformer);
                byteCodeInfo = future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            realByteCodeMap.put(className, byteCodeInfo);
        }
        return byteCodeInfo;
    }

    /**
     * 获取原始字节码
     *
     * @param sourceCodeType
     * @param byteCodeInfo
     * @return
     */
    private static byte[] getClassByteCodeByCache(SourceCodeTypeEnum sourceCodeType, ByteCodeInfo byteCodeInfo) {
        byte[] byteCode;
        if (sourceCodeType.equals(SourceCodeTypeEnum.originalClassFile)) {
            byteCode = byteCodeInfo.getOriginalClassFileByteCode();
        } else if (sourceCodeType.equals(SourceCodeTypeEnum.agentTransformClassByteCode)) {
            byteCode = byteCodeInfo.getAgentTransformClassByteCode();
        } else if (sourceCodeType.equals(SourceCodeTypeEnum.agentTransformClassBuffer)) {
            byteCode = byteCodeInfo.getAgentTransformClassBufferByteCode();
        } else {
            byteCode = byteCodeInfo.getAttachClassByteCode();
        }
        return byteCode;
    }

    /**
     * 加载原始类文件
     *
     * @param className
     * @return
     */
    private synchronized static Map<String, URL> loadOriginalClass(String className) {
        String mainClassName = className.contains("$") ? className.substring(0, className.indexOf("$")) : className;
        if (classURLMap.get(mainClassName) != null) {
            return classURLMap.get(mainClassName);
        } else {
            Map<String, URL> classMap = new HashMap<>();
            try {
                Class<?> clazz = Class.forName(mainClassName);
                ClassLoader loader = clazz.getClassLoader();
                String path = clazz.getName().replace('.', '/') + ".class";
                classMap.put(mainClassName, loader.getResource(path));
                String basePath = path.substring(0, path.lastIndexOf('/') + 1);
                String simpleName = clazz.getSimpleName();
                URL dirURL = loader.getResource(basePath);
                if (dirURL != null && "file".equals(dirURL.getProtocol())) {
                    String namePrefix = mainClassName.substring(0, mainClassName.length() - simpleName.length());
                    File dir = new File(dirURL.toURI());
                    for (File file : Objects.requireNonNull(dir.listFiles())) {
                        String name = file.getName();
                        if (name.startsWith(simpleName + "$") && name.endsWith(".class")) {
                            classMap.put(namePrefix + name.replace(".class", ""), file.toURI().toURL());
                        }
                    }
                } else if (dirURL != null && "jar".equals(dirURL.getProtocol())) {
                    String jarPath = dirURL.getPath().replace("file:", "").replace("nested:", "");
                    jarPath = jarPath.substring(0, jarPath.indexOf("!"));
                    JarFile jar = new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8));
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        String innerClassNamePrefix = basePath + simpleName + "$";
                        if (name.contains(innerClassNamePrefix) && name.endsWith(".class")) {
                            String[] innerSplit = name.split(Pattern.quote(innerClassNamePrefix));
                            String innerClassName = innerClassNamePrefix + innerSplit[1];
                            classMap.put(innerClassName.replace("/", ".").replace(".class", ""), loader.getResource(name));
                        }
                    }
                    jar.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            classMap.keySet().forEach(cls -> {
                try {
                    Class.forName(cls);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            LinkedHashMap<String, URL> linkedHashMap = classMap.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (o1, o2) -> o1,
                            LinkedHashMap::new
                    ));
            classURLMap.put(mainClassName, linkedHashMap);
            return linkedHashMap;
        }
    }

    /**
     * jadx字节码转源码
     *
     * @param className
     * @param byteCode
     * @return
     */
    public static String jadxByteCodeToSource(String className, byte[] byteCode) {
        try {
            File file = new File(FileUtils.createTempDir(), className + ".class");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(byteCode);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            file.deleteOnExit();
            return jadxDecompile(List.of(file));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * jadx字节码转源码（支持内部类）
     *
     * @param instrumentation
     * @param className
     * @param sourceCodeType
     * @return
     */
    public static String jadxByteCodeToSourceWithInner(Instrumentation instrumentation, String className, SourceCodeTypeEnum sourceCodeType) {
        return jadxByteCodeToSourceWithInner(instrumentation, className, sourceCodeType, null, null);
    }

    /**
     * jadx字节码转源码（支持内部类）
     *
     * @param instrumentation
     * @param className
     * @param sourceCodeType
     * @param lineClassName
     * @param lineClassByteCodeByCache
     * @return
     */
    public static String jadxByteCodeToSourceWithInner(Instrumentation instrumentation, String className,
                                                       SourceCodeTypeEnum sourceCodeType, String lineClassName, byte[] lineClassByteCodeByCache) {
        try {
            List<File> fileList = new ArrayList<>();
            Map<String, URL> originalClass = loadOriginalClass(className);
            for (Map.Entry<String, URL> entry : originalClass.entrySet()) {
                ByteCodeInfo byteCodeInfo = getClassByteCodeInfo(instrumentation, entry.getKey());
                byte[] classByteCodeByCache = getClassByteCodeByCache(sourceCodeType, byteCodeInfo);
                if (StrUtil.isNotBlank(lineClassName) && entry.getKey().endsWith(lineClassName)) {
                    classByteCodeByCache = lineClassByteCodeByCache;
                }
                File file = new File(FileUtils.createTempDir(), className + ".class");
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(classByteCodeByCache);
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
                fileList.add(file);
            }
            fileList.forEach(File::deleteOnExit);
            return jadxDecompile(fileList);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * jadx反编译
     *
     * @param inputFiles
     * @return
     */
    private synchronized static String jadxDecompile(List<File> inputFiles) {
        JadxArgs jadxArgs = new JadxArgs();
        jadxArgs.setInputFiles(inputFiles);
        jadxArgs.setDebugInfo(false);
        jadxArgs.setCodeNewLineStr("\n");
        JadxDecompiler jadx = new JadxDecompiler(jadxArgs);
        jadx.load();
        String sourceCode = null;
        for (JavaClass cls : jadx.getClasses()) {
            sourceCode = cls.getCode();
        }
        jadx.close();
        return sourceCode;
    }

    /**
     * 获取类方法
     *
     * @param instrumentation
     * @param className
     * @return
     */
    public synchronized static List<String> classMethods(Instrumentation instrumentation, String className) {
        if (classMethodMap.get(className) != null) {
            return classMethodMap.get(className);
        } else {
            List<String> classMethods = new ArrayList<>();
            Map<String, URL> originalClass = loadOriginalClass(className);
            for (Map.Entry<String, URL> entry : originalClass.entrySet()) {
                ByteCodeInfo byteCodeInfo = getClassByteCodeInfo(instrumentation, entry.getKey());
                byte[] classByteCodeByCache = getClassByteCodeByCache(SourceCodeTypeEnum.attachClassByteCode, byteCodeInfo);
                if (classByteCodeByCache != null) {
                    String simpleName = entry.getKey().substring(entry.getKey().lastIndexOf(".") + 1);
                    try {
                        ClassPool pool = ClassPool.getDefault();
                        CtClass cc = pool.get(entry.getKey());
                        if (cc.isFrozen()) {
                            cc.defrost();
                        }
                        pool.makeClass(new ByteArrayInputStream(classByteCodeByCache));
                        cc = pool.get(entry.getKey());
                        CtMethod[] declaredMethods = cc.getDeclaredMethods();
                        Map<String, Integer> counter = new HashMap<>();
                        for (CtMethod declaredMethod : declaredMethods) {
                            int count = counter.merge(declaredMethod.getName(), 1, Integer::sum);
                            if (count == 1) {
                                classMethods.add(simpleName + "@" + declaredMethod.getName());
                            } else {
                                classMethods.add(simpleName + "@" + declaredMethod.getName() + "#" + count);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            classMethodMap.put(className, classMethods);
            return classMethods;
        }
    }

    /**
     * 获取类方法签名
     *
     * @param className
     * @return
     */
    public static List<ObjMethodInfo> methodSignatureInfo(String className) {
        if (classMethodInfoMap.containsKey(className)) {
            return classMethodInfoMap.get(className);
        }
        List<ObjMethodInfo> signatureInfos = new ArrayList<>();
        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass cc = pool.get(className);
            CtMethod[] declaredMethods = cc.getDeclaredMethods();
            for (CtMethod declaredMethod : declaredMethods) {
                ObjMethodInfo signatureInfo = ObjMethodInfo.builder()
                        .methodName(declaredMethod.getName())
                        .isStatic(Modifier.isStatic(declaredMethod.getModifiers()))
                        .build();
                SignatureAttribute sa = (SignatureAttribute) declaredMethod.getMethodInfo().getAttribute(SignatureAttribute.tag);
                if (sa == null) {
                    signatureInfo.setReturnType(declaredMethod.getReturnType().getName());
                    signatureInfo.setArgTypeList(Arrays.stream(declaredMethod.getParameterTypes()).map(CtClass::getName).toList());
                } else {
                    SignatureAttribute.MethodSignature ms = SignatureAttribute.toMethodSignature(sa.getSignature());
                    signatureInfo.setReturnType(parseType(ms.getReturnType()));
                    List<String> argList = new ArrayList<>();
                    for (SignatureAttribute.Type t : ms.getParameterTypes()) {
                        argList.add(parseType(t));
                    }
                    signatureInfo.setArgTypeList(argList);
                }
                signatureInfo.setSignature(signatureInfo.toString());
                signatureInfos.add(signatureInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        classMethodInfoMap.put(className, signatureInfos);
        return signatureInfos;
    }

    private static String parseType(SignatureAttribute.Type type) {
        if (type instanceof SignatureAttribute.ClassType ct) {
            StringBuilder sb = new StringBuilder();
            sb.append(ct.getName());
            if (ct.getTypeArguments() != null && ct.getTypeArguments().length > 0) {
                sb.append("<");
                for (int i = 0; i < ct.getTypeArguments().length; i++) {
                    if (i > 0) sb.append(", ");
                    SignatureAttribute.TypeArgument arg = ct.getTypeArguments()[i];
                    if (arg.getType() != null) {
                        sb.append(parseType(arg.getType()));
                    } else {
                        sb.append("?");
                    }
                }
                sb.append(">");
            }
            return sb.toString();
        }
        if (type instanceof SignatureAttribute.ArrayType at) {
            return parseType(at.getComponentType()) + "[]";
        }
        if (type instanceof SignatureAttribute.TypeVariable tv) {
            return tv.getName();
        }
        return type.toString();
    }

    /**
     * 获取类方法签名
     *
     * @param className
     * @return
     */
    public static List<ObjMethodInfo> jadxMethodSignature(String className) {
        if (classMethodInfoMap.containsKey(className)) {
            return classMethodInfoMap.get(className);
        }
        List<ObjMethodInfo> signatureInfos = new ArrayList<>();
        try {
            ByteCodeInfo byteCodeInfo = getClassByteCodeInfo(Debugger.getInstrumentation(), className);
            File file = new File(FileUtils.createTempDir(), className + ".class");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(getClassByteCodeByCache(SourceCodeTypeEnum.originalClassFile, byteCodeInfo));
            } catch (Exception e) {
                e.printStackTrace();
                return signatureInfos;
            } finally {
                file.deleteOnExit();
            }
            JadxArgs jadxArgs = new JadxArgs();
            jadxArgs.setInputFile(file);
            jadxArgs.setDebugInfo(false);
            jadxArgs.setCodeNewLineStr("\n");
            JadxDecompiler jadx = new JadxDecompiler(jadxArgs);
            jadx.load();
            for (JavaClass cls : jadx.getClasses()) {
                List<JavaMethod> methods = cls.getMethods();
                for (JavaMethod declaredMethod : methods) {
                    ObjMethodInfo signatureInfo = ObjMethodInfo.builder()
                            .methodName(declaredMethod.getName())
                            .returnType(declaredMethod.getReturnType().toString())
                            .argTypeList(declaredMethod.getArguments().stream().map(ArgType::toString).toList())
                            .build();
                    signatureInfo.setSignature(signatureInfo.toString());
                    signatureInfos.add(signatureInfo);
                }
            }
            jadx.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        classMethodInfoMap.put(className, signatureInfos);
        return signatureInfos;
    }

    /**
     * 源码热更新
     *
     * @param instrumentation
     * @param className
     * @param sourceCode
     * @return
     */
    public synchronized static boolean sourceReload(Instrumentation instrumentation, String className, String sourceCode) {
        ByteCodeInfo byteCodeInfo = getClassByteCodeInfo(instrumentation, className);
        ByteCodeInfo orginalByteCodeInfo = BeanUtil.copyProperties(byteCodeInfo, ByteCodeInfo.class);
        try {
            if (byteCodeInfo != null && !byteCodeInfo.getAttachClassByteCodeType().equals(ByteCodeTypeEnum.agentWithByteBuddy)) {
                CompletableFuture<ByteCodeInfo> future = new CompletableFuture<>();
                Debug4jClassFileTransformer debug4jClassFileTransformer = new Debug4jClassFileTransformer(className,
                        CommandTypeEnum.ATTACH_REQ_CLASS_RELOAD_JAVA, sourceCode, null, future, byteCodeInfo);
                reTransformer(instrumentation, className, debug4jClassFileTransformer);
                realByteCodeMap.put(className, future.get(30, TimeUnit.SECONDS));
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            realByteCodeMap.put(className, orginalByteCodeInfo);
        }
        return false;
    }

    /**
     * 源码热更新（支持内部类）
     *
     * @param instrumentation
     * @param className
     * @param sourceCode
     * @return
     */
    public synchronized static boolean sourceReloadWithInner(Instrumentation instrumentation, String className, String sourceCode) {
        ByteCodeInfo byteCodeInfo = getClassByteCodeInfo(instrumentation, className);
        ByteCodeInfo orginalByteCodeInfo = BeanUtil.copyProperties(byteCodeInfo, ByteCodeInfo.class);
        try {
            if (byteCodeInfo != null && !byteCodeInfo.getAttachClassByteCodeType().equals(ByteCodeTypeEnum.agentWithByteBuddy)) {
                CompletableFuture<ByteCodeInfo> future = new CompletableFuture<>();
                Debug4jClassFileTransformer debug4jClassFileTransformer = new Debug4jClassFileTransformer(className,
                        CommandTypeEnum.ATTACH_REQ_CLASS_RELOAD_JAVA, sourceCode, null, future, byteCodeInfo);
                reTransformer(instrumentation, className, debug4jClassFileTransformer);
                Map<String, URL> originalClass = loadOriginalClass(className);
                originalClass.keySet().forEach(innerClassName -> {
                    if (!innerClassName.equals(className)) {
                        try {
                            ByteCodeInfo innerByteCodeInfo = getClassByteCodeInfo(instrumentation, innerClassName);
                            Debug4jClassFileTransformer debug4jClassFileTransformerInner = new Debug4jClassFileTransformer(innerClassName,
                                    CommandTypeEnum.ATTACH_REQ_CLASS_RELOAD_JAVA, sourceCode, null, null, innerByteCodeInfo);
                            reTransformer(instrumentation, innerClassName, debug4jClassFileTransformerInner);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                realByteCodeMap.put(className, future.get(30, TimeUnit.SECONDS));
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            realByteCodeMap.put(className, orginalByteCodeInfo);
        }
        return false;
    }

    /**
     * 字节码热更新
     *
     * @param instrumentation
     * @param className
     * @param byteCode
     * @return
     */
    public synchronized static boolean classReload(Instrumentation instrumentation, String className, String byteCode) {
        ByteCodeInfo byteCodeInfo = getClassByteCodeInfo(instrumentation, className);
        ByteCodeInfo orginalByteCodeInfo = BeanUtil.copyProperties(byteCodeInfo, ByteCodeInfo.class);
        try {
            if (byteCodeInfo != null && !byteCodeInfo.getAttachClassByteCodeType().equals(ByteCodeTypeEnum.agentWithByteBuddy)) {
                CompletableFuture<ByteCodeInfo> future = new CompletableFuture<>();
                Debug4jClassFileTransformer debug4jClassFileTransformer = new Debug4jClassFileTransformer(className,
                        CommandTypeEnum.ATTACH_REQ_CLASS_RELOAD, null, byteCode, future, byteCodeInfo);
                reTransformer(instrumentation, className, debug4jClassFileTransformer);
                realByteCodeMap.put(className, future.get(30, TimeUnit.SECONDS));
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            realByteCodeMap.put(className, orginalByteCodeInfo);
        }
        return false;
    }

    /**
     * 代码还原
     *
     * @param instrumentation
     * @param className
     */
    public synchronized static void classRestore(Instrumentation instrumentation, String className) {
        try {
            ByteCodeInfo byteCodeInfo = getClassByteCodeInfo(instrumentation, className);
            if (byteCodeInfo != null) {
                CompletableFuture<ByteCodeInfo> future = new CompletableFuture<>();
                Debug4jClassFileTransformer debug4jClassFileTransformer = new Debug4jClassFileTransformer(className,
                        CommandTypeEnum.ATTACH_REQ_CLASS_RESTORE, null, null, future, byteCodeInfo);
                reTransformer(instrumentation, className, debug4jClassFileTransformer);
                Map<String, URL> originalClass = loadOriginalClass(className);
                originalClass.keySet().forEach(innerClassName -> {
                    if (!innerClassName.equals(className)) {
                        try {
                            ByteCodeInfo innerByteCodeInfo = getClassByteCodeInfo(instrumentation, innerClassName);
                            Debug4jClassFileTransformer debug4jClassFileTransformerInner = new Debug4jClassFileTransformer(innerClassName,
                                    CommandTypeEnum.ATTACH_REQ_CLASS_RESTORE, null, null, null, innerByteCodeInfo);
                            reTransformer(instrumentation, innerClassName, debug4jClassFileTransformerInner);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                realByteCodeMap.put(className, future.get(30, TimeUnit.SECONDS));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 重载类
     *
     * @param instrumentation
     * @param className
     * @param debug4jClassFileTransformer
     */
    private synchronized static void reTransformer(Instrumentation instrumentation, String className,
                                                   Debug4jClassFileTransformer debug4jClassFileTransformer) throws UnmodifiableClassException {
        instrumentation.addTransformer(debug4jClassFileTransformer, true);
        for (Class allLoadedClass : instrumentation.getAllLoadedClasses()) {
            if (allLoadedClass.getName().equals(className)) {
                try {
                    instrumentation.retransformClasses(allLoadedClass);
                } catch (Exception e) {
                    e.printStackTrace();
                    instrumentation.removeTransformer(debug4jClassFileTransformer);
                    throw e;
                }
                break;
            }
        }
        instrumentation.removeTransformer(debug4jClassFileTransformer);
    }

    /**
     * 获取源码代行号
     *
     * @param instrumentation
     * @param className
     * @param lineMethodName
     */
    public synchronized static MethodLineInfo methodLine(Instrumentation instrumentation, String className, String lineMethodName) {
        String[] lineMethodNameSplit = lineMethodName.split("@");
        String lineClassName = lineMethodNameSplit[0];
        lineMethodName = lineMethodNameSplit[1];
        className = className.substring(0, className.lastIndexOf(".")) + "." + lineClassName;
        ByteCodeInfo byteCodeInfo = getClassByteCodeInfo(instrumentation, className);
        byte[] classByteCodeByCache = getClassByteCodeByCache(SourceCodeTypeEnum.attachClassByteCode, byteCodeInfo);
        if (classByteCodeByCache != null) {
            try {
                ClassPool pool = ClassPool.getDefault();
                CtClass cc = pool.get(className);
                if (cc.isFrozen()) {
                    cc.defrost();
                }
                pool.makeClass(new ByteArrayInputStream(classByteCodeByCache));
                cc = pool.get(className);
                int methodIndex = 0;
                String realMethodName = lineMethodName;
                if (lineMethodName.contains("#")) {
                    methodIndex = Integer.parseInt(lineMethodName.split("#")[1]) - 1;
                    realMethodName = lineMethodName.split("#")[0];
                }
                CtMethod[] declaredMethods = cc.getDeclaredMethods(realMethodName);
                SortedSet<Integer> set = new TreeSet<>();
                CtMethod declaredMethod = declaredMethods[methodIndex];
                CodeIterator iterator = declaredMethod.getMethodInfo().getCodeAttribute().iterator();
                while (iterator.hasNext()) {
                    set.add(declaredMethod.getMethodInfo().getLineNumber(iterator.next()));
                }
                set.forEach(lineNumber -> {
                    try {
                        declaredMethod.insertAt(lineNumber, "{com.k4ln.debug4j.common.daemon.Debug4jLine.tag(" + lineNumber + ");}");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                byte[] bytecode = cc.toBytecode();
                String sourceCode = jadxByteCodeToSourceWithInner(instrumentation, className, SourceCodeTypeEnum.attachClassByteCode, lineClassName, bytecode);
                String flagSourceCode = sourceLineFlag(sourceCode);
                return MethodLineInfo.builder().sourceCode(flagSourceCode).classMethods(classMethods(instrumentation, className)).lineNumbers(set.stream().toList()).build();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return MethodLineInfo.builder().build();
    }

    /**
     * 源码行标记
     *
     * @param sourceCode
     * @return
     */
    private static String sourceLineFlag(String sourceCode) {
        if (StrUtil.isNotBlank(sourceCode)) {
            String lineSeparator = "\\n";
            String[] split = sourceCode.split(lineSeparator);
            Map<String, String> lineMap = new LinkedHashMap<>();
            for (int i = 0; i < split.length; i++) {
                String str = split[i];
                String regex = "Debug4jLine\\.tag\\((\\d+)\\);";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(str);
                if (matcher.find()) {
                    String group = matcher.group();
                    String newLine = str.replace(group, "/* next real line number is: " + matcher.group(1) + " */");
                    lineMap.remove(newLine);
                    lineMap.put(newLine, newLine + lineSeparator);
                } else {
                    lineMap.put(str + ":" + i, str + lineSeparator);
                }
            }
            StringBuilder stringBuilder = new StringBuilder();
            lineMap.values().forEach(stringBuilder::append);
            return stringBuilder.toString();
        } else {
            return sourceCode;
        }
    }

    /**
     * 原代码行补丁
     *
     * @param instrumentation
     * @param className
     * @param lineMethodName
     * @param sourceCode
     * @param lineNumber
     */
    public synchronized static void patchLine(Instrumentation instrumentation, String className, String lineMethodName,
                                              String sourceCode, Integer lineNumber) {
        try {
            String[] lineMethodNameSplit = lineMethodName.split("@");
            String lineClassName = lineMethodNameSplit[0];
            lineMethodName = lineMethodNameSplit[1];
            className = className.substring(0, className.lastIndexOf(".")) + "." + lineClassName;
            ByteCodeInfo byteCodeInfo = getClassByteCodeInfo(instrumentation, className);
            if (byteCodeInfo != null) {
                CompletableFuture<ByteCodeInfo> future = new CompletableFuture<>();
                Debug4jClassFileTransformer debug4jClassFileTransformer = new Debug4jClassFileTransformer(className,
                        CommandTypeEnum.ATTACH_REQ_CLASS_RELOAD_JAVA_LINE, sourceCode, null, future, byteCodeInfo, lineMethodName, lineNumber);
                reTransformer(instrumentation, className, debug4jClassFileTransformer);
                realByteCodeMap.put(className, future.get(30, TimeUnit.SECONDS));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

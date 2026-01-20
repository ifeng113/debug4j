package com.k4ln.debug4j.core.attach;

import cn.hutool.core.util.StrUtil;
import com.k4ln.debug4j.common.protocol.command.CommandTypeEnum;
import com.k4ln.debug4j.common.protocol.command.message.enums.ByteCodeTypeEnum;
import com.k4ln.debug4j.common.protocol.command.message.enums.SourceCodeTypeEnum;
import com.k4ln.debug4j.common.utils.FileUtils;
import com.k4ln.debug4j.core.attach.dto.ByteCodeInfo;
import com.k4ln.debug4j.core.attach.dto.MethodLineInfo;
import com.k4ln.debug4j.core.attach.dto.SourceCodeInfo;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.CodeIterator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class Debug4jAttachOperator {

    /**
     * className -> ByteCodeInfo
     */
    @Getter
    private static final Map<String, ByteCodeInfo> realByteCodeMap = new ConcurrentHashMap<>();

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
            if (!allLoadedClass.getName().contains("$") // 过滤内部类
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
        String classSource = jadxByteCodeToSource(className, getClassByteCodeByCache(sourceCodeType, byteCodeInfo));
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
    public static ByteCodeInfo getClassByteCodeInfo(Instrumentation instrumentation, String className) {
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
            JadxArgs jadxArgs = new JadxArgs();
            jadxArgs.setInputFile(file);
            jadxArgs.setDebugInfo(false);
            jadxArgs.setCodeNewLineStr("\n");
            JadxDecompiler jadx = new JadxDecompiler(jadxArgs);
            jadx.load();
            for (JavaClass cls : jadx.getClasses()) {
                return cls.getCode();
            }
            jadx.close();
            file.deleteOnExit();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取类方法
     *
     * @param instrumentation
     * @param className
     * @return
     */
    public static List<String> classMethods(Instrumentation instrumentation, String className) {
        List<String> classMethods = new ArrayList<>();
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
                CtMethod[] declaredMethods = cc.getDeclaredMethods();
                Map<String, Integer> counter = new HashMap<>();
                for (CtMethod declaredMethod : declaredMethods) {
                    int count = counter.merge(declaredMethod.getName(), 1, Integer::sum);
                    if (count == 1) {
                        classMethods.add(declaredMethod.getName());
                    } else {
                        classMethods.add(declaredMethod.getName() + "#" + count);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return classMethods;
    }

    /**
     * 源码热更新
     *
     * @param instrumentation
     * @param className
     * @param sourceCode
     */
    public static void sourceReload(Instrumentation instrumentation, String className, String sourceCode) {
        try {
            ByteCodeInfo byteCodeInfo = getClassByteCodeInfo(instrumentation, className);
            if (byteCodeInfo != null && !byteCodeInfo.getAttachClassByteCodeType().equals(ByteCodeTypeEnum.agentWithByteBuddy)) {
                CompletableFuture<ByteCodeInfo> future = new CompletableFuture<>();
                Debug4jClassFileTransformer debug4jClassFileTransformer = new Debug4jClassFileTransformer(className,
                        CommandTypeEnum.ATTACH_REQ_CLASS_RELOAD_JAVA, sourceCode, null, future, byteCodeInfo);
                reTransformer(instrumentation, className, debug4jClassFileTransformer);
                realByteCodeMap.put(className, future.get(30, TimeUnit.SECONDS));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 字节码热更新
     *
     * @param instrumentation
     * @param className
     * @param byteCode
     */
    public static void classReload(Instrumentation instrumentation, String className, String byteCode) {
        try {
            ByteCodeInfo byteCodeInfo = getClassByteCodeInfo(instrumentation, className);
            if (byteCodeInfo != null && !byteCodeInfo.getAttachClassByteCodeType().equals(ByteCodeTypeEnum.agentWithByteBuddy)) {
                CompletableFuture<ByteCodeInfo> future = new CompletableFuture<>();
                Debug4jClassFileTransformer debug4jClassFileTransformer = new Debug4jClassFileTransformer(className,
                        CommandTypeEnum.ATTACH_REQ_CLASS_RELOAD, null, byteCode, future, byteCodeInfo);
                reTransformer(instrumentation, className, debug4jClassFileTransformer);
                realByteCodeMap.put(className, future.get(30, TimeUnit.SECONDS));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 代码还原
     *
     * @param instrumentation
     * @param className
     */
    public static void classRestore(Instrumentation instrumentation, String className) {
        try {
            ByteCodeInfo byteCodeInfo = getClassByteCodeInfo(instrumentation, className);
            if (byteCodeInfo != null) {
                CompletableFuture<ByteCodeInfo> future = new CompletableFuture<>();
                Debug4jClassFileTransformer debug4jClassFileTransformer = new Debug4jClassFileTransformer(className,
                        CommandTypeEnum.ATTACH_REQ_CLASS_RESTORE, null, null, future, byteCodeInfo);
                reTransformer(instrumentation, className, debug4jClassFileTransformer);
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
    private static void reTransformer(Instrumentation instrumentation, String className, Debug4jClassFileTransformer debug4jClassFileTransformer) throws UnmodifiableClassException {
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
    public static MethodLineInfo methodLine(Instrumentation instrumentation, String className, String lineMethodName) {
        ByteCodeInfo byteCodeInfo = getClassByteCodeInfo(instrumentation, className);
        byte[] classByteCodeByCache = getClassByteCodeByCache(SourceCodeTypeEnum.attachClassByteCode, byteCodeInfo);
        if (classByteCodeByCache != null) {
            if (StrUtil.isNotBlank(lineMethodName)) {
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
                    Integer first = set.first();
                    declaredMethod.insertAt(first, "{com.k4ln.debug4j.common.daemon.Debug4jLine.tag(" + first + ");}");
                    byte[] bytecode = cc.toBytecode();
                    String sourceCode = jadxByteCodeToSource(className, bytecode);
                    String flagSourceCode = sourceLineFlag(sourceCode);
                    return MethodLineInfo.builder().sourceCode(flagSourceCode).classMethods(classMethods(instrumentation, className)).lineNumbers(set.stream().toList()).build();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                String sourceCode = jadxByteCodeToSource(className, classByteCodeByCache);
                return MethodLineInfo.builder().sourceCode(sourceCode).classMethods(classMethods(instrumentation, className)).lineNumbers(new ArrayList<>()).build();
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
    public static void patchLine(Instrumentation instrumentation, String className, String lineMethodName, String sourceCode, Integer lineNumber) {
        try {
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

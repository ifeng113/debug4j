package com.k4ln.debug4j.core.attach;

import cn.hutool.core.codec.Base64Decoder;
import cn.hutool.core.codec.Base64Encoder;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.core.io.resource.Resource;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.MD5;
import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.common.protocol.command.CommandTypeEnum;
import com.k4ln.debug4j.common.protocol.command.message.enums.ByteCodeTypeEnum;
import com.k4ln.debug4j.common.utils.FileUtils;
import com.k4ln.debug4j.core.attach.compile.CompilerUtil;
import com.k4ln.debug4j.core.attach.compile.JavaSourceCompiler;
import com.k4ln.debug4j.core.attach.compile.ResourceClassLoader;
import com.k4ln.debug4j.core.attach.dto.ByteCodeInfo;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import lombok.extern.slf4j.Slf4j;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Debug4jClassFileTransformer implements ClassFileTransformer {

    private final String transformerClassName;

    private final CommandTypeEnum commandType;

    private final String sourceCode;

    private final String byteCode;

    private final CompletableFuture<ByteCodeInfo> future;

    private final ByteCodeInfo byteCodeInfo;

    private final String lineMethodName;

    private final Integer lineNumber;

    public Debug4jClassFileTransformer(String transformerClassName, CommandTypeEnum commandType, String sourceCode,
                                       String byteCode, CompletableFuture<ByteCodeInfo> future, ByteCodeInfo byteCodeInfo) {
        this.transformerClassName = transformerClassName;
        this.commandType = commandType;
        this.sourceCode = sourceCode;
        this.byteCode = byteCode;
        this.future = future;
        this.byteCodeInfo = byteCodeInfo;
        this.lineMethodName = StrUtil.EMPTY;
        this.lineNumber = Integer.MIN_VALUE;
    }

    public Debug4jClassFileTransformer(String transformerClassName, CommandTypeEnum commandType, String sourceCode,
                                       String byteCode, CompletableFuture<ByteCodeInfo> future, ByteCodeInfo byteCodeInfo,
                                       String lineMethodName, Integer lineNumber) {
        this.transformerClassName = transformerClassName;
        this.commandType = commandType;
        this.sourceCode = sourceCode;
        this.byteCode = byteCode;
        this.future = future;
        this.byteCodeInfo = byteCodeInfo;
        this.lineMethodName = lineMethodName;
        this.lineNumber = lineNumber;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className.replace("/", ".").equals(transformerClassName)) {
            if (commandType.equals(CommandTypeEnum.ATTACH_REQ_CLASS_RELOAD)) {
                byte[] bytes = Base64Decoder.decode(byteCode);
                byteCodeInfo.setAttachClassByteCode(bytes);
                future.complete(byteCodeInfo);
                return byteCodeInfo.getAttachClassByteCode();
            } else if (commandType.equals(CommandTypeEnum.ATTACH_REQ_CLASS_RELOAD_JAVA)) {
                return compilerSourceCodeByCacheClass(loader);
            } else if (commandType.equals(CommandTypeEnum.ATTACH_REQ_CLASS_SOURCE)) {
                byteCodeInfo.setAgentTransformClassBufferByteCode(classfileBuffer);
                String classFileSourceCode = classSignature(byteCodeInfo.getOriginalClassFileByteCode());
                String classBufferSourceCode = classSignature(byteCodeInfo.getAgentTransformClassBufferByteCode());
                String classSourceCode = classSignature(byteCodeInfo.getAgentTransformClassByteCode());
                if (StrUtil.isBlank(classFileSourceCode) || StrUtil.isBlank(classBufferSourceCode) || StrUtil.isBlank(classSourceCode)) {
                    throw new RuntimeException("class source code is null or empty");
                }
                if (!classFileSourceCode.equals(classBufferSourceCode)) {
                    byteCodeInfo.setAttachClassByteCodeType(ByteCodeTypeEnum.agentWithByteBuddy);
                    byteCodeInfo.setAttachClassByteCode(byteCodeInfo.getAgentTransformClassBufferByteCode());
                } else if (!classFileSourceCode.equals(classSourceCode)) {
                    byteCodeInfo.setAttachClassByteCodeType(ByteCodeTypeEnum.agentOnlyJavassist);
                    byteCodeInfo.setAttachClassByteCode(byteCodeInfo.getAgentTransformClassByteCode());
                } else {
                    byteCodeInfo.setAttachClassByteCodeType(ByteCodeTypeEnum.original);
                    byteCodeInfo.setAttachClassByteCode(byteCodeInfo.getOriginalClassFileByteCode());
                }
                future.complete(byteCodeInfo);
                return byteCodeInfo.getAttachClassByteCode();
            } else if (commandType.equals(CommandTypeEnum.ATTACH_REQ_CLASS_RESTORE)) {
                byte[] defaultByteCode = getDefaultByteCode(byteCodeInfo);
                byteCodeInfo.setAttachClassByteCode(defaultByteCode);
                future.complete(byteCodeInfo);
                return byteCodeInfo.getAttachClassByteCode();
            } else if (commandType.equals(CommandTypeEnum.ATTACH_REQ_CLASS_RELOAD_JAVA_LINE)) {
                try {
                    int methodIndex = 0;
                    String realMethodName = lineMethodName;
                    if (lineMethodName.contains("#")) {
                        methodIndex = Integer.parseInt(lineMethodName.split("#")[1]) - 1;
                        realMethodName = lineMethodName.split("#")[0];
                    }
                    ClassPool pool = ClassPool.getDefault();
                    CtClass cc = pool.get(transformerClassName);
                    if (cc.isFrozen()) {
                        cc.defrost();
                    }
                    pool.makeClass(new ByteArrayInputStream(byteCodeInfo.getAttachClassByteCode()));
                    cc = pool.get(transformerClassName);
                    CtMethod[] declaredMethods = cc.getDeclaredMethods(realMethodName);
                    CtMethod declaredMethod = declaredMethods[methodIndex];
                    declaredMethod.insertAt(lineNumber, sourceCode);
                    byte[] bytecode = cc.toBytecode();
                    byteCodeInfo.setAttachClassByteCode(bytecode);
                    future.complete(byteCodeInfo);
                    return byteCodeInfo.getAttachClassByteCode();
                } catch (Exception e) {
                    e.printStackTrace();
                    future.complete(byteCodeInfo);
                    return byteCodeInfo.getAttachClassByteCode();
                }
            }
        }
        return classfileBuffer;
    }

    private byte[] compilerSourceCodeByCacheClass(ClassLoader loader) {
        File file = new File(FileUtils.createTempDir(), transformerClassName.substring(transformerClassName.lastIndexOf('.') + 1) + ".java");
        FileWriter.create(file).write(sourceCode);
        JavaSourceCompiler javaSourceCompiler = CompilerUtil.getCompiler(loader).addSource(file);
        ResourceClassLoader classLoader = (ResourceClassLoader) javaSourceCompiler.compile();
        Map resourceMap = classLoader.getResourceMap();
        Resource resource = (Resource) resourceMap.get(transformerClassName);
        file.deleteOnExit();
        byte[] bytes = resource.readBytes();
        byteCodeInfo.setAttachClassByteCode(bytes);
        future.complete(byteCodeInfo);
        return byteCodeInfo.getAttachClassByteCode();
    }

    private byte[] compilerSourceCodeByClassFile() {
        File tempDir = FileUtils.createTempDir();
        String classLastName = transformerClassName.substring(transformerClassName.lastIndexOf('.') + 1);
        File file = new File(tempDir, classLastName + ".java");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(sourceCode.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager standardFileManager = javaCompiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> javaFileObjects = standardFileManager.getJavaFileObjects(file);
        JavaCompiler.CompilationTask task = javaCompiler.getTask(null, standardFileManager, null, null, null, javaFileObjects);
        task.call();
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(tempDir.getAbsolutePath() + File.separator + classLastName + ".class"));
            byteCodeInfo.setAttachClassByteCode(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        future.complete(byteCodeInfo);
        return byteCodeInfo.getAttachClassByteCode();
    }

    private String classSignature(byte[] byteCode) {
        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass cc = pool.get(transformerClassName);
            if (cc.isFrozen()) {
                cc.defrost();
            }
            pool.makeClass(new ByteArrayInputStream(byteCode));
            cc = pool.get(transformerClassName);
            List<String> fieldList = new java.util.ArrayList<>(Arrays.stream(cc.getDeclaredFields()).map(e -> e.getName() + e.getSignature()).toList());
            fieldList.sort(Comparator.naturalOrder());
            List<String> methodList = new java.util.ArrayList<>(Arrays.stream(cc.getDeclaredMethods()).map(e -> e.getName() + e.getSignature() +
                    MD5.create().digestHex(Base64Encoder.encode(e.getMethodInfo().getCodeAttribute().getCode()))).toList());
            methodList.sort(Comparator.naturalOrder());
            return cc.getName() + "@" + cc.getGenericSignature() + "@" + JSON.toJSONString(fieldList) + "@" + JSON.toJSONString(methodList);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取默认字节码
     *
     * @param byteCodeInfo
     * @return
     */
    private byte[] getDefaultByteCode(ByteCodeInfo byteCodeInfo) {
        if (byteCodeInfo.getAttachClassByteCodeType().equals(ByteCodeTypeEnum.agentWithByteBuddy)) {
            return byteCodeInfo.getAgentTransformClassBufferByteCode();
        } else if (byteCodeInfo.getAttachClassByteCodeType().equals(ByteCodeTypeEnum.agentOnlyJavassist)) {
            return byteCodeInfo.getAgentTransformClassByteCode();
        } else {
            return byteCodeInfo.getOriginalClassFileByteCode();
        }
    }

}

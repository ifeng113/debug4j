package com.k4ln.debug4j.core.attach.jvm.trace;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.*;

@Slf4j
public class Debug4jTraceInstaller {

    @Getter
    private static final Map<String, ResettableClassFileTransformer> transformerMap = new LinkedHashMap<>();

    @Getter
    private static final Map<String, String> classNameMethodMap = new HashMap<>();

    /**
     * 安装追踪
     * Controller 中的 @RequestParam 必须显示声明 name
     * 不支持方法体内部链路，如果需包含内部链路，可使用 Arthas（相关源码类：com.taobao.arthas.core.advisor.Enhancer#transform）
     *
     * @param inst
     * @param className
     * @param methodName
     */
    public static synchronized void install(Instrumentation inst, String className, String methodName) {
        ResettableClassFileTransformer transformer = transformerMap.get(className);
        if (transformer != null) {
            uninstall(inst, className);
        }
        transformer = new AgentBuilder.Default()
                .ignore(ElementMatchers.none())
                .with(AgentBuilder.Listener.StreamWriting.toSystemOut().withErrorsOnly()) // 打印错误，方便排查问题
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .type(ElementMatchers.hasSuperType(named(className))
                        .and(not(isInterface()))
                        .and(not(isAnnotation()))
                        .and(not(isEnum())))
                .transform((builder, type, cl, module, pd) -> {
                            ElementMatcher.Junction<MethodDescription> junction = ElementMatchers.isMethod().and(ElementMatchers.not(ElementMatchers.isConstructor()));
                            if (StrUtil.isNotBlank(methodName))
                                junction = junction.and(nameContains(methodName));
                            return builder.visit(Advice.to(Debug4jTraceLogAdvice.class).on(junction));  // 不能使用any，否则构造方法会报错
                        }
                ).installOn(inst);
        transformerMap.put(className, transformer);
        classNameMethodMap.put(className, methodName);
    }

    /**
     * 卸载追踪
     *
     * @param inst
     * @param className
     */
    public static synchronized void uninstall(Instrumentation inst, String className) {
        if (transformerMap.get(className) != null) {
            transformerMap.get(className).reset(inst, AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
            transformerMap.remove(className);
            classNameMethodMap.remove(className);
        }
    }

}


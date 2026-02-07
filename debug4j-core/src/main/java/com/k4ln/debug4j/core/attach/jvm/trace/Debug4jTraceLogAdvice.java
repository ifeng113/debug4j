package com.k4ln.debug4j.core.attach.jvm.trace;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * 【注意】IDEA不会进入此 Advice 断点，但能够进入内部的其他类的方法断点
 */
public class Debug4jTraceLogAdvice {

    @Advice.OnMethodEnter
    public static Debug4jTraceInfo enter(@Advice.AllArguments Object[] args,
                                         @Advice.Origin("#t.#m") String method) {
        return Debug4jTraceInfo.builder()
                .start(System.nanoTime())
                .method(method)
                .args(Debug4jTraceSafePrinter.printArgs(args))
                .traceId(Debug4jTraceSafePrinter.getTraceId())
                .build();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.Enter Debug4jTraceInfo traceInfo,
                            @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object ret, // (typing = Assigner.Typing.DYNAMIC) 防止void方法报错
                            @Advice.Thrown Throwable thrown) {
        traceInfo.setEnd(System.nanoTime());
        if (thrown == null) {
            traceInfo.setRet(Debug4jTraceSafePrinter.print(ret));
        } else {
            traceInfo.setThrowable(thrown.toString());
        }
        traceInfo.print();
        Debug4jTraceSafePrinter.clearTraceId();
    }
}


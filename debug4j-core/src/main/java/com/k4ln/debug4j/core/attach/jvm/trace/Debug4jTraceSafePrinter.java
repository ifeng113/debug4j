package com.k4ln.debug4j.core.attach.jvm.trace;

import java.io.*;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Set;
import java.util.stream.Collectors;

public class Debug4jTraceSafePrinter {

    private static final ThreadLocal<Deque<String>> traceIdStack = ThreadLocal.withInitial(ArrayDeque::new);

    private static final int MAX_LEN = 100;

    private static final Set<Class<?>> FORBIDDEN = Set.of(
            InputStream.class, OutputStream.class,
            Reader.class, Writer.class,
            Socket.class, File.class,
            Thread.class, ClassLoader.class
    );

    public static String printArgs(Object[] args) {
        if (args == null) return "[]";
        return Arrays.stream(args).map(Debug4jTraceSafePrinter::print)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    public static String print(Object obj) {
        if (obj == null) return "null";
        if (isForbidden(obj)) return "<" + obj.getClass().getSimpleName() + ":ignored>";
        try {
            String s = String.valueOf(obj);
            return s.length() > MAX_LEN ? s.substring(0, MAX_LEN) + "..." : s;
        } catch (Throwable t) {
            return "<toString-error>";
        }
    }

    private static boolean isForbidden(Object o) {
        return FORBIDDEN.stream().anyMatch(c -> c.isAssignableFrom(o.getClass()));
    }

    public static String getTraceId() {
        Deque<String> stack = traceIdStack.get();
        if (stack.isEmpty()) {
            String traceId = Thread.currentThread().getName() + "-" + Math.random();
            stack.push(traceId);
        }
        return stack.peek();
    }

    public static void clearTraceId() {
        Deque<String> stack = traceIdStack.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            traceIdStack.remove();
        }
    }
}

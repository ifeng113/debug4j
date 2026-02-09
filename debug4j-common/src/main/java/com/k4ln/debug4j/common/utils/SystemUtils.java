package com.k4ln.debug4j.common.utils;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.CharUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static com.k4ln.debug4j.common.utils.StringUtils.extractPort;

@Slf4j
public class SystemUtils {

    /**
     * 获取jdwp端口
     *
     * @return
     */
    public static Integer getJdwpPort() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        List<String> jvmArguments = runtimeMXBean.getInputArguments();
        for (String arg : jvmArguments) {
            if (arg.startsWith("-agentlib:jdwp=transport=dt_socket")) {
                String jdwpPort = extractPort(arg);
                if (jdwpPort != null) {
                    try {
                        return Integer.parseInt(jdwpPort);
                    } catch (Exception e) {
                        log.warn("parsing jdwp port error arg:{} exception:{}", arg, e.getMessage());
                    }
                }
            }
        }
        return -1;
    }

    /**
     * 获取类名
     *
     * @param obj
     * @return
     */
    public static String getClassName(Object obj) {
        return getClass(obj).getName();
    }

    /**
     * 获取类名
     *
     * @param cls
     * @return
     */
    public static String getClassName(Class cls) {
        return cls.getName();
    }

    /**
     * 获取类Class
     *
     * @param obj
     * @return
     */
    public static Class<?> getClass(Object obj) {
        if (obj == null) return null;
        Class<?> clazz = obj.getClass();
        if (java.lang.reflect.Proxy.isProxyClass(clazz)) {
            Class<?>[] interfaces = clazz.getInterfaces();
            if (interfaces.length > 0) {
                return interfaces[0];
            }
            return clazz;
        }
        while (clazz != null) {
            String name = clazz.getName();
            if (isProxyName(name)) {
                clazz = clazz.getSuperclass();
            } else {
                break;
            }
        }
        return clazz;
    }

    /**
     * 是否为代理名称
     *
     * @param name
     * @return
     */
    private static boolean isProxyName(String name) {
        return name.contains("$$")
                || name.contains("$ByteBuddy$")
                || name.contains("_$$_jvst")
                || name.contains("CGLIB");
    }


    /**
     * 执行命令
     *
     * @param cmds
     * @return
     */
    public static Process exec(String... cmds) {
        Process process;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(handleCmds(cmds)).redirectErrorStream(true);
            // 避免子进程阻塞，支持日志回显【更改logback.xml的配置：<discardingThreshold>100</discardingThreshold>】
            processBuilder.inheritIO();
            process = processBuilder.start();
        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
        return process;
    }

    /**
     * 处理命令
     *
     * @param cmds
     * @return
     */
    private static String[] handleCmds(String... cmds) {
        if (ArrayUtil.isEmpty(cmds)) {
            throw new NullPointerException("Command is empty !");
        }
        if (1 == cmds.length) {
            final String cmd = cmds[0];
            if (StrUtil.isBlank(cmd)) {
                throw new NullPointerException("Command is blank !");
            }
            cmds = cmdSplit(cmd);
        }
        return cmds;
    }

    /**
     * 命令拆分
     *
     * @param cmd
     * @return
     */
    private static String[] cmdSplit(String cmd) {
        final List<String> cmds = new ArrayList<>();
        final int length = cmd.length();
        final Stack<Character> stack = new Stack<>();
        boolean inWrap = false;
        final StrBuilder cache = StrUtil.strBuilder();
        char c;
        for (int i = 0; i < length; i++) {
            c = cmd.charAt(i);
            switch (c) {
                case CharUtil.SINGLE_QUOTE:
                case CharUtil.DOUBLE_QUOTES:
                    if (inWrap) {
                        if (c == stack.peek()) {
                            stack.pop();
                            inWrap = false;
                        }
                        cache.append(c);
                    } else {
                        stack.push(c);
                        cache.append(c);
                        inWrap = true;
                    }
                    break;
                case CharUtil.SPACE:
                    if (inWrap) {
                        cache.append(c);
                    } else {
                        cmds.add(cache.toString());
                        cache.reset();
                    }
                    break;
                default:
                    cache.append(c);
                    break;
            }
        }
        if (cache.hasContent()) {
            cmds.add(cache.toString());
        }
        return cmds.toArray(new String[0]);
    }
}

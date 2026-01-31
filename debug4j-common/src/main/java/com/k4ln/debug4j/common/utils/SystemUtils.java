package com.k4ln.debug4j.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

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

    private static boolean isProxyName(String name) {
        return name.contains("$$")
                || name.contains("$ByteBuddy$")
                || name.contains("_$$_jvst")
                || name.contains("CGLIB");
    }
}

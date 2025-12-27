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
}

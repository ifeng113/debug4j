package com.k4ln.debug4j.daemon;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RuntimeUtil;
import com.k4ln.debug4j.core.Debugger;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class Debug4jDaemon {

    public static final String DEBUG4J_THREAD_NAME = "debug4j-daemon";

    /**
     * 开启调试调度器
     * @param application
     * @param host
     * @param port
     * @param key
     */
    public static void start(String application, String host, Integer port, String key) {
        startWithProcessMode(application, host, port, key);
    }

    /**
     * 开启调试调度器（带模式）
     * @param application
     * @param host
     * @param port
     * @param key
     */
    public static void start(String application, String host, Integer port, String key, Debug4jMode debug4jMode) {
        if (debug4jMode != null && debug4jMode.equals(Debug4jMode.thread)){
            Debugger.start(application, host, port, key, ProcessHandle.current().pid(), null, Debug4jMode.thread);
        } else {
            startWithProcessMode(application, host, port, key);
        }
    }

    /**
     * 开启进程模式
     */
    private static void startWithProcessMode(String application, String host, Integer port, String key) {
        Debug4jArgs debug4jArgs = loadDebug4jArgs(application, host, port, key);
        Debug4jDaemonThread debug4jDaemonThread = new Debug4jDaemonThread(debug4jArgs);
        Thread thread = ThreadUtil.newThread(debug4jDaemonThread, DEBUG4J_THREAD_NAME, true);
        thread.start();
        RuntimeUtil.addShutdownHook(() -> {
            Process process = debug4jDaemonThread.getProcess();
            if (process != null) {
                process.destroy();
            }
        });
    }

    /**
     * 装载参数
     * @return
     */
    private static Debug4jArgs loadDebug4jArgs(String application, String host, Integer port, String key) {
        Debug4jArgs debug4jArgs = Debug4jArgs.builder()
                .application(application)
                .host(host)
                .port(port)
                .key(key)
                .pid(ProcessHandle.current().pid())
                .build();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        List<String> jvmArguments = runtimeMXBean.getInputArguments();
        for (String arg : jvmArguments) {
            if (arg.startsWith("-agentlib:jdwp=transport=dt_socket")) {
                String jdwpPort = extractPort(arg);
                if (jdwpPort != null) {
                    try {
                        debug4jArgs.setJdwpPort(Integer.parseInt(jdwpPort));
                    } catch (Exception e){
                        log.warn("debug4j daemon match jdwp port error arg:{} exception:{}", arg, e.getMessage());
                    }
                }
            }
        }
        debug4jArgs.setThreadName(Thread.currentThread().getName());
        return debug4jArgs;
    }

    /**
     * 提取 JDWP 参数中的端口号
     *
     * @param input 输入的参数字符串
     * @return 提取的端口号，找不到时返回 null
     */
    public static String extractPort(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        // 正则表达式匹配 address 后的端口号
        String regex = "address=[^:]*:(\\d+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1) : null;
    }
}

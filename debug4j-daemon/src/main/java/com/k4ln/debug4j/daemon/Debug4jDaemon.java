package com.k4ln.debug4j.daemon;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import com.k4ln.debug4j.common.daemon.Debug4jArgs;
import com.k4ln.debug4j.common.daemon.Debug4jCommand;
import com.k4ln.debug4j.common.daemon.Debug4jMode;
import com.k4ln.debug4j.common.utils.SystemUtils;
import com.k4ln.debug4j.core.Debugger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Debug4jDaemon {

    public static final String DEBUG4J_THREAD_NAME = "debug4j-daemon";

    /**
     * 开启调试调度器
     *
     * @param proxyMode
     * @param application
     * @param packageName
     * @param host
     * @param port
     * @param key
     * @param command
     * @param developer
     */
    public static void start(Boolean proxyMode, String application, String packageName, String host, Integer port, String key,
                             Debug4jCommand command, boolean developer) {
        String uniqueId = UUID.fastUUID().toString(true);
        Debug4jArgs debug4jArgs = loadDebug4jArgs(application, uniqueId, packageName, host, port, key);
        Debugger.start(application, uniqueId, packageName, host, port, key, ProcessHandle.current().pid(), debug4jArgs.getJdwpPort(), Debug4jMode.thread, command);
        if (proxyMode != null && proxyMode && StrUtil.isBlank(command.getRootUniqueId())) {
            startProxyProcess(debug4jArgs, developer);
        }
    }

    /**
     * 开启代理进程
     *
     * @param debug4jArgs
     * @param developer
     */
    private static void startProxyProcess(Debug4jArgs debug4jArgs, boolean developer) {
        Debug4jDaemonThread debug4jDaemonThread = new Debug4jDaemonThread(debug4jArgs, developer);
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
     * @param application
     * @param uniqueId
     * @param packageName
     * @param host
     * @param port
     * @param key
     * @return
     */
    private static Debug4jArgs loadDebug4jArgs(String application, String uniqueId, String packageName, String host, Integer port, String key) {
        return Debug4jArgs.builder()
                .application(application)
                .packageName(packageName)
                .uniqueId(uniqueId)
                .host(host)
                .port(port)
                .key(key)
                .pid(ProcessHandle.current().pid())
                .jdwpPort(SystemUtils.getJdwpPort())
                .threadName(Thread.currentThread().getName())
                .build();
    }

}

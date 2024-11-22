package com.k4ln.debug4j.core;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.k4ln.debug4j.common.daemon.Debug4jMode;
import com.k4ln.debug4j.common.protocol.command.message.CommandInfoMessage;
import com.k4ln.debug4j.core.attach.Debug4jClassFileTransformer;
import com.k4ln.debug4j.core.client.SocketClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.ByteBuddyAgent;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Debugger {

    private static SocketClient socketClient;

    private static CommandInfoMessage commandInfoMessage;

    private static ScheduledThreadPoolExecutor scheduledExecutor;

    @Getter
    private static Instrumentation instrumentation;

    /**
     * 开启调试器
     * @param application
     * @param uniqueId
     * @param packageName
     * @param host
     * @param port
     * @param key
     * @param pid
     * @param jdwpPort
     * @param debug4jMode
     */
    public static void start(String application, String uniqueId, String packageName, String host, Integer port, String key,
                             Long pid, Integer jdwpPort, Debug4jMode debug4jMode) {
        if (debug4jMode.equals(Debug4jMode.thread)) {
            instrumentation = ByteBuddyAgent.install();
        }
        commandInfoMessage = CommandInfoMessage.builder()
                .applicationName(application)
                .packageName(packageName)
                .socketClientHost(NetUtil.getLocalHostName())
                .socketClientIp(NetUtil.getLocalhostStr())
                .uniqueId(uniqueId)
                .pid(pid)
                .jdwpPort(jdwpPort)
                .debug4jMode(debug4jMode)
                .build();
        scheduledExecutor = ThreadUtil.createScheduledExecutor(10);
        scheduledExecutor.scheduleWithFixedDelay(buildKeepAliveRunnable(host, port, key), 0, 10, TimeUnit.SECONDS);
    }

    public static void shutdown() {
        scheduledExecutor.shutdown();
        if (socketClient != null) {
            socketClient.shutdown();
        }
    }

    /**
     * 构建检查线程
     *
     * @return
     */
    private static Runnable buildKeepAliveRunnable(String host, Integer port, String key) {
        return () -> {
            try {
                if (socketClient == null || !socketClient.isAlive()) {
                    socketClient = new SocketClient(key, host, port, commandInfoMessage);
                    socketClient.start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
    }
}

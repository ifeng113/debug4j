package com.k4ln.debug4j.core.attach.jvm;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.common.daemon.Debug4jCommand;
import com.k4ln.debug4j.core.Debugger;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Debug4jProcessOperator {

    /**
     * 新建进程重载模式
     */
    public static ProcessBuilder processBuilder = null;

    public static Process process = null;

    /**
     * 当前进程重载模式
     */


    /**
     * 新建子进程重启
     */
    public static synchronized void restart() {
        if (StrUtil.isBlank(Debugger.getDebug4jCommand().getRootUniqueId())     // 仅支持根节点进程
                && Debugger.getDebug4jCommand().getReloadMode().equals(Debug4jCommand.ReloadMode.Restart)) {
            try {
                if (processBuilder == null) {
                    Debugger.getDebug4jCommand().getReloadHandler().accept(null);
                }
                if (process != null && process.isAlive()) {
                    process.destroy();
                }
                processBuilder = new ProcessBuilder(getRestartCommand());
                processBuilder.redirectErrorStream(true);
                processBuilder.inheritIO();
                process = processBuilder.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取重启指令
     * @return
     */
    private static List<String> getRestartCommand() {
        List<String> command = new ArrayList<>();
        String javaBin = ProcessHandle.current().info().command()
                .orElseGet(() -> System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        command.add(javaBin);
        if (jvmArgs != null && !jvmArgs.isEmpty()) {
            // fixme: remove -agentlib:jdwp
            log.info("jvmArgs: {}", JSON.toJSONString(jvmArgs));
            command.addAll(jvmArgs.stream().filter(e -> !e.startsWith("-agentlib:jdwp")).toList());
        }
        if (StrUtil.isNotBlank(Debugger.getDebug4jCommand().getJarPath())) {
            command.add("-jar");
            command.add(Debugger.getDebug4jCommand().getJarPath());
        } else {
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(Debugger.getDebug4jCommand().getCls().getName());
        }
        if (Debugger.getDebug4jCommand().getOriginalArgs() != null && !Debugger.getDebug4jCommand().getOriginalArgs().isEmpty()) {
            command.addAll(Debugger.getDebug4jCommand().getOriginalArgs());
        }
        String rootUniqueId = StrUtil.isNotBlank(Debugger.getDebug4jCommand().getRootUniqueId()) ?
                Debugger.getDebug4jCommand().getRootUniqueId() : Debugger.getCommandInfoMessage().getUniqueId();
        command.add("--debug4j-root-uniqueId=" + rootUniqueId);
        return command;
    }

}

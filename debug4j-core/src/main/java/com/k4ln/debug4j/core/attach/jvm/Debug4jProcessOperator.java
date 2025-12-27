package com.k4ln.debug4j.core.attach.jvm;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.system.SystemUtil;
import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.common.daemon.Debug4jCommand;
import com.k4ln.debug4j.common.protocol.command.message.CommandProcessReqMessage;
import com.k4ln.debug4j.common.utils.StringUtils;
import com.k4ln.debug4j.core.Debugger;
import com.k4ln.debug4j.core.attach.dto.ProcessArgsInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class Debug4jProcessOperator {

    /**
     * 新建进程重载模式
     */
    public static ProcessBuilder processBuilder = null;

    public static Process process = null;

    /**
     * 进程重载
     */
    public static ProcessArgsInfo reload(CommandProcessReqMessage processReq) {
        if (StrUtil.isBlank(Debugger.getDebug4jCommand().getRootUniqueId())) {
            if (Debugger.getDebug4jCommand().getReloadMode().equals(Debug4jCommand.ReloadMode.Reload)) {
                restartCurrentProcess(processReq);
                return getProcessArgsInfo();
            } else if (Debugger.getDebug4jCommand().getReloadMode().equals(Debug4jCommand.ReloadMode.Restart)) {
                restartChildProcess(processReq);
                // todo，子进程
                return ProcessArgsInfo.builder().build();
            }
        }
        return ProcessArgsInfo.builder().build();
    }

    /**
     * 获取进程参数
     */
    public static ProcessArgsInfo getProcessArgsInfo() {
        return ProcessArgsInfo.builder()
                .jvmArgs(ManagementFactory.getRuntimeMXBean().getInputArguments())
                .programArgs(Debugger.getDebug4jCommand().getOriginalArgs())
                .envs(System.getenv()
                        .entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .toList())
                .jvmRuntimeInfo(SystemUtil.getJavaRuntimeInfo().toString())
                .build();
    }

    /**
     * 当前进程重载模式
     */
    public static synchronized void restartCurrentProcess(CommandProcessReqMessage processReq) {
        Debugger.getDebug4jCommand().getReloadCloseHandler().accept(null);
        Debugger.getDebug4jCommand().getReloadStartHandler().accept(null);
    }

    /**
     * 新建子进程模式
     */
    public static synchronized void restartChildProcess(CommandProcessReqMessage processReq) {
        try {
            if (processBuilder == null) {
                Debugger.getDebug4jCommand().getReloadCloseHandler().accept(null);
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

    /**
     * 获取重启指令
     *
     * @return
     */
    private static List<String> getRestartCommand() {
        List<String> command = new ArrayList<>();
        String javaBin = ProcessHandle.current().info().command()
                .orElseGet(() -> System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        command.add(javaBin);
        List<String> originalJvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        if (originalJvmArgs != null && !originalJvmArgs.isEmpty()) {
            log.info("originalJvmArgs: {}", JSON.toJSONString(originalJvmArgs));
            Optional<String> any = originalJvmArgs.stream().filter(e -> e.startsWith("-agentlib:jdwp")).findAny();
            if (any.isPresent()) {
                String port = StringUtils.extractPort(any.get());
                if (StrUtil.isNotBlank(port)) {
                    String newJdwpArg = any.get().replace(port, String.valueOf(NetUtil.getUsableLocalPort()));
                    newJdwpArg = newJdwpArg.replace("server=n", "server=y");
                    newJdwpArg = newJdwpArg.replace("suspend=y", "suspend=n");
                    command.add(newJdwpArg);
                }
            }
            command.addAll(originalJvmArgs.stream().filter(e -> !e.startsWith("-agentlib:jdwp")).toList());
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
            log.info("originalProgramArgs: {}", JSON.toJSONString(Debugger.getDebug4jCommand().getOriginalArgs()));
            command.addAll(Debugger.getDebug4jCommand().getOriginalArgs());
        }
        String rootUniqueId = StrUtil.isNotBlank(Debugger.getDebug4jCommand().getRootUniqueId()) ?
                Debugger.getDebug4jCommand().getRootUniqueId() : Debugger.getCommandInfoMessage().getUniqueId();
        command.add("--debug4j-root-uniqueId=" + rootUniqueId);
        log.info("newCommand:{}", JSON.toJSONString(command));
        return command;
    }

}

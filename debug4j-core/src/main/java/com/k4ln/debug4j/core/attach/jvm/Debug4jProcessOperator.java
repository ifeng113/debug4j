package com.k4ln.debug4j.core.attach.jvm;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.system.SystemUtil;
import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.common.daemon.Debug4jCommand;
import com.k4ln.debug4j.common.daemon.enums.ExtendedHookType;
import com.k4ln.debug4j.common.daemon.enums.ReloadMode;
import com.k4ln.debug4j.common.protocol.command.message.CommandProcessReqMessage;
import com.k4ln.debug4j.common.utils.StringUtils;
import com.k4ln.debug4j.core.Debugger;
import com.k4ln.debug4j.core.attach.dto.ProcessArgsInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.*;

@Slf4j
public class Debug4jProcessOperator {

    /**
     * 新建进程重载模式
     */
    public static ProcessBuilder processBuilder = null;

    public static Process process = null;

    /**
     * 进程重载
     *
     * @param processReq
     * @return
     */
    public static ProcessArgsInfo reload(CommandProcessReqMessage processReq) {
        if (StrUtil.isBlank(Debugger.getDebug4jCommand().getRootUniqueId())) {
            if (Debugger.getDebug4jCommand().getReloadMode().equals(ReloadMode.Reload)) {
                restartCurrentProcess(processReq);
                return getProcessArgsInfo();
            } else if (Debugger.getDebug4jCommand().getReloadMode().equals(ReloadMode.Restart)) {
                restartChildProcess(processReq);
            }
        }
        return ProcessArgsInfo.builder().build();
    }

    /**
     * 获取进程参数
     *
     * @return
     */
    public static ProcessArgsInfo getProcessArgsInfo() {
        Debug4jCommand debug4jCommand = Debugger.getDebug4jCommand();
        Map<String, List<String>> hookArgs = new LinkedHashMap<>();
        if (debug4jCommand.getExtendedHook() != null && debug4jCommand.getExtendedHook().get(ExtendedHookType.HOOK_ARGS) != null) {
            Object apply = debug4jCommand.getExtendedHook().get(ExtendedHookType.HOOK_ARGS).apply(null);
            if (apply instanceof LinkedHashMap) {
                hookArgs = (LinkedHashMap<String, List<String>>) apply;
            }
        }
        return ProcessArgsInfo.builder()
                .jvmArgs(ManagementFactory.getRuntimeMXBean().getInputArguments())
                .programArgs(debug4jCommand.getOriginalArgs())
                .properties(System.getProperties()
                        .entrySet()
                        .stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .toList())
                .envs(System.getenv()
                        .entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .toList())
                .hookArgs(hookArgs)
                .build();
    }

    /**
     * 当前进程重载模式
     */
    public static synchronized void restartCurrentProcess(CommandProcessReqMessage processReq) {
        Debugger.getDebug4jCommand().getReloadCloseHandler().accept(null);
        ProcessArgsInfo processArgsInfo = getProcessArgsInfo();
        processReq.getRemoveProperties().forEach(e -> {
            if (e.split("=").length == 2) {
                System.clearProperty(e.split("=")[0]);
            }
        });
        processReq.getAddProperties().forEach(e -> {
            if (e.split("=").length == 2) {
                System.setProperty(e.split("=")[0], e.split("=")[1]);
            }
        });
        processArgsInfo.getProgramArgs().removeAll(processReq.getRemoveProgramArgs());
        processArgsInfo.getProgramArgs().addAll(processReq.getAddProgramArgs());
        Debugger.getDebug4jCommand().getReloadStartHandler().accept(processArgsInfo.getProgramArgs());
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
            processBuilder = new ProcessBuilder(getRestartCommand(processReq));
            processBuilder.redirectErrorStream(true);
            processBuilder.inheritIO();
            Map<String, String> environment = new HashMap<>();
            processReq.getCoverEnvs().forEach(e -> {
                if (e.split("=").length == 2) {
                    environment.put(e.split("=")[0], e.split("=")[1]);
                }
            });
            List<String> environmentList = environment.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .toList();
            log.info("coverEnvs: {}", JSON.toJSONString(environmentList));
            processBuilder.environment().putAll(environment);
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
    private static List<String> getRestartCommand(CommandProcessReqMessage processReqMessage) {
        List<String> command = new ArrayList<>();
        String javaBin = ProcessHandle.current().info().command()
                .orElseGet(() -> System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        command.add(javaBin);
        List<String> originalJvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        if (originalJvmArgs != null && !originalJvmArgs.isEmpty()) {
            log.info("originalJvmArgs: {}", JSON.toJSONString(originalJvmArgs));
            List<String> newJvmArgs = new ArrayList<>(originalJvmArgs.stream()
                    .filter(e -> !processReqMessage.getRemoveJvmArgs().contains(e))
                    .filter(e -> !e.startsWith("-agentlib:jdwp")).toList());
            Optional<String> any = originalJvmArgs.stream()
                    .filter(e -> !processReqMessage.getRemoveJvmArgs().contains(e))
                    .filter(e -> e.startsWith("-agentlib:jdwp")).findAny();
            if (any.isPresent()) {
                String port = StringUtils.extractPort(any.get());
                if (StrUtil.isNotBlank(port)) {
                    String newJdwpArg = any.get().replace(port, String.valueOf(NetUtil.getUsableLocalPort()));
                    newJdwpArg = newJdwpArg.replace("server=n", "server=y");
                    newJdwpArg = newJdwpArg.replace("suspend=y", "suspend=n");
                    newJvmArgs.add(0, newJdwpArg);
                }
            }
            newJvmArgs.addAll(processReqMessage.getAddJvmArgs());
            log.info("newJvmArgs: {}", JSON.toJSONString(newJvmArgs));
            processReqMessage.getRemoveProperties().forEach(e -> {
                if (e.split("=").length == 2) {
                    newJvmArgs.add("-D" + e.split("=")[0]);
                }
            });
            processReqMessage.getAddProperties().forEach(e -> {
                if (e.split("=").length == 2) {
                    newJvmArgs.add("-D" + e);
                }
            });
            log.info("newJvmArgs with properties: {}", JSON.toJSONString(newJvmArgs));
            command.addAll(newJvmArgs);
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
            List<String> newProgramArgs = new ArrayList<>(Debugger.getDebug4jCommand().getOriginalArgs());
            newProgramArgs.removeAll(processReqMessage.getRemoveProgramArgs());
            newProgramArgs.addAll(processReqMessage.getAddProgramArgs());
            log.info("newProgramArgs: {}", JSON.toJSONString(Debugger.getDebug4jCommand().getOriginalArgs()));
            command.addAll(newProgramArgs);
        }
        String rootUniqueId = StrUtil.isNotBlank(Debugger.getDebug4jCommand().getRootUniqueId()) ?
                Debugger.getDebug4jCommand().getRootUniqueId() : Debugger.getCommandInfoMessage().getUniqueId();
        command.add("--debug4j-root-uniqueId=" + rootUniqueId);
        log.info("newCommand:{}", JSON.toJSONString(command));
        return command;
    }

}

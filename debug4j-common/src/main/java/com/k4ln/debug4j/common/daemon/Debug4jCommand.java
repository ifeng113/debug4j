package com.k4ln.debug4j.common.daemon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Consumer;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Debug4jCommand {

    /**
     * 重载模式
     */
    private ReloadMode reloadMode;

    /**
     * 重载启动处理器（Restart模式会忽略此处理器）
     */
    private Consumer<?> reloadStartHandler;

    /**
     * 重载关闭处理器
     */
    private Consumer<?> reloadCloseHandler;

    /**
     * 原始启动参数
     */
    private List<String> originalArgs;

    /**
     * 启动类
     */
    private Class cls;

    /**
     * 启动jar包路径
     */
    private String jarPath;

    /**
     * 根唯一ID
     */
    private String rootUniqueId;

    /**
     * 加载进程命令对象
     * @param sourceArgs
     * @param reloadMode
     * @return
     */
    public static Debug4jCommand loadDebug4jCommand(String[] sourceArgs, Debug4jCommand.ReloadMode reloadMode) {
        String rootUniqueId = null;
        for (String sourceArg : sourceArgs) {
            if (sourceArg.contains("--debug4j-root-uniqueId")) {
                rootUniqueId = sourceArg.split("=")[1];
            }
        }
        String command = System.getProperty("sun.java.command");
        if (command != null && !command.startsWith("org.springframework.boot.loader")) {
            command = command.split(" ")[0];
        }
        String jarPath = null;
        Class<?> cls = null;
        if (command != null) {
            if (command.endsWith(".jar")) {
                jarPath = command;
            } else {
                try {
                    cls = Class.forName(command);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        return Debug4jCommand.builder()
                .reloadMode(reloadMode)
                .originalArgs(List.of(sourceArgs))
                .jarPath(jarPath)
                .cls(cls)
                .rootUniqueId(rootUniqueId)
                .build();
    }

    /**
     * 重载模式
     */
    public enum ReloadMode {
        None,       // 不支持
        Reload,     // 当前进程重启（支持程序参数修改，不支持jvm参数修改，如动态开启jdwp,gc等）
        Restart,    // 新建子进程重启（支持程序参数修改，支持jvm参数修改，但如果原本存在jdwp的情况下，子进程的jdwp需重新链接）
    }
}

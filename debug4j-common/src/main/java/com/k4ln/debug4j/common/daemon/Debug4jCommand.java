package com.k4ln.debug4j.common.daemon;

import com.k4ln.debug4j.common.daemon.enums.ExtendedHookType;
import com.k4ln.debug4j.common.daemon.enums.ReloadMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

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
    private Consumer<List<String>> reloadStartHandler;

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
     * 扩展回调
     */
    private Map<ExtendedHookType, Function<?, ?>> extendedHook;

    /**
     * 加载进程命令对象
     *
     * @param sourceArgs
     * @param reloadMode
     * @return
     */
    public static Debug4jCommand loadDebug4jCommand(String[] sourceArgs, ReloadMode reloadMode) {
        return loadDebug4jCommand(sourceArgs, reloadMode, null);
    }

    /**
     * 加载进程命令对象
     *
     * @param sourceArgs
     * @param reloadMode
     * @param extendedHook
     * @return
     */
    public static Debug4jCommand loadDebug4jCommand(String[] sourceArgs, ReloadMode reloadMode, Map<ExtendedHookType, Function<?, ?>> extendedHook) {
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
                .extendedHook(extendedHook)
                .build();
    }
}

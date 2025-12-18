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
     * 重载处理器
     */
    private Consumer<?> reloadHandler;

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

    public enum ReloadMode {
        None,       // 不支持
        Reload,     // 当前进程重启（不支持jvm参数修改，如动态开启jdwp,gc等）
        Restart,    // 新建子进程重启（支持jvm参数修改，但如果原本存在jdwp的情况下，子进程的jdwp需重新链接）
    }
}

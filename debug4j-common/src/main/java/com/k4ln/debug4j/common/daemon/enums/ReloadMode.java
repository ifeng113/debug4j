package com.k4ln.debug4j.common.daemon.enums;

public enum ReloadMode {
    None,       // 不支持
    Reload,     // 当前进程重启（支持程序参数、系统属性修改；不支持jvm参数、环境变量修改，如动态开启jdwp,gc等）
    Restart,    // 新建子进程重启（支持程序参数、系统属性[受限，可结合AdjustmentTypeEnum.property食用]、jvm参数、环境变量修改，但如果原本存在jdwp的情况下，子进程的jdwp需使用新端口链接）
}

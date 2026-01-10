package com.k4ln.debug4j.common.protocol.command.message.enums;

public enum AdjustmentTypeEnum {
    log,                    // 日志级别
    property,               // 系统属性
    property_hook,          // 系统属性（hook）
    sftp_open,              // 开启SFTP服务
    sftp_close              // 关闭SFTP服务
}

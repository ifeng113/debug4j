package com.k4ln.debug4j.common.protocol.command.message.enums;

public enum AdjustmentTypeEnum {
    log,                    // 日志级别
    property,               // 系统属性
    property_hook,          // 系统属性（钩子）
    sftp_open,              // 开启SFTP
    sftp_close,             // 关闭SFTP
    jvm_heap,               // 内存快照
    jvm_jfr_start,          // 开启JFR
    jvm_jfr_end,            // 结束JFR
    jvm_list,               // JVM列表
    file_list,              // 文件列表
    file_upload,            // 上传文件
    file_remove,            // 删除文件（夹）
    file_download,          // 下载文件（夹）
    obj_test,               // 对象操作测试
}

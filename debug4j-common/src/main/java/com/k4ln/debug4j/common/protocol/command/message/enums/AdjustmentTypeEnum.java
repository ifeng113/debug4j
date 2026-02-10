package com.k4ln.debug4j.common.protocol.command.message.enums;

public enum AdjustmentTypeEnum {
    log,                    // 日志级别
    log_replay,             // 日志复制
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
    file_reader,            // 文件读取
    obj_info,               // 对象信息
    obj_field,              // 对象属性
    obj_method,             // 对象方法
    obj_trace,              // 对象追踪
    module_ssh,             // SSH组件
    module_arthas,          // Arthas组件
    module_proxy,           // Proxy组件
}

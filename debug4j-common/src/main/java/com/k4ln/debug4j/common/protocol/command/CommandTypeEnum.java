package com.k4ln.debug4j.common.protocol.command;

import lombok.Getter;
import lombok.Setter;

public enum CommandTypeEnum {

    LOG("0x0000", "日志"),

    INFO("0x0001", "信息"),

    PROXY_OPEN("0x0011", "开启代理"),
    PROXY_CLOSE("0x0012", "关闭代理"),

    // server -> client
    ATTACH_REQ_CLASS_ALL("0x0101", "请求所有类"),
    ATTACH_REQ_CLASS_SOURCE("0x0102", "请求类源码"),
    ATTACH_REQ_CLASS_SOURCE_LINE("0x0103", "请求类源码及行号"),
    ATTACH_REQ_CLASS_RELOAD("0x0104", "请求类重载"),
    ATTACH_REQ_CLASS_RELOAD_JAVA("0x0105", "请求类源码编译重载"),
    ATTACH_REQ_CLASS_RELOAD_JAVA_LINE("0x0106", "请求类源码行编译重载"),
    ATTACH_REQ_CLASS_RESTORE("0x0107", "请求类还原"),

    ATTACH_REQ_TASK("0x0201", "请求任务列表"),
    ATTACH_REQ_TASK_OPEN("0x0202", "请求开启任务"),
    ATTACH_REQ_TASK_CLOSE("0x0203", "请求关闭任务"),

    ATTACH_REQ_PROCESS_ARG("0x0301", "请求进程参数"),
    ATTACH_REQ_PROCESS_RELOAD("0x0302", "请求进程重启"),
    ATTACH_REQ_PROCESS_ADJUSTMENT("0x0303", "请求进程调整"),

    // client -> server
    ATTACH_RESP_CLASS_ALL("0x0901", "响应所有类"),
    ATTACH_RESP_CLASS_SOURCE("0x0902", "响应类源码"),
    ATTACH_RESP_CLASS_SOURCE_LINE("0x0903", "响应类源码"),
    ATTACH_RESP_TASK("0x0904", "响应任务列表"),
    ATTACH_RESP_TASK_DETAILS("0x0905", "响应任务详情"),

    ATTACH_RESP_PROCESS_ARG_DETAILS("0x0906", "响应进程详情"),
    ATTACH_RESP_PROCESS_ADJUSTMENT_RESULT("0x0907", "响应进程调整结果"),

    ;

    @Setter
    @Getter
    private String code;

    @Setter
    @Getter
    private String message;

    CommandTypeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 通过code获取协议类型
     *
     * @param code
     * @return
     */
    public static CommandTypeEnum getProtocolTypeByCode(String code) {
        CommandTypeEnum[] values = CommandTypeEnum.values();
        for (CommandTypeEnum value : values) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}

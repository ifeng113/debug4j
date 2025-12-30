package com.k4ln.debug4j.common.protocol.command.message;

import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.common.protocol.command.Command;
import com.k4ln.debug4j.common.protocol.command.CommandTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandProcessReqMessage {

    /**
     * 请求ID
     */
    private String reqId;

    /**
     * 移除的jvm参数
     */
    private List<String> removeJvmArgs;

    /**
     * 新增的jvm参数
     */
    private List<String> addJvmArgs;

    /**
     * 移除的程序参数
     */
    private List<String> removeProgramArgs;

    /**
     * 新增的程序参数
     */
    private List<String> addProgramArgs;

    /**
     * 移除的系统属性
     */
    private List<String> removeProperties;

    /**
     * 新增的系统属性
     */
    private List<String> addProperties;

    /**
     * 覆盖的环境变量
     */
    private List<String> coverEnvs;

    public static byte[] buildCommandProcessArgReqMessage(String reqId) {
        return (JSON.toJSONString(Command.builder()
                .command(CommandTypeEnum.ATTACH_REQ_PROCESS_ARG)
                .data(CommandProcessReqMessage.builder()
                        .reqId(reqId)
                        .build())
                .build())
        ).getBytes();
    }

    public static byte[] buildCommandProcessReloadReqMessage(String reqId,
                                                             List<String> removeJvmArgs, List<String> addJvmArgs,
                                                             List<String> removeProgramArgs, List<String> addProgramArgs,
                                                             List<String> removeProperties, List<String> addProperties,
                                                             List<String> coverEnvs) {
        return (JSON.toJSONString(Command.builder()
                .command(CommandTypeEnum.ATTACH_REQ_PROCESS_RELOAD)
                .data(CommandProcessReqMessage.builder()
                        .reqId(reqId)
                        .removeJvmArgs(removeJvmArgs)
                        .addJvmArgs(addJvmArgs)
                        .removeProgramArgs(removeProgramArgs)
                        .addProgramArgs(addProgramArgs)
                        .removeProperties(removeProperties)
                        .addProperties(addProperties)
                        .coverEnvs(coverEnvs)
                        .build())
                .build())
        ).getBytes();
    }

}

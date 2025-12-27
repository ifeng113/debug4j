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
public class CommandProcessRespMessage {

    /**
     * 请求ID
     */
    private String reqId;

    /**
     * jvm参数
     */
    private List<String> jvmArgs;

    /**
     * 程序参数
     */
    private List<String> programArgs;

    /**
     * 环境变量
     */
    private List<String> envs;

    /**
     * jvm运行信息
     */
    private String jvmRuntimeInfo;

    public static byte[] buildCommandProcessRespMessage(String reqId,
                                                        List<String> jvmArgs, List<String> programArgs,
                                                        List<String> envs, String jvmRuntimeInfo) {
        return (JSON.toJSONString(Command.builder()
                .command(CommandTypeEnum.ATTACH_RESP_PROCESS_ARG_DETAILS)
                .data(CommandProcessRespMessage.builder()
                        .reqId(reqId)
                        .jvmArgs(jvmArgs)
                        .programArgs(programArgs)
                        .envs(envs)
                        .jvmRuntimeInfo(jvmRuntimeInfo)
                        .build())
                .build())
        ).getBytes();
    }

}

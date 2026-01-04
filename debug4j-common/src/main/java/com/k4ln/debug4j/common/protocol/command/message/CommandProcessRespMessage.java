package com.k4ln.debug4j.common.protocol.command.message;

import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.common.protocol.command.Command;
import com.k4ln.debug4j.common.protocol.command.CommandTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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
     * 系统属性
     */
    private List<String> properties;

    /**
     * 环境变量
     */
    private List<String> envs;

    /**
     * 钩子参数
     */
    private Map<String, List<String>> hookArgs;

    public static byte[] buildCommandProcessRespMessage(String reqId,
                                                        List<String> jvmArgs, List<String> programArgs, List<String> properties,
                                                        List<String> envs, Map<String, List<String>> hookArgs) {
        return (JSON.toJSONString(Command.builder()
                .command(CommandTypeEnum.ATTACH_RESP_PROCESS_ARG_DETAILS)
                .data(CommandProcessRespMessage.builder()
                        .reqId(reqId)
                        .jvmArgs(jvmArgs)
                        .programArgs(programArgs)
                        .properties(properties)
                        .envs(envs)
                        .hookArgs(hookArgs)
                        .build())
                .build())
        ).getBytes();
    }

}

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
public class CommandTaskRespMessage {

    /**
     * 请求ID
     */
    private String reqId;

    /**
     * 任务列表
     */
    private List<CommandTaskReqMessage> commandTaskReqMessages;

    public static byte[] buildTaskRespMessage(String reqId, List<CommandTaskReqMessage> commandTaskReqMessages) {
        return (JSON.toJSONString(Command.builder()
                .command(CommandTypeEnum.ATTACH_RESP_TASK)
                .data(CommandTaskRespMessage.builder()
                        .reqId(reqId)
                        .commandTaskReqMessages(commandTaskReqMessages)
                        .build())
                .build())
        ).getBytes();
    }
}

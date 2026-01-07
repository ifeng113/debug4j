package com.k4ln.debug4j.common.protocol.command.message;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import com.k4ln.debug4j.common.protocol.command.Command;
import com.k4ln.debug4j.common.protocol.command.CommandTypeEnum;
import com.k4ln.debug4j.common.protocol.command.message.deserializer.SequentialMapDeserializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandProcessAdjustmentRespMessage {

    /**
     * 请求ID
     */
    private String reqId;

    /**
     * 调整结果
     */
    @JSONField(deserializeUsing = SequentialMapDeserializer.class)
    private Map<String, String> adjustmentResult;

    public static byte[] buildCommandProcessAdjustmentRespMessage(String reqId, Map<String, String> adjustmentResult) {
        return (JSON.toJSONString(Command.builder()
                .command(CommandTypeEnum.ATTACH_RESP_PROCESS_ARG_DETAILS)
                .data(CommandProcessAdjustmentRespMessage.builder()
                        .reqId(reqId)
                        .adjustmentResult(adjustmentResult)
                        .build())
                .build())
        ).getBytes();
    }

}

package com.k4ln.debug4j.common.protocol.command.message;

import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.common.protocol.command.Command;
import com.k4ln.debug4j.common.protocol.command.CommandTypeEnum;
import com.k4ln.debug4j.common.protocol.command.message.enums.AdjustmentTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandProcessAdjustmentReqMessage {

    /**
     * 请求ID
     */
    private String reqId;

    /**
     * 调整类型
     */
    private AdjustmentTypeEnum adjustmentType;

    /**
     * 调整内容
     */
    private Map<String, String> adjustmentContent;

    public static byte[] buildCommandProcessAdjustmentReqMessage(String reqId, AdjustmentTypeEnum adjustmentType, Map<String, String> adjustmentContent) {
        return (JSON.toJSONString(Command.builder()
                .command(CommandTypeEnum.ATTACH_REQ_PROCESS_ADJUSTMENT)
                .data(CommandProcessAdjustmentReqMessage.builder()
                        .reqId(reqId)
                        .adjustmentType(adjustmentType)
                        .adjustmentContent(adjustmentContent)
                        .build())
                .build())
        ).getBytes();
    }
}

package com.k4ln.debug4j.common.protocol.command.message;

import com.k4ln.debug4j.common.protocol.command.message.enums.AdjustmentTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

}

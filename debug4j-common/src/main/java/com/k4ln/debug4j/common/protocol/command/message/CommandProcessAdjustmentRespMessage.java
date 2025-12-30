package com.k4ln.debug4j.common.protocol.command.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandProcessAdjustmentRespMessage {

    /**
     * 请求ID
     */
    private String reqId;


}

package com.k4ln.debug4j.controller.vo;

import com.k4ln.debug4j.common.protocol.command.message.enums.AdjustmentTypeEnum;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessAdjustmentReqVO {

    /**
     * 客户端sessionId
     */
    @NotBlank
    private String clientSessionId;

    /**
     * 调整类型
     */
    private AdjustmentTypeEnum adjustmentType;


}

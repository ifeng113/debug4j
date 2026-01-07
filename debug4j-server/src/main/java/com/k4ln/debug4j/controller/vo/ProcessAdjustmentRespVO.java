package com.k4ln.debug4j.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessAdjustmentRespVO {

    /**
     * 调整结果
     */
    private Map<String, String> adjustmentResult;

}

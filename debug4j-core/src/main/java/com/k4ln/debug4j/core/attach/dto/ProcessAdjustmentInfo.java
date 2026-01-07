package com.k4ln.debug4j.core.attach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessAdjustmentInfo {

    /**
     * 调整结果
     */
    private Map<String, String> adjustmentResult;
}

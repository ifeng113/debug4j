package com.k4ln.debug4j.core.attach.jvm.logger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoggerInfo {

    /**
     * 名称
     */
    private String name;

    /**
     * 配置等级
     */
    private String configuredLevel;

    /**
     * 生效等级
     */
    private String effectiveLevel;

    @Override
    public String toString() {
        return "configuredLevel=" + configuredLevel + ", effectiveLevel=" + effectiveLevel;
    }
}

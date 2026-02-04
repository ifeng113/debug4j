package com.k4ln.debug4j.core.attach.jvm.logger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogReplayInfo {

    /**
     * 匹配方式
     */
    @Builder.Default
    private MatchType matchType = MatchType.CONTAIN;

    /**
     * 匹配字符串
     */
    private String matchString;

    /**
     * 日志名称
     */
    private String logFileName;

    /**
     * 操作类型
     */
    @Builder.Default
    private OperationType operationType = OperationType.ADD;

    public enum OperationType {
        ADD,
        REMOVE,
    }

    public enum MatchType {
        ALL,
        CONTAIN,
        REGEX
    }
}

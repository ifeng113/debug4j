package com.k4ln.debug4j.core.attach.dto;

import cn.hutool.core.io.file.Tailer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskInfo {

    /**
     * 请求ID
     */
    private String reqId;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 监听超时（分钟）
     */
    private Integer expire;

    /**
     * 文件监听器
     */
    private Tailer tailer;
}

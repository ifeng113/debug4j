package com.k4ln.debug4j.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachTaskRespVO {

    /**
     * 请求ID
     */
    private String reqId;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 客户端ID
     */
    private String loginId;

    /**
     * 预读行数
     */
    private Integer initReadLine;

    /**
     * 监听时间
     */
    private Long lastListenTime;

}

package com.k4ln.debug4j.controller.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachTaskOpenReqVO {

    /**
     * 客户端sessionId
     */
    @NotBlank
    private String clientSessionId;

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
    @Builder.Default
    private Integer initReadLine = 100;
}

package com.k4ln.debug4j.controller.vo;

import com.k4ln.debug4j.common.protocol.command.message.enums.SourceCodeTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachClassSourceReqVO {

    /**
     * 客户端sessionId
     */
    @NotBlank
    private String clientSessionId;

    /**
     * 类名
     */
    @NotBlank
    private String className;

    /**
     * 源码类型
     */
    @NotNull
    private SourceCodeTypeEnum sourceCodeType;

}

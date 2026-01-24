package com.k4ln.debug4j.controller.vo;

import com.k4ln.debug4j.common.protocol.command.message.enums.ByteCodeTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachClassSourceRespVO {

    /**
     * 源码
     */
    private String classSource;

    /**
     * 源码方法列表
     */
    private List<String> classMethods;

    /**
     * 字节码类型
     */
    private ByteCodeTypeEnum byteCodeType;

    /**
     * 状态（成功/失败）
     */
    private Boolean status;

}

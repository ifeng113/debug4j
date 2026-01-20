package com.k4ln.debug4j.core.attach.dto;

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
public class SourceCodeInfo {

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
}

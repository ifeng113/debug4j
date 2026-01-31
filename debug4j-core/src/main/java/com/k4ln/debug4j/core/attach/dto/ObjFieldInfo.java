package com.k4ln.debug4j.core.attach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjFieldInfo {

    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * 字段类型
     */
    private String fieldType;

    /**
     * 是否为Final
     */
    private Boolean isFinal;

    /**
     * 是否为Static
     */
    private Boolean isStatic;

    /**
     * 字段值
     */
    private Object fieldValue;

    /**
     * 签名信息
     */
    private String signature;

    @Override
    public String toString() {
        return fieldType + " " + fieldName;
    }

}

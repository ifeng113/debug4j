package com.k4ln.debug4j.core.attach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjMethodInfo {

    /**
     * 返回类型
     */
    private String returnType;

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 方法参数
     */
    private List<String> argTypeList;

    /**
     * 方法参数值
     */
    @Builder.Default
    private List<Object> argValues = new ArrayList<>();

    /**
     * 返回值
     */
    private Object returnValue;

    /**
     * 是否为Static
     */
    private Boolean isStatic;

    /**
     * 签名信息
     */
    private String signature;

    @Override
    public String toString() {
        return returnType + " " + methodName + "(" + String.join(", ", argTypeList) + ")";
    }
}

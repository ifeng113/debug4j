package com.k4ln.debug4j.core.attach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodLineInfo {

    /**
     * 源码
     */
    private String sourceCode;

    /**
     * 源码方法列表
     */
    private List<String> classMethods;

    /**
     * 行号
     */
    private List<Integer> lineNumbers;
}

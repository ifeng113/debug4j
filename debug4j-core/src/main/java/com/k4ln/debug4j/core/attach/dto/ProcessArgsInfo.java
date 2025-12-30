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
public class ProcessArgsInfo {

    /**
     * jvm参数
     */
    @Builder.Default
    private List<String> jvmArgs = new ArrayList<>();

    /**
     * 程序参数
     */
    @Builder.Default
    private List<String> programArgs = new ArrayList<>();

    /**
     * 系统属性
     */
    @Builder.Default
    private List<String> properties = new ArrayList<>();

    /**
     * 环境变量
     */
    @Builder.Default
    private List<String> envs = new ArrayList<>();

    /**
     * jvm运行信息
     */
    @Builder.Default
    private String jvmRuntimeInfo = null;
}

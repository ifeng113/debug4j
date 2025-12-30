package com.k4ln.debug4j.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessArgRespVO {

    /**
     * jvm参数
     */
    private List<String> jvmArgs;

    /**
     * 程序参数
     */
    private List<String> programArgs;

    /**
     * 系统属性
     */
    private List<String> properties;

    /**
     * 环境变量
     */
    private List<String> envs;

    /**
     * jvm运行信息
     */
    private String jvmRuntimeInfo;
}

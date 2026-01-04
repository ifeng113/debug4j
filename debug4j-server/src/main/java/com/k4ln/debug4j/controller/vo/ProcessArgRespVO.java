package com.k4ln.debug4j.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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
     * 钩子参数
     */
    private Map<String, List<String>> hookArgs;
}

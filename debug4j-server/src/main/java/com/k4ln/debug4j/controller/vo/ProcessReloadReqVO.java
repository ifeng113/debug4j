package com.k4ln.debug4j.controller.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;


/**
 * commandLineArgs                ← 最高
 * systemProperties               (-Dxxx)
 * systemEnvironment              (OS 环境变量)
 * nacos                          (配置中心)
 * application.yaml(properties)
 * bootstrap.yaml
 * defaultProperties              ← 最低
 *  TODO 打印spring全参数；验证nacos等参数优先级（低于命令行与jvm）、覆盖顺序、获取（配置中心参数）方式、perporty是否会更新spring变量
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessReloadReqVO {

    /**
     * 客户端sessionId
     */
    @NotBlank
    private String clientSessionId;

    /**
     * 移除的jvm参数（Reload模式不支持）
     */
    @Builder.Default
    private List<String> removeJvmArgs = new ArrayList<>();

    /**
     * 新增的jvm参数（Reload模式不支持）
     */
    @Builder.Default
    private List<String> addJvmArgs = new ArrayList<>();

    /**
     * 移除的程序参数
     */
    @Builder.Default
    private List<String> removeProgramArgs = new ArrayList<>();

    /**
     * 新增的程序参数
     */
    @Builder.Default
    private List<String> addProgramArgs = new ArrayList<>();

    /**
     * 移除的系统属性（Restart模式下通过 jvm -Dkey= 实现，【非 -D 参数无法移除】）
     */
    @Builder.Default
    private List<String> removeProperties = new ArrayList<>();

    /**
     * 新增的系统属性（Restart模式下通过 jvm -Dkey=value 实现）
     */
    @Builder.Default
    private List<String> addProperties = new ArrayList<>();

    /**
     * 覆盖的环境变量（Reload模式不支持）
     */
    @Builder.Default
    private List<String> coverEnvs = new ArrayList<>();

}

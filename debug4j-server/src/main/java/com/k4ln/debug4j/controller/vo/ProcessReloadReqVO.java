package com.k4ln.debug4j.controller.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


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
     * 移除的jvm参数
     */
    private List<String> removeJvmArgs;

    /**
     * 新增的jvm参数
     */
    private List<String> addJvmArgs;

    /**
     * 移除的程序参数
     */
    private List<String> removeProgramArgs;

    /**
     * 新增的程序参数
     */
    private List<String> addProgramArgs;

    /**
     * 移除的环境变量
     */
    private List<String> removeEnvs;

    /**
     * 新增的程序参数
     */
    private List<String> addEnvs;

}

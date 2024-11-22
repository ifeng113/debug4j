package com.k4ln.debug4j.controller.vo;

import com.k4ln.debug4j.common.daemon.Debug4jMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManageClientRespVO {

    /**
     * 客户端sessionId
     */
    private String clientSessionId;

    /**
     * 应用名称
     */
    private String applicationName;

    /**
     * 通信客户端主机名
     */
    private String socketClientHost;

    /**
     * 通信客户端主Ip
     */
    private String socketClientIp;

    /**
     * 通信客户端出口IP
     */
    private String socketClientOutletIp;

    /**
     * 唯一ID
     */
    private String uniqueId;

    /**
     * 进程ID
     */
    private Long pid;

    /**
     * 远程调试端口
     */
    private Integer jdwpPort;

    /**
     * 调试模式
     */
    private Debug4jMode debug4jMode;

}

package com.k4ln.debug4j.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(SocketServerProperties.PREFIX)
public class SocketServerProperties {

    public static final String PREFIX = "debug4j";

    /**
     * 通信密钥
     */
    private String key;

    /**
     * socket端口
     */
    private Integer socketPort;

    /**
     * 开发者模式
     */
    private Boolean developer = false;

    /**
     * 最小代理端口
     */
    private Integer minProxyPort = 33000;

    /**
     * 最大代理端口
     */
    private Integer maxProxyPort = 34000;
}

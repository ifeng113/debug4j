package com.k4ln.debug4j.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import com.k4ln.debug4j.common.daemon.Debug4jMode;
import com.k4ln.debug4j.common.protocol.command.message.CommandInfoMessage;
import com.k4ln.debug4j.common.response.exception.BaseException;
import com.k4ln.debug4j.common.response.exception.abort.BusinessAbort;
import com.k4ln.debug4j.config.SocketServerProperties;
import com.k4ln.debug4j.controller.vo.ProxyDetailsRespVO;
import com.k4ln.debug4j.controller.vo.ProxyReqVO;
import com.k4ln.debug4j.controller.vo.ProxyRespVO;
import com.k4ln.debug4j.socket.SocketServer;
import com.k4ln.debug4j.socket.SocketTFProxyServer;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.BindException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ProxyService {

    @Resource
    SocketServer socketServer;

    @Resource
    SocketServerProperties serverProperties;

    @Resource
    HttpServletRequest httpServletRequest;

    /**
     * ProxyReqVO -> SocketTFProxyServer
     */
    private static final Map<String, SocketTFProxyServer> proxyServers = new ConcurrentHashMap<>();

    /**
     * 获取代理服务
     *
     * @param clientSessionId
     * @return
     */
    public List<ProxyDetailsRespVO> getProxyServer(String clientSessionId) {
        List<ProxyDetailsRespVO> proxyDetailsRespVOS = new ArrayList<>();
        for (Map.Entry<String, SocketTFProxyServer> entry : proxyServers.entrySet()) {
            if (entry.getKey().startsWith(clientSessionId)) {
                ProxyDetailsRespVO serverRespVO = BeanUtil.toBean(entry.getValue().getProxyReqVO(), ProxyDetailsRespVO.class);
                serverRespVO.setClientOutletIps(new HashSet<>(entry.getValue().getClientOutletIps().values()));
                proxyDetailsRespVOS.add(serverRespVO);
            }
        }
        return proxyDetailsRespVOS;
    }

    /**
     * 创建代理
     *
     * @param proxyReqVO
     * @return
     */
    public ProxyRespVO proxy(ProxyReqVO proxyReqVO) {
        clientSessionCheck(proxyReqVO);
        String proxyKey = getProxyKey(proxyReqVO);
        if (proxyServers.containsKey(proxyKey)) {
            SocketTFProxyServer proxyServer = proxyServers.get(proxyKey);
            return ProxyRespVO.builder().proxyPort(proxyServer.getTfpServerPort()).build();
        } else {
            try {
                if (proxyReqVO.getServerPort() == null) {
                    proxyReqVO.setServerPort(NetUtil.getUsableLocalPort(serverProperties.getMinProxyPort(), serverProperties.getMaxProxyPort()));
                }
                if (proxyReqVO.getAllowNetworks() == null || proxyReqVO.getAllowNetworks().isEmpty()) {
                    String clientIP = JakartaServletUtil.getClientIP(httpServletRequest);
                    List<String> allowNetworks = new ArrayList<>();
                    allowNetworks.add(clientIP + "/32");
                    proxyReqVO.setAllowNetworks(allowNetworks);
                }
                SocketTFProxyServer proxyServer = new SocketTFProxyServer(proxyReqVO.getServerPort(), proxyReqVO, socketServer);
                proxyServer.start();
                proxyServers.put(proxyKey, proxyServer);
                log.info("proxy server:{} started at port {}", proxyKey, proxyReqVO.getServerPort());
                return ProxyRespVO.builder().proxyPort(proxyServer.getTfpServerPort()).allowNetworks(proxyReqVO.getAllowNetworks()).build();
            } catch (BindException e) {
                throw new BusinessAbort("port already in use");
            } catch (Exception e) {
                throw new BaseException(e.getMessage());
            }
        }
    }

    /**
     * 客户端sessionId检查
     *
     * @param proxyReqVO
     */
    private void clientSessionCheck(ProxyReqVO proxyReqVO) {
        if (!socketServer.getSessionMap().containsKey(proxyReqVO.getClientSessionId())) {
            throw new BusinessAbort("not found remote client");
        } else {
            CommandInfoMessage commandInfoMessage = socketServer.getInfoMessageMap().get(proxyReqVO.getClientSessionId());
            if (commandInfoMessage == null || commandInfoMessage.getDebug4jMode().equals(Debug4jMode.thread)) {
                throw new BusinessAbort("not found proxy client");
            }
        }
    }

    /**
     * 获取代理Key
     *
     * @param proxyReqVO
     * @return
     */
    public static String getProxyKey(ProxyReqVO proxyReqVO) {
        return proxyReqVO.getClientSessionId() + "#" + proxyReqVO.getRemoteHost() + "#" + proxyReqVO.getRemotePort();
    }

    /**
     * 删除代理服务
     *
     * @param clientSessionId
     */
    public static void removeProxyServer(String clientSessionId) {
        List<String> removeKeys = new ArrayList<>();
        proxyServers.keySet().forEach(e -> {
            if (e.startsWith(clientSessionId)) {
                removeKeys.add(e);
            }
        });
        removeKeys.forEach(proxyServers::remove);
    }


}

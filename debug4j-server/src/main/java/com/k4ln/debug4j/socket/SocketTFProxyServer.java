package com.k4ln.debug4j.socket;

import cn.hutool.core.net.NetUtil;
import com.k4ln.debug4j.common.protocol.command.CommandTypeEnum;
import com.k4ln.debug4j.common.protocol.command.message.CommandProxyMessage;
import com.k4ln.debug4j.common.protocol.socket.ProtocolTypeEnum;
import com.k4ln.debug4j.common.protocol.socket.TFProtocolDecoder;
import com.k4ln.debug4j.controller.vo.ProxyReqVO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.smartboot.socket.StateMachineEnum;
import org.smartboot.socket.extension.processor.AbstractMessageProcessor;
import org.smartboot.socket.transport.AioQuickServer;
import org.smartboot.socket.transport.AioSession;
import org.smartboot.socket.transport.WriteBuffer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SocketTFProxyServer {

    @Getter
    Integer tfpServerPort;

    @Getter
    ProxyReqVO proxyReqVO;

    @Getter
    SocketServer socketServer;

    @Getter
    AioQuickServer server;

    public SocketTFProxyServer(Integer tfpServerPort, ProxyReqVO proxyReqVO, SocketServer socketServer) {
        this.tfpServerPort = tfpServerPort;
        this.proxyReqVO = proxyReqVO;
        this.socketServer = socketServer;
    }

    @Getter
    AbstractMessageProcessor<byte[]> processor;

    /**
     * sourceClientId -> sourceClient
     */
    @Getter
    private static final Map<String, AioSession> sessionMap = new ConcurrentHashMap<>();

    /**
     * sessionId -> clientOutletIps
     */
    @Getter
    private Map<String, String> clientOutletIps = new ConcurrentHashMap<>();

    public void shutdown() {
        if (server != null) {
            server.shutdown();
        }
    }

    public void start() throws Exception {

        processor = new AbstractMessageProcessor<>() {

            // Source -> TFPServer
            @Override
            public void process0(AioSession session, byte[] body) {
                //  TFPServer -> socketServer
                socketServer.sendMessage(proxyReqVO.getClientSessionId(), getSessionClientId(session), ProtocolTypeEnum.PROXY, body);
            }

            private static Integer getSessionClientId(AioSession session) {
                return Integer.parseInt(session.getSessionID().replace("aioSession-", ""));
            }

            @Override
            public void stateEvent0(AioSession session, StateMachineEnum stateMachineEnum, Throwable throwable) {
                if (stateMachineEnum.equals(StateMachineEnum.NEW_SESSION)) {
                    sessionMap.put(session.getSessionID(), session);
                    log.info("TFProxy server clientId:{} connected", getSessionClientId(session));
                    try {
                        if (allowNetworks(session.getRemoteAddress().getAddress().getHostAddress())) {
                            socketServer.sendMessage(proxyReqVO.getClientSessionId(), getSessionClientId(session), ProtocolTypeEnum.COMMAND,
                                    CommandProxyMessage.buildCommandProxyMessage(CommandTypeEnum.PROXY_OPEN, proxyReqVO.getRemoteHost(),
                                            proxyReqVO.getRemotePort()));
                            clientOutletIps.put(session.getSessionID(), session.getRemoteAddress().getAddress().getHostAddress());
                            return;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    // 关闭客户端
                    closeClient(getSessionClientId(session));
                } else if (stateMachineEnum.equals(StateMachineEnum.SESSION_CLOSED)) {
                    sessionMap.remove(session.getSessionID());
                    socketServer.getClientIdSocketMap().remove(getSessionClientId(session));
                    log.info("TFProxy server clientId:{} disConnected", getSessionClientId(session));
                    socketServer.sendMessage(proxyReqVO.getClientSessionId(), getSessionClientId(session), ProtocolTypeEnum.COMMAND,
                            CommandProxyMessage.buildCommandProxyMessage(CommandTypeEnum.PROXY_CLOSE, proxyReqVO.getRemoteHost(),
                                    proxyReqVO.getRemotePort()));
                    clientOutletIps.remove(session.getSessionID());
                } else if (throwable != null) {
                    throwable.printStackTrace();
                }
            }

            private boolean allowNetworks(String hostAddress) {
                List<String> allowNetworks = proxyReqVO.getAllowNetworks();
                for (String allowNetwork : allowNetworks) {
                    if (NetUtil.isInRange(hostAddress, allowNetwork)) {
                        return true;
                    }
                }
                return false;
            }
        };

        server = new AioQuickServer(tfpServerPort, new TFProtocolDecoder(), processor);
        server.start();

        log.info("TFProxy server started at port {}", tfpServerPort);
    }

    public static void callbackMessage(Integer clientId, byte[] body) {
        try {
            AioSession session = sessionMap.get(parseSessionId(clientId));
            WriteBuffer writeBuffer = session.writeBuffer();
            writeBuffer.writeAndFlush(body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void closeClient(Integer clientId) {
        try {
            AioSession session = sessionMap.get(parseSessionId(clientId));
            if (session != null) {
                session.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String parseSessionId(Integer clientId) {
        return "aioSession-" + clientId;
    }

}

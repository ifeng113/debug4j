package com.k4ln.debug4j.socket;

import cn.hutool.core.util.ArrayUtil;
import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.common.protocol.command.Command;
import com.k4ln.debug4j.common.protocol.command.CommandTypeEnum;
import com.k4ln.debug4j.common.protocol.command.message.*;
import com.k4ln.debug4j.common.protocol.socket.ProtocolTypeEnum;
import com.k4ln.debug4j.common.protocol.socket.SocketProtocol;
import com.k4ln.debug4j.common.protocol.socket.SocketProtocolDecoder;
import com.k4ln.debug4j.common.utils.SocketProtocolUtil;
import com.k4ln.debug4j.config.SocketServerProperties;
import com.k4ln.debug4j.service.AttachHub;
import com.k4ln.debug4j.service.ProxyService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.smartboot.socket.StateMachineEnum;
import org.smartboot.socket.extension.plugins.HeartPlugin;
import org.smartboot.socket.extension.processor.AbstractMessageProcessor;
import org.smartboot.socket.transport.AioQuickServer;
import org.smartboot.socket.transport.AioSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.k4ln.debug4j.socket.SocketTFProxyServer.callbackMessage;
import static com.k4ln.debug4j.socket.SocketTFProxyServer.closeClient;

/**
 * socket实现HTTP代理和HTTPS代理【透明代理】：https://www.jb51.net/program/3204924ux.htm
 * 代理分三种：
 * TCP全流量（如OpenVPN，需要修改本地路由表、创建虚拟网卡等）
 * TCP指定端口（SocketTFProxyServer + SocketTFProxyClient | goproxy），指定端口代理（指定入端口与出端口，支持多用户单端口代理，简称正向代理）
 * TCP穿透代理（【本程序】 | frp）
 * HTTP全流量（Clash[透明代理]、笔记[http+https]：20220809/0825-pj/https-proxy + proxy-dm）
 * - http 劫持代理（可篡改内容）
 * - https
 * - 劫持代理（可篡改内容，需要安装证书）
 * - 透明代理（流量透明转发，获取到的是tcp流量包，篡改内容较为复杂，需对数据包处理）
 */
@Slf4j
public class SocketServer {

    final SocketServerProperties serverProperties;

    final AttachHub attachHub;

    AioQuickServer server;

    AbstractMessageProcessor<SocketProtocol> processor;

    /**
     * socketClient
     */
    @Getter
    final Map<String, AioSession> sessionMap = new ConcurrentHashMap<>();

    /**
     * socketClient -> heartTimes
     */
    final Map<String, Integer> authSessionMap = new ConcurrentHashMap<>();

    /**
     * socketClient -> packagingData
     */
    final Map<String, byte[]> sessionPackaging = new ConcurrentHashMap<>();

    /**
     * socketClient -> CommandInfoMessage
     */
    @Getter
    final Map<String, CommandInfoMessage> infoMessageMap = new ConcurrentHashMap<>();

    /**
     * clientId -> socketClient
     */
    @Getter
    final Map<Integer, String> clientIdSocketMap = new ConcurrentHashMap<>();

    public SocketServer(SocketServerProperties serverProperties, AttachHub attachHub) {
        this.serverProperties = serverProperties;
        this.attachHub = attachHub;
    }

    public void start() throws Exception {

        processor = new AbstractMessageProcessor<>() {

            @Override
            public void process0(AioSession session, SocketProtocol protocol) {
                // 鉴权
                if (!protocol.getProtocolType().equals(ProtocolTypeEnum.AUTH) && !checkAuth(session.getSessionID())) {
                    return;
                }
                if (protocol.getSubcontract()) {
                    String sessionPackagingKey = getSessionPackagingKey(session, protocol);
                    sessionPackaging.put(sessionPackagingKey, ArrayUtil.addAll(sessionPackaging.get(sessionPackagingKey), protocol.getBody()));
                    if (protocol.getSubcontractCount().equals(protocol.getSubcontractIndex())) {
                        messageHandler(session, protocol, sessionPackaging.get(sessionPackagingKey));
                        sessionPackaging.remove(sessionPackagingKey);
                    }
                } else {
                    messageHandler(session, protocol, protocol.getBody());
                }
            }

            private Boolean checkAuth(String sessionID) {
                Integer heartTimes = authSessionMap.get(sessionID);
                return heartTimes != null && heartTimes == -1;
            }

            private void messageHandler(AioSession session, SocketProtocol protocol, byte[] data) {
                switch (protocol.getProtocolType()) {
                    case AUTH -> {
                        if (serverProperties.getKey().equals(new String(data))) {
                            authSessionMap.put(session.getSessionID(), -1);
                            sendMessage(session.getSessionID(), 0, ProtocolTypeEnum.COMMAND,
                                    CommandLogMessage.buildCommandLogMessage(session.getSessionID() + " auth successful"));
                        } else {
                            session.close();
                        }
                    }
                    case COMMAND -> {
                        Command command = JSON.parseObject(new String(data), Command.class);
                        if (command.getCommand().equals(CommandTypeEnum.PROXY_CLOSE)) {
                            closeClient(protocol.getClientId());
                        } else if (command.getCommand().equals(CommandTypeEnum.INFO)) {
                            String infoString = JSON.toJSONString(command.getData());
                            log.warn("sessionId:{} with info:{}", session.getSessionID(), infoString);
                            CommandInfoMessage infoMessage = JSON.parseObject(infoString, CommandInfoMessage.class);
                            try {
                                infoMessage.setSocketClientOutletIp(session.getRemoteAddress().getAddress().getHostAddress());
                            } catch (Exception e) {
                                log.warn("sessionId:{} fail to get outlet ip", session.getSessionID());
                            }
                            infoMessage.setClientSessionId(session.getSessionID());
                            infoMessageMap.put(session.getSessionID(), infoMessage);
                        } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_RESP_CLASS_ALL)
                                || command.getCommand().equals(CommandTypeEnum.ATTACH_RESP_CLASS_SOURCE)
                                || command.getCommand().equals(CommandTypeEnum.ATTACH_RESP_CLASS_SOURCE_LINE)) {
                            String jsonString = JSON.toJSONString(command.getData());
                            CommandAttachRespMessage attachResp = JSON.parseObject(jsonString, CommandAttachRespMessage.class);
                            attachHub.pushResult(attachResp.getReqId(), jsonString);
                        } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_RESP_TASK)) {
                            String jsonString = JSON.toJSONString(command.getData());
                            CommandTaskRespMessage taskResp = JSON.parseObject(jsonString, CommandTaskRespMessage.class);
                            attachHub.pushResult(taskResp.getReqId(), jsonString);
                        } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_RESP_TASK_DETAILS)) {
                            String jsonString = JSON.toJSONString(command.getData());
                            CommandTaskTailRespMessage taskResp = JSON.parseObject(jsonString, CommandTaskTailRespMessage.class);
                            attachHub.pushSseEmitter(session.getSessionID() + "@" + taskResp.getFilePath(), taskResp.getLine());
                        } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_RESP_PROCESS_ARG_DETAILS)) {
                            String jsonString = JSON.toJSONString(command.getData());
                            CommandProcessRespMessage processResp = JSON.parseObject(jsonString, CommandProcessRespMessage.class);
                            attachHub.pushResult(processResp.getReqId(), jsonString);
                        }
                    }
                    case PROXY -> callbackMessage(protocol.getClientId(), data);
                }
            }

            private static String getSessionPackagingKey(AioSession session, SocketProtocol protocol) {
                return session.getSessionID() + "-" + protocol.getProtocolType().name() + "-" + protocol.getClientId();
            }

            @Override
            public void stateEvent0(AioSession session, StateMachineEnum stateMachineEnum, Throwable throwable) {
                if (stateMachineEnum.equals(StateMachineEnum.NEW_SESSION)) {
                    sessionMap.put(session.getSessionID(), session);
                    authSessionMap.put(session.getSessionID(), 0);
                    log.info("socket server session:{} connected", session.getSessionID());
                } else if (stateMachineEnum.equals(StateMachineEnum.SESSION_CLOSED)) {
                    sessionMap.remove(session.getSessionID());
                    authSessionMap.remove(session.getSessionID());
                    infoMessageMap.remove(session.getSessionID());
                    clearSocketClientClientIds(session);
                    log.info("socket server session:{} disConnected", session.getSessionID());
                } else if (throwable != null) {
                    throwable.printStackTrace();
                }
            }

            private void clearSocketClientClientIds(AioSession session) {
                List<Integer> clientIds = new ArrayList<>();
                for (Map.Entry<Integer, String> entry : clientIdSocketMap.entrySet()) {
                    if (entry.getValue().equals(session.getSessionID())) {
                        clientIds.add(entry.getKey());
                    }
                }
                clientIds.forEach(SocketTFProxyServer::closeClient);
                ProxyService.removeProxyServer(session.getSessionID());
            }
        };

        processor.addPlugin(new HeartPlugin<>(5, TimeUnit.SECONDS) {

            @Override
            public void sendHeartRequest(AioSession session) {
                Integer heartTimes = authSessionMap.get(session.getSessionID());
                if (heartTimes == null) {
                    session.close();
                    return;
                } else {
                    if (heartTimes > 3) {
                        session.close();
                        return;
                    } else if (heartTimes != -1) {
                        authSessionMap.put(session.getSessionID(), heartTimes + 1);
                    }
                }
                sendMessage(session.getSessionID(), 0, ProtocolTypeEnum.HEART, null);
            }

            @Override
            public boolean isHeartMessage(AioSession session, SocketProtocol socketProtocol) {
                return socketProtocol.getProtocolType().equals(ProtocolTypeEnum.HEART);
            }
        });

        server = new AioQuickServer(serverProperties.getSocketPort(), new SocketProtocolDecoder(), processor);
        server.setReadBufferSize(SocketProtocolUtil.READ_BUFFER_SIZE);
        server.start();

        log.info("socket server started at pid:{} port {}", ProcessHandle.current().pid(), serverProperties.getSocketPort());
    }


    /**
     * 根据sessionId发送消息
     *
     * @param sessionId
     * @param clientId
     * @param protocolType
     * @param body
     */
    public void sendMessage(String sessionId, Integer clientId, ProtocolTypeEnum protocolType, byte[] body) {
        AioSession session = sessionMap.get(sessionId);
        if (session != null && !session.isInvalid()) {
            SocketProtocolUtil.sendMessage(session, clientId, protocolType, body);
            if (clientId != 0) {
                clientIdSocketMap.put(clientId, sessionId);
            }
        } else {
            if (clientId != 0) {
                closeClient(clientId);
            }
        }
    }

}

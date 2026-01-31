package com.k4ln.debug4j.core.client;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.HashUtil;
import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.common.daemon.Debug4jMode;
import com.k4ln.debug4j.common.protocol.command.Command;
import com.k4ln.debug4j.common.protocol.command.CommandTypeEnum;
import com.k4ln.debug4j.common.protocol.command.message.*;
import com.k4ln.debug4j.common.protocol.command.message.enums.SourceCodeTypeEnum;
import com.k4ln.debug4j.common.protocol.socket.ProtocolTypeEnum;
import com.k4ln.debug4j.common.protocol.socket.SocketProtocol;
import com.k4ln.debug4j.common.protocol.socket.SocketProtocolDecoder;
import com.k4ln.debug4j.common.utils.SocketProtocolUtil;
import com.k4ln.debug4j.core.Debugger;
import com.k4ln.debug4j.core.attach.Debug4jAttachOperator;
import com.k4ln.debug4j.core.attach.Debug4jWatcher;
import com.k4ln.debug4j.core.attach.dto.*;
import com.k4ln.debug4j.core.attach.jvm.Debug4jProcessOperator;
import com.k4ln.debug4j.core.proxy.SocketTFProxyClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.smartboot.socket.MessageProcessor;
import org.smartboot.socket.StateMachineEnum;
import org.smartboot.socket.extension.processor.AbstractMessageProcessor;
import org.smartboot.socket.transport.AioQuickClient;
import org.smartboot.socket.transport.AioSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.k4ln.debug4j.common.utils.SocketProtocolUtil.*;

@Slf4j
public class SocketClient {

    private final String key;

    private final String host;

    private final Integer port;

    private static CommandInfoMessage commandInfoMessage;

    public SocketClient(String key, String host, Integer port, CommandInfoMessage commandInfoMessage) {
        this.key = key;
        this.host = host;
        this.port = port;
        SocketClient.commandInfoMessage = commandInfoMessage;
    }

    @Getter
    static AioSession session;

    /**
     * realClientId -> targetClient
     */
    static final Map<Integer, SocketTFProxyClient> targetClients = new ConcurrentHashMap<>();

    /**
     * socketServer -> packagingData
     */
    final Map<String, byte[]> sessionPackaging = new ConcurrentHashMap<>();

    /**
     * socketServer -> FileInfo
     */
    @Getter
    final Map<String, FileInfo> sessionRandomAccessFile = new ConcurrentHashMap<>();

    /**
     * 是否还存活
     *
     * @return
     */
    public boolean isAlive() {
        return session != null && !session.isInvalid();
    }

    public void shutdown() {
        if (session != null) {
            session.close();
        }
    }

    /**
     * 开启socket客户端
     *
     * @throws IOException
     */
    public void start() throws IOException {

        targetClients.clear();

        MessageProcessor<SocketProtocol> processor = new AbstractMessageProcessor<>() {

            @Override
            public void process0(AioSession session, SocketProtocol socketProtocol) {
                switch (socketProtocol.getProtocolType()) {
                    case COMMAND -> {
                        if (socketProtocol.getSubcontract()) {
                            String sessionPackagingKey = getSessionPackagingKey(session, socketProtocol);
                            sessionPackaging.put(sessionPackagingKey, ArrayUtil.addAll(sessionPackaging.get(sessionPackagingKey), socketProtocol.getBody()));
                            if (socketProtocol.getSubcontractCount().equals(socketProtocol.getSubcontractIndex())) {
                                handCommand(socketProtocol, sessionPackaging.get(sessionPackagingKey));
                                sessionPackaging.remove(sessionPackagingKey);
                            }
                        } else {
                            handCommand(socketProtocol, socketProtocol.getBody());
                        }
                    }
                    case PROXY -> {
                        Integer clientId = socketProtocol.getClientId();
                        if (targetClients.containsKey(clientId)) {
                            targetClients.get(clientId).sendMessage(socketProtocol.getBody());
                        } else {
                            log.warn("socketClient proxy no clientId:{}", clientId);
                        }
                    }
                    case FILE -> handleUploadFile(socketProtocol);
                }
            }

            private void handleUploadFile(SocketProtocol socketProtocol) {
                // 上传文件：新建临时文件，流写入完毕后，再将临时文件移至上传目录（覆盖老文件，删除临时文件）
                String clientId = String.valueOf(socketProtocol.getClientId());
                FileInfo fileInfo = sessionRandomAccessFile.get(clientId);
                try {
                    if (socketProtocol.getSubcontract()) {
                        int maxBodyLength = READ_BUFFER_SIZE - BUFFER_LENGTH - BUFFER_HEADER;
                        fileInfo.getRandomAccessFile().seek((long) (socketProtocol.getSubcontractIndex() - 1) * maxBodyLength);
                        fileInfo.getRandomAccessFile().write(socketProtocol.getBody());
                        fileInfo.setLastModified(System.currentTimeMillis());
                        if (Objects.equals(socketProtocol.getSubcontractCount(), socketProtocol.getSubcontractIndex())) {
                            fileInfo.getRandomAccessFile().close();
                            sessionRandomAccessFile.remove(clientId);
                            moveReplaceRename(Path.of(fileInfo.getTemporaryFilename()), Paths.get(fileInfo.getFileDir()), fileInfo.getFilename());
                        }
                    } else {
                        fileInfo.getRandomAccessFile().seek(0);
                        fileInfo.getRandomAccessFile().write(socketProtocol.getBody());
                        fileInfo.getRandomAccessFile().close();
                        sessionRandomAccessFile.remove(clientId);
                        moveReplaceRename(Path.of(fileInfo.getTemporaryFilename()), Paths.get(fileInfo.getFileDir()), fileInfo.getFilename());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (fileInfo != null && fileInfo.getRandomAccessFile() != null) {
                        try {
                            fileInfo.getRandomAccessFile().close();
                        } catch (IOException e2) {
                            e2.printStackTrace();
                        }
                    }
                    sessionRandomAccessFile.remove(clientId);
                }
            }

            private void moveReplaceRename(Path sourceFile,
                                           Path targetDir,
                                           String deleteFileName) {
                try {
                    Files.createDirectories(targetDir);
                    Path deletePath = targetDir.resolve(deleteFileName);
                    Files.deleteIfExists(deletePath);
                    Path movedPath = Files.move(sourceFile, targetDir.resolve(sourceFile.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    Path finalPath = targetDir.resolve(deleteFileName);
                    Files.move(movedPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            private static String getSessionPackagingKey(AioSession session, SocketProtocol protocol) {
                return session.getSessionID() + "-" + protocol.getProtocolType().name() + "-" + protocol.getClientId();
            }

            private static void handCommand(SocketProtocol socketProtocol, byte[] data) {
                Command command = JSON.parseObject(new String(data), Command.class);
                if (command.getCommand().equals(CommandTypeEnum.LOG)) {
                    log.info(JSON.parseObject(JSON.toJSONString(command.getData()), CommandLogMessage.class).getContent());
                } else if (commandInfoMessage.getDebug4jMode().equals(Debug4jMode.process)) {
                    if (command.getCommand().equals(CommandTypeEnum.PROXY_OPEN)) {
                        CommandProxyMessage proxyMessage = JSON.parseObject(JSON.toJSONString(command.getData()), CommandProxyMessage.class);
                        try {
                            SocketTFProxyClient tfProxyClient = new SocketTFProxyClient();
                            targetClients.put(socketProtocol.getClientId(), tfProxyClient);
                            tfProxyClient.start(socketProtocol.getClientId(), proxyMessage.getHost(), proxyMessage.getPort());
                            log.info("socketClient proxy started successfully clientId:{}", socketProtocol.getClientId());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (command.getCommand().equals(CommandTypeEnum.PROXY_CLOSE)) {
                        if (targetClients.containsKey(socketProtocol.getClientId())) {
                            SocketTFProxyClient tfProxyClient = targetClients.get(socketProtocol.getClientId());
                            if (tfProxyClient != null && tfProxyClient.getSession() != null && !tfProxyClient.getSession().isInvalid()) {
                                tfProxyClient.getSession().close();
                            }
                            targetClients.remove(socketProtocol.getClientId());
                            log.info("socketClient proxy closed successfully clientId:{}", socketProtocol.getClientId());
                        } else {
                            log.warn("socketClient command no clientId:{}", socketProtocol.getClientId());
                        }
                    }
                } else if (commandInfoMessage.getDebug4jMode().equals(Debug4jMode.thread)) {
                    if (command.getCommand().equals(CommandTypeEnum.ATTACH_REQ_CLASS_ALL)) {
                        CommandAttachReqMessage attachReq = JSON.parseObject(JSON.toJSONString(command.getData()), CommandAttachReqMessage.class);
                        List<String> allClass = Debug4jAttachOperator.getAllClass(Debugger.getInstrumentation(), commandInfoMessage.getPackageName(), attachReq.getPackageName());
                        SocketProtocolUtil.sendMessage(session, HashUtil.fnvHash(attachReq.getReqId()), ProtocolTypeEnum.COMMAND, CommandAttachRespMessage.buildClassAllRespMessage(attachReq.getReqId(), allClass));
                    } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_REQ_CLASS_SOURCE)) {
                        CommandAttachReqMessage attachReq = JSON.parseObject(JSON.toJSONString(command.getData()), CommandAttachReqMessage.class);
                        SourceCodeInfo sourceCodeInfo = Debug4jAttachOperator.getClassSource(Debugger.getInstrumentation(), attachReq.getClassName(), attachReq.getSourceCodeType());
                        SocketProtocolUtil.sendMessage(session, HashUtil.fnvHash(attachReq.getReqId()), ProtocolTypeEnum.COMMAND, CommandAttachRespMessage.buildClassSourceRespMessage(attachReq.getReqId(), sourceCodeInfo.getClassSource(), sourceCodeInfo.getClassMethods(), sourceCodeInfo.getByteCodeType(), true));
                    } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_REQ_TASK)) {
                        CommandTaskReqMessage taskReq = JSON.parseObject(JSON.toJSONString(command.getData()), CommandTaskReqMessage.class);
                        List<CommandTaskReqMessage> reqMessages = Debug4jWatcher.getTask();
                        SocketProtocolUtil.sendMessage(session, HashUtil.fnvHash(taskReq.getReqId()), ProtocolTypeEnum.COMMAND, CommandTaskRespMessage.buildTaskRespMessage(taskReq.getReqId(), reqMessages));
                    } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_REQ_TASK_OPEN)) {
                        CommandTaskReqMessage taskReq = JSON.parseObject(JSON.toJSONString(command.getData()), CommandTaskReqMessage.class);
                        List<CommandTaskReqMessage> reqMessages = Debug4jWatcher.openTask(taskReq);
                        SocketProtocolUtil.sendMessage(session, HashUtil.fnvHash(taskReq.getReqId()), ProtocolTypeEnum.COMMAND, CommandTaskRespMessage.buildTaskRespMessage(taskReq.getReqId(), reqMessages));
                    } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_REQ_TASK_CLOSE)) {
                        CommandTaskReqMessage taskReq = JSON.parseObject(JSON.toJSONString(command.getData()), CommandTaskReqMessage.class);
                        List<CommandTaskReqMessage> reqMessages = Debug4jWatcher.closeTask(taskReq);
                        SocketProtocolUtil.sendMessage(session, HashUtil.fnvHash(taskReq.getReqId()), ProtocolTypeEnum.COMMAND, CommandTaskRespMessage.buildTaskRespMessage(taskReq.getReqId(), reqMessages));
                    } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_REQ_CLASS_RELOAD_JAVA)) {
                        CommandAttachReqMessage attachReq = JSON.parseObject(JSON.toJSONString(command.getData()), CommandAttachReqMessage.class);
                        boolean reloadStatus = Debug4jAttachOperator.sourceReloadWithInner(Debugger.getInstrumentation(), attachReq.getClassName(), attachReq.getSourceCode());
                        SourceCodeInfo sourceCodeInfo = Debug4jAttachOperator.getClassSource(Debugger.getInstrumentation(), attachReq.getClassName(), SourceCodeTypeEnum.attachClassByteCode);
                        SocketProtocolUtil.sendMessage(session, HashUtil.fnvHash(attachReq.getReqId()), ProtocolTypeEnum.COMMAND, CommandAttachRespMessage.buildClassSourceRespMessage(attachReq.getReqId(), sourceCodeInfo.getClassSource(), sourceCodeInfo.getClassMethods(), sourceCodeInfo.getByteCodeType(), reloadStatus));
                    } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_REQ_CLASS_RELOAD)) {
                        CommandAttachReqMessage attachReq = JSON.parseObject(JSON.toJSONString(command.getData()), CommandAttachReqMessage.class);
                        boolean reloadStatus = Debug4jAttachOperator.classReload(Debugger.getInstrumentation(), attachReq.getClassName(), attachReq.getByteCode());
                        SourceCodeInfo sourceCodeInfo = Debug4jAttachOperator.getClassSource(Debugger.getInstrumentation(), attachReq.getClassName(), SourceCodeTypeEnum.attachClassByteCode);
                        SocketProtocolUtil.sendMessage(session, HashUtil.fnvHash(attachReq.getReqId()), ProtocolTypeEnum.COMMAND, CommandAttachRespMessage.buildClassSourceRespMessage(attachReq.getReqId(), sourceCodeInfo.getClassSource(), sourceCodeInfo.getClassMethods(), sourceCodeInfo.getByteCodeType(), reloadStatus));
                    } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_REQ_CLASS_RESTORE)) {
                        CommandAttachReqMessage attachReq = JSON.parseObject(JSON.toJSONString(command.getData()), CommandAttachReqMessage.class);
                        Debug4jAttachOperator.classRestore(Debugger.getInstrumentation(), attachReq.getClassName());
                        SourceCodeInfo sourceCodeInfo = Debug4jAttachOperator.getClassSource(Debugger.getInstrumentation(), attachReq.getClassName(), SourceCodeTypeEnum.attachClassByteCode);
                        SocketProtocolUtil.sendMessage(session, HashUtil.fnvHash(attachReq.getReqId()), ProtocolTypeEnum.COMMAND, CommandAttachRespMessage.buildClassSourceRespMessage(attachReq.getReqId(), sourceCodeInfo.getClassSource(), sourceCodeInfo.getClassMethods(), sourceCodeInfo.getByteCodeType(), true));
                    } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_REQ_CLASS_SOURCE_LINE)) {
                        CommandAttachReqMessage attachReq = JSON.parseObject(JSON.toJSONString(command.getData()), CommandAttachReqMessage.class);
                        MethodLineInfo methodLineInfo = Debug4jAttachOperator.methodLine(Debugger.getInstrumentation(), attachReq.getClassName(), attachReq.getLineMethodName());
                        SocketProtocolUtil.sendMessage(session, HashUtil.fnvHash(attachReq.getReqId()), ProtocolTypeEnum.COMMAND, CommandAttachRespMessage.buildClassSourceLineRespMessage(attachReq.getReqId(), methodLineInfo.getSourceCode(), methodLineInfo.getClassMethods(), methodLineInfo.getLineNumbers()));
                    } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_REQ_CLASS_RELOAD_JAVA_LINE)) {
                        CommandAttachReqMessage attachReq = JSON.parseObject(JSON.toJSONString(command.getData()), CommandAttachReqMessage.class);
                        Debug4jAttachOperator.patchLine(Debugger.getInstrumentation(), attachReq.getClassName(), attachReq.getLineMethodName(), attachReq.getSourceCode(), attachReq.getLingNumber());
                        MethodLineInfo methodLineInfo = Debug4jAttachOperator.methodLine(Debugger.getInstrumentation(), attachReq.getClassName(), attachReq.getLineMethodName());
                        SocketProtocolUtil.sendMessage(session, HashUtil.fnvHash(attachReq.getReqId()), ProtocolTypeEnum.COMMAND, CommandAttachRespMessage.buildClassSourceLineRespMessage(attachReq.getReqId(), methodLineInfo.getSourceCode(), methodLineInfo.getClassMethods(), methodLineInfo.getLineNumbers()));
                    } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_REQ_PROCESS_ARG)) {
                        CommandProcessReqMessage processReq = JSON.parseObject(JSON.toJSONString(command.getData()), CommandProcessReqMessage.class);
                        ProcessArgsInfo processArgsInfo = Debug4jProcessOperator.getProcessArgsInfo();
                        SocketProtocolUtil.sendMessage(session, HashUtil.fnvHash(processReq.getReqId()), ProtocolTypeEnum.COMMAND, CommandProcessRespMessage.buildCommandProcessRespMessage(processReq.getReqId(), processArgsInfo.getJvmArgs(), processArgsInfo.getProgramArgs(), processArgsInfo.getProperties(), processArgsInfo.getEnvs(), processArgsInfo.getHookArgs()));
                    } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_REQ_PROCESS_RELOAD)) {
                        CommandProcessReqMessage processReq = JSON.parseObject(JSON.toJSONString(command.getData()), CommandProcessReqMessage.class);
                        ProcessArgsInfo processArgsInfo = Debug4jProcessOperator.reload(processReq);
                        SocketProtocolUtil.sendMessage(session, HashUtil.fnvHash(processReq.getReqId()), ProtocolTypeEnum.COMMAND, CommandProcessRespMessage.buildCommandProcessRespMessage(processReq.getReqId(), processArgsInfo.getJvmArgs(), processArgsInfo.getProgramArgs(), processArgsInfo.getProperties(), processArgsInfo.getEnvs(), processArgsInfo.getHookArgs()));
                    } else if (command.getCommand().equals(CommandTypeEnum.ATTACH_REQ_PROCESS_ADJUSTMENT)) {
                        CommandProcessAdjustmentReqMessage adjustmentReqMessage = JSON.parseObject(JSON.toJSONString(command.getData()), CommandProcessAdjustmentReqMessage.class);
                        ProcessAdjustmentInfo processAdjustmentInfo = Debug4jProcessOperator.adjustment(adjustmentReqMessage);
                        SocketProtocolUtil.sendMessage(session, HashUtil.fnvHash(adjustmentReqMessage.getReqId()), ProtocolTypeEnum.COMMAND, CommandProcessAdjustmentRespMessage.buildCommandProcessAdjustmentRespMessage(adjustmentReqMessage.getReqId(), processAdjustmentInfo.getAdjustmentResult(), processAdjustmentInfo.getAdjustmentExtendResult()));
                    }
                }
            }

            @Override
            public void stateEvent0(AioSession session, StateMachineEnum stateMachineEnum, Throwable throwable) {
                if (stateMachineEnum.equals(StateMachineEnum.NEW_SESSION)) {
                    SocketProtocolUtil.sendMessage(session, 0, ProtocolTypeEnum.AUTH, key.getBytes());
                    SocketProtocolUtil.sendMessage(session, 0, ProtocolTypeEnum.COMMAND,
                            JSON.toJSONString(Command.builder().command(CommandTypeEnum.INFO).data(commandInfoMessage).build()).getBytes());
                    log.info("socket client connected");
                } else if (stateMachineEnum.equals(StateMachineEnum.SESSION_CLOSED)) {
                    log.info("socket client disConnected");
                    Debug4jWatcher.clear();
                } else if (throwable != null) {
                    throwable.printStackTrace();
                }
            }
        };
        AioQuickClient client = new AioQuickClient(host, port, new SocketProtocolDecoder(), processor);
        client.setReadBufferSize(SocketProtocolUtil.READ_BUFFER_SIZE);
        session = client.start();
    }

    public static void callbackMessage(Integer clientId, byte[] body) {
        SocketProtocolUtil.sendMessage(session, clientId, ProtocolTypeEnum.PROXY, body);
    }

    public static void callbackMessage(Integer clientId, ProtocolTypeEnum protocolType, byte[] body) {
        SocketProtocolUtil.sendMessage(session, clientId, protocolType, body);
    }

    public static void callbackFileMessage(Integer clientId, byte[] body, Integer subcontractCount, Integer subcontractIndex) {
        SocketProtocolUtil.sendFileMessage(session, clientId, body, subcontractCount, subcontractIndex);
    }

    public static void clientClose(Integer clientId) {
        targetClients.remove(clientId);
        SocketProtocolUtil.sendMessage(session, clientId, ProtocolTypeEnum.COMMAND,
                CommandProxyMessage.buildCommandProxyMessage(CommandTypeEnum.PROXY_CLOSE, null, null));
        log.info("socketClient proxy target closed clientId:{}", clientId);
    }
}

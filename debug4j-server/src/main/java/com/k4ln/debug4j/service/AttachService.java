package com.k4ln.debug4j.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.codec.Base64Encoder;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.HashUtil;
import cn.hutool.core.util.StrUtil;
import com.k4ln.debug4j.common.daemon.Debug4jMode;
import com.k4ln.debug4j.common.protocol.command.message.*;
import com.k4ln.debug4j.common.protocol.socket.ProtocolTypeEnum;
import com.k4ln.debug4j.common.response.exception.abort.BusinessAbort;
import com.k4ln.debug4j.common.utils.FileUtils;
import com.k4ln.debug4j.controller.vo.*;
import com.k4ln.debug4j.service.aop.CodeLock;
import com.k4ln.debug4j.socket.SocketServer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AttachService {

    @Resource
    AttachHub attachHub;

    @Resource
    SocketServer socketServer;

    /**
     * 客户端session检查
     *
     * @param clientSessionId
     */
    private void clientSessionCheck(String clientSessionId) {
        if (!socketServer.getSessionMap().containsKey(clientSessionId)) {
            throw new BusinessAbort("not found remote client");
        } else {
            CommandInfoMessage commandInfoMessage = socketServer.getInfoMessageMap().get(clientSessionId);
            if (commandInfoMessage == null || commandInfoMessage.getDebug4jMode().equals(Debug4jMode.process)) {
                throw new BusinessAbort("not found attach client");
            }
        }
    }

    /**
     * ping客户端
     */
    public void ping(AttachPingReqVO attachPingReqVO) {
        clientSessionCheck(attachPingReqVO.getClientSessionId());
        CommandInfoMessage commandInfoMessage = socketServer.getInfoMessageMap().get(attachPingReqVO.getClientSessionId());
        String reqId = UUID.fastUUID().toString(true);
        socketServer.sendMessage(attachPingReqVO.getClientSessionId(), HashUtil.fnvHash(reqId), ProtocolTypeEnum.COMMAND,
                CommandLogMessage.buildCommandLogMessage("receive ping uniqueId: " + commandInfoMessage.getUniqueId()));
    }

    /**
     * 获取所有类
     *
     * @param attachClassAllReqVO
     * @return
     */
    public List<String> getClassNames(AttachClassAllReqVO attachClassAllReqVO) {
        clientSessionCheck(attachClassAllReqVO.getClientSessionId());
        String reqId = UUID.fastUUID().toString(true);
        CommandAttachRespMessage attachResp = attachHub.syncResult(reqId, () ->
                        socketServer.sendMessage(attachClassAllReqVO.getClientSessionId(), HashUtil.fnvHash(reqId), ProtocolTypeEnum.COMMAND,
                                CommandAttachReqMessage.buildClassAllMessage(reqId, attachClassAllReqVO.getPackageName())),
                CommandAttachRespMessage.class);
        if (attachResp != null) {
            return attachResp.getClassNames();
        }
        return new ArrayList<>();
    }

    /**
     * 获取类源码
     *
     * @param attachClassSourceReqVO
     * @return
     */
    public AttachClassSourceRespVO getClassSource(AttachClassSourceReqVO attachClassSourceReqVO) {
        clientSessionCheck(attachClassSourceReqVO.getClientSessionId());
        String reqId = UUID.fastUUID().toString(true);
        CommandAttachRespMessage attachResp = attachHub.syncResult(reqId, () ->
                        socketServer.sendMessage(attachClassSourceReqVO.getClientSessionId(), HashUtil.fnvHash(reqId), ProtocolTypeEnum.COMMAND,
                                CommandAttachReqMessage.buildClassSourceMessage(reqId, attachClassSourceReqVO.getClassName(), attachClassSourceReqVO.getSourceCodeType())),
                CommandAttachRespMessage.class);
        if (attachResp != null) {
            return AttachClassSourceRespVO.builder()
                    .byteCodeType(attachResp.getByteCodeType())
                    .classSource(attachResp.getSourceCode())
                    .build();
        }
        return null;
    }

    /**
     * 获取任务列表
     *
     * @param attachTaskReqVO
     * @return
     */
    public List<AttachTaskRespVO> getTask(AttachTaskReqVO attachTaskReqVO) {
        clientSessionCheck(attachTaskReqVO.getClientSessionId());
        String reqId = UUID.fastUUID().toString(true);
        CommandTaskRespMessage attachResp = attachHub.syncResult(reqId, () ->
                        socketServer.sendMessage(attachTaskReqVO.getClientSessionId(), HashUtil.fnvHash(reqId), ProtocolTypeEnum.COMMAND,
                                CommandTaskReqMessage.buildTaskAllMessage(reqId)),
                CommandTaskRespMessage.class);
        if (attachResp != null) {
            return BeanUtil.copyToList(attachResp.getCommandTaskReqMessages(), AttachTaskRespVO.class);
        }
        return new ArrayList<>();
    }

    /**
     * 开启任务
     *
     * @param openReqVO
     * @return
     */
    public List<AttachTaskRespVO> openTask(AttachTaskOpenReqVO openReqVO) {
        clientSessionCheck(openReqVO.getClientSessionId());
        String reqId = UUID.fastUUID().toString(true);
        CommandTaskRespMessage attachResp = attachHub.syncResult(reqId, () ->
                        socketServer.sendMessage(openReqVO.getClientSessionId(), HashUtil.fnvHash(reqId), ProtocolTypeEnum.COMMAND,
                                CommandTaskReqMessage.buildTaskOpenMessage(reqId, openReqVO.getFilePath(), openReqVO.getExpire())),
                CommandTaskRespMessage.class);
        if (attachResp != null) {
            return BeanUtil.copyToList(attachResp.getCommandTaskReqMessages(), AttachTaskRespVO.class);
        }
        return new ArrayList<>();
    }

    /**
     * 关闭任务
     *
     * @param closeReqVO
     * @return
     */
    public List<AttachTaskRespVO> closeTask(AttachTaskCloseReqVO closeReqVO) {
        clientSessionCheck(closeReqVO.getClientSessionId());
        String reqId = UUID.fastUUID().toString(true);
        CommandTaskRespMessage attachResp = attachHub.syncResult(reqId, () ->
                        socketServer.sendMessage(closeReqVO.getClientSessionId(), HashUtil.fnvHash(reqId), ProtocolTypeEnum.COMMAND,
                                CommandTaskReqMessage.buildTaskCloseMessage(reqId, closeReqVO.getFilePath())),
                CommandTaskRespMessage.class);
        if (attachResp != null) {
            attachHub.removeSseEmitter(closeReqVO.getClientSessionId() + "@" + closeReqVO.getFilePath(), null);
            return BeanUtil.copyToList(attachResp.getCommandTaskReqMessages(), AttachTaskRespVO.class);
        }
        return new ArrayList<>();
    }

    /**
     * 源码热更新
     *
     * @param sourceReloadReqVO
     * @return
     */
    @CodeLock(clientSessionId = "#sourceReloadReqVO.clientSessionId", className = "#sourceReloadReqVO.className")
    public AttachClassSourceRespVO sourceReload(AttachSourceReloadReqVO sourceReloadReqVO) {
        clientSessionCheck(sourceReloadReqVO.getClientSessionId());
        String reqId = UUID.fastUUID().toString(true);
        CommandAttachRespMessage attachResp = attachHub.syncResult(reqId, () ->
                        socketServer.sendMessage(sourceReloadReqVO.getClientSessionId(), HashUtil.fnvHash(reqId), ProtocolTypeEnum.COMMAND,
                                CommandAttachReqMessage.buildSourceReloadMessage(reqId, sourceReloadReqVO.getClassName(), sourceReloadReqVO.getSourceCode())),
                CommandAttachRespMessage.class);
        if (attachResp != null) {
            return AttachClassSourceRespVO.builder()
                    .byteCodeType(attachResp.getByteCodeType())
                    .classSource(attachResp.getSourceCode())
                    .build();
        }
        return null;
    }

    /**
     * 字节码热更新
     *
     * @param classFile
     * @param clientSessionId
     * @param className
     * @return
     */
    @CodeLock(clientSessionId = "#clientSessionId", className = "#className")
    public AttachClassSourceRespVO classReload(MultipartFile classFile, String clientSessionId, String className) {
        clientSessionCheck(clientSessionId);
        if (classFile != null && StrUtil.isNotBlank(classFile.getOriginalFilename())) {
            try {
                File file = new File(FileUtils.createTempDir(), classFile.getOriginalFilename());
                classFile.transferTo(file);
                byte[] bytes = Files.readAllBytes(file.toPath());
                String byteCode = Base64Encoder.encode(bytes);
                String reqId = UUID.fastUUID().toString(true);
                CommandAttachRespMessage attachResp = attachHub.syncResult(reqId, () ->
                                socketServer.sendMessage(clientSessionId, HashUtil.fnvHash(reqId), ProtocolTypeEnum.COMMAND,
                                        CommandAttachReqMessage.buildClassReloadMessage(reqId, className, byteCode)),
                        CommandAttachRespMessage.class);
                file.deleteOnExit();
                if (attachResp != null) {
                    return AttachClassSourceRespVO.builder()
                            .byteCodeType(attachResp.getByteCodeType())
                            .classSource(attachResp.getSourceCode())
                            .build();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 代码还原
     *
     * @param classRestoreReqVO
     * @return
     */
    @CodeLock(clientSessionId = "#classRestoreReqVO.clientSessionId", className = "#classRestoreReqVO.className")
    public AttachClassSourceRespVO classRestore(AttachClassRestoreReqVO classRestoreReqVO) {
        clientSessionCheck(classRestoreReqVO.getClientSessionId());
        String reqId = UUID.fastUUID().toString(true);
        CommandAttachRespMessage attachResp = attachHub.syncResult(reqId, () ->
                        socketServer.sendMessage(classRestoreReqVO.getClientSessionId(), HashUtil.fnvHash(reqId), ProtocolTypeEnum.COMMAND,
                                CommandAttachReqMessage.buildClassRestoreMessage(reqId, classRestoreReqVO.getClassName())),
                CommandAttachRespMessage.class);
        if (attachResp != null) {
            return AttachClassSourceRespVO.builder()
                    .byteCodeType(attachResp.getByteCodeType())
                    .classSource(attachResp.getSourceCode())
                    .build();
        }
        return null;
    }

    /**
     * 获取源码带行号
     *
     * @param sourceLineReqVO
     * @return
     */
    @CodeLock(clientSessionId = "#sourceLineReqVO.clientSessionId", className = "#sourceLineReqVO.className")
    public AttachClassSourceLineRespVO getClassSourceMethodLine(AttachClassSourceLineReqVO sourceLineReqVO) {
        clientSessionCheck(sourceLineReqVO.getClientSessionId());
        String reqId = UUID.fastUUID().toString(true);
        CommandAttachRespMessage attachResp = attachHub.syncResult(reqId, () ->
                        socketServer.sendMessage(sourceLineReqVO.getClientSessionId(), HashUtil.fnvHash(reqId), ProtocolTypeEnum.COMMAND,
                                CommandAttachReqMessage.buildClassSourceMethodLineMessage(reqId, sourceLineReqVO.getClassName(), sourceLineReqVO.getLineMethodName())),
                CommandAttachRespMessage.class);
        if (attachResp != null) {
            return AttachClassSourceLineRespVO.builder()
                    .classSource(attachResp.getSourceCode())
                    .lineNumbers(attachResp.getLineNumbers())
                    .build();
        }
        return null;
    }

    /**
     * 行代码补丁
     *
     * @param pathLineReqVO
     * @return
     */
    public AttachClassSourceLineRespVO patchMethodLine(AttachClassPathLineReqVO pathLineReqVO) {
        clientSessionCheck(pathLineReqVO.getClientSessionId());
        String reqId = UUID.fastUUID().toString(true);
        String sourceCode = "{" + pathLineReqVO.getSourceCode() + ";" +
                "com.k4ln.debug4j.common.daemon.Debug4jLine.tag(" + pathLineReqVO.getLineNumber() + ");}";
        CommandAttachRespMessage attachResp = attachHub.syncResult(reqId, () ->
                        socketServer.sendMessage(pathLineReqVO.getClientSessionId(), HashUtil.fnvHash(reqId), ProtocolTypeEnum.COMMAND,
                                CommandAttachReqMessage.buildClassPatchMethodLineMessage(reqId, pathLineReqVO.getClassName(),
                                        pathLineReqVO.getLineMethodName(), sourceCode, pathLineReqVO.getLineNumber())),
                CommandAttachRespMessage.class);
        if (attachResp != null) {
            return AttachClassSourceLineRespVO.builder()
                    .classSource(attachResp.getSourceCode())
                    .lineNumbers(attachResp.getLineNumbers())
                    .build();
        }
        return null;
    }
}

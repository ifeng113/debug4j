package com.k4ln.debug4j.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.HashUtil;
import cn.hutool.core.util.StrUtil;
import com.k4ln.debug4j.common.protocol.command.message.CommandProcessAdjustmentReqMessage;
import com.k4ln.debug4j.common.protocol.command.message.CommandProcessAdjustmentRespMessage;
import com.k4ln.debug4j.common.protocol.command.message.CommandProcessReqMessage;
import com.k4ln.debug4j.common.protocol.command.message.CommandProcessRespMessage;
import com.k4ln.debug4j.common.protocol.command.message.enums.AdjustmentTypeEnum;
import com.k4ln.debug4j.common.protocol.socket.ProtocolTypeEnum;
import com.k4ln.debug4j.common.response.exception.abort.BusinessAbort;
import com.k4ln.debug4j.controller.vo.*;
import com.k4ln.debug4j.socket.SocketServer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ProcessService {

    @Resource
    AttachHub attachHub;

    @Resource
    SocketServer socketServer;

    @Resource
    ProxyService proxyService;

    /**
     * 获取进程参数
     *
     * @param processArgReqVO
     */
    public ProcessArgRespVO args(ProcessArgReqVO processArgReqVO) {
        processArgReqVO.setClientSessionId(attachHub.clientSessionCheck(processArgReqVO.getClientSessionId(), socketServer));
        String reqId = UUID.fastUUID().toString(true);
        CommandProcessRespMessage processResp = attachHub.syncResult(reqId, () ->
                socketServer.sendMessage(processArgReqVO.getClientSessionId(), HashUtil.fnvHash(reqId), ProtocolTypeEnum.COMMAND,
                        CommandProcessReqMessage.buildCommandProcessArgReqMessage(reqId)), CommandProcessRespMessage.class);
        if (processResp != null) {
            return BeanUtil.toBean(processResp, ProcessArgRespVO.class);
        }
        return null;
    }

    /**
     * 重启进程
     *
     * @param processReloadReqVO
     * @return
     */
    public ProcessArgRespVO reload(ProcessReloadReqVO processReloadReqVO) {
        processReloadReqVO.setClientSessionId(attachHub.clientSessionCheck(processReloadReqVO.getClientSessionId(), socketServer));
        String reqId = UUID.fastUUID().toString(true);
        CommandProcessRespMessage processResp = attachHub.syncResult(reqId, () ->
                        socketServer.sendMessage(processReloadReqVO.getClientSessionId(), HashUtil.fnvHash(reqId), ProtocolTypeEnum.COMMAND,
                                CommandProcessReqMessage.buildCommandProcessReloadReqMessage(
                                        reqId, processReloadReqVO.getRemoveJvmArgs(), processReloadReqVO.getAddJvmArgs(),
                                        processReloadReqVO.getRemoveProgramArgs(), processReloadReqVO.getAddProgramArgs(),
                                        processReloadReqVO.getRemoveProperties(), processReloadReqVO.getAddProperties(),
                                        processReloadReqVO.getCoverEnvs())),
                CommandProcessRespMessage.class);
        if (processResp != null) {
            return BeanUtil.toBean(processResp, ProcessArgRespVO.class);
        }
        return null;
    }

    /**
     * 进程内调整
     *
     * @param adjustmentReqVO
     * @return
     */
    public ProcessAdjustmentRespVO adjustment(ProcessAdjustmentReqVO adjustmentReqVO) {
        adjustmentReqVO.setClientSessionId(attachHub.clientSessionCheck(adjustmentReqVO.getClientSessionId(), socketServer));
        String reqId = UUID.fastUUID().toString(true);
        CommandProcessAdjustmentRespMessage adjustmentRespMessage = attachHub.syncResult(reqId, () ->
                        socketServer.sendMessage(adjustmentReqVO.getClientSessionId(), HashUtil.fnvHash(reqId), ProtocolTypeEnum.COMMAND,
                                CommandProcessAdjustmentReqMessage.buildCommandProcessAdjustmentReqMessage(
                                        reqId, adjustmentReqVO.getAdjustmentType(), adjustmentReqVO.getAdjustmentContent())),
                CommandProcessAdjustmentRespMessage.class);
        if (adjustmentRespMessage != null) {
            ProcessAdjustmentRespVO bean = BeanUtil.toBean(adjustmentRespMessage, ProcessAdjustmentRespVO.class);
            adjustmentAfter(adjustmentReqVO, bean);
            return bean;
        }
        return null;
    }

    /**
     * 进程内调整后置处理器
     *
     * @param respVO
     */
    public void adjustmentAfter(ProcessAdjustmentReqVO adjustmentReqVO, ProcessAdjustmentRespVO respVO) {
        if (adjustmentReqVO != null && adjustmentReqVO.getAdjustmentType() != null) {
            switch (adjustmentReqVO.getAdjustmentType()) {
                case sftp_open -> {
                    if (respVO != null && respVO.getAdjustmentResult() != null) {
                        String port = respVO.getAdjustmentResult().get("sftpPort");
                        if (StrUtil.isNotBlank(port) && !port.equals("0")) {
                            String processSessionId = socketServer.getProcessSessionId(adjustmentReqVO.getClientSessionId());
                            if (StrUtil.isNotBlank(processSessionId)) {
                                ProxyRespVO proxyRespVO = proxyService.proxy(ProxyReqVO.builder()
                                        .remark("debug4j sftp server")
                                        .clientSessionId(processSessionId)
                                        .remoteHost("127.0.0.1")
                                        .remotePort(Integer.parseInt(port))
                                        .build());
                                respVO.getAdjustmentResult().put("sftp_proxy", proxyRespVO.getProxyPort() + "");
                            }
                        }
                    }
                }
                case sftp_close -> {
                    if (respVO != null && respVO.getAdjustmentResult() != null) {
                        String port = respVO.getAdjustmentResult().get("sftpPort");
                        if (StrUtil.isNotBlank(port) && !port.equals("0")) {
                            String processSessionId = socketServer.getProcessSessionId(adjustmentReqVO.getClientSessionId());
                            if (StrUtil.isNotBlank(processSessionId)) {
                                proxyService.proxyRemove(ProxyRemoveReqVO.builder()
                                        .clientSessionId(processSessionId)
                                        .remoteHost("127.0.0.1")
                                        .remotePort(Integer.parseInt(port))
                                        .build());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 进程内调整（上传文件）
     *
     * @param multipartFiles
     * @param clientSessionId
     * @param fileDir
     * @return
     */
    public ProcessAdjustmentRespVO adjustmentUpload(MultipartFile[] multipartFiles, String clientSessionId, String fileDir) {
        clientSessionId = attachHub.clientSessionCheck(clientSessionId, socketServer);
        Map<String, String> adjustmentContent = new HashMap<>();
        adjustmentContent.put("fileDir", fileDir);
        ProcessAdjustmentRespVO respVO = adjustment(ProcessAdjustmentReqVO.builder()
                .clientSessionId(clientSessionId)
                .adjustmentType(AdjustmentTypeEnum.file_list)
                .adjustmentContent(adjustmentContent)
                .build());
        if (respVO != null && respVO.getAdjustmentResult() != null && respVO.getAdjustmentResult().get("//errMsg") == null) {
            for (MultipartFile file : multipartFiles) {
                int clientId = HashUtil.fnvHash(UUID.fastUUID().toString(true));
                Map<String, String> fileTemporary = new HashMap<>();
                fileTemporary.put("clientId", clientId + "");
                fileTemporary.put("fileDir", fileDir);
                fileTemporary.put("filename", file.getOriginalFilename());
                adjustment(ProcessAdjustmentReqVO.builder()
                        .clientSessionId(clientSessionId)
                        .adjustmentType(AdjustmentTypeEnum.file_temporary)
                        .adjustmentContent(fileTemporary)
                        .build());
                try {
                    socketServer.sendMessage(clientSessionId, clientId, ProtocolTypeEnum.FILE, file.getBytes());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return adjustment(ProcessAdjustmentReqVO.builder()
                    .clientSessionId(clientSessionId)
                    .adjustmentType(AdjustmentTypeEnum.file_list)
                    .adjustmentContent(adjustmentContent)
                    .build());
        } else {
            throw new BusinessAbort("fileDir query failed");
        }
    }
}

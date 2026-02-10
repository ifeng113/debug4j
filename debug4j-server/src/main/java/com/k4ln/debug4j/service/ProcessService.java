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
import com.k4ln.debug4j.service.dto.AttachFileTask;
import com.k4ln.debug4j.socket.SocketServer;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
                                respVO.getAdjustmentResult().put("sftp_proxy", String.valueOf(proxyRespVO.getProxyPort()));
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
                case module_ssh -> {
                    String processSessionId = socketServer.getProcessSessionId(adjustmentReqVO.getClientSessionId());
                    if (StrUtil.isNotBlank(processSessionId) && StrUtil.isBlank(respVO.getAdjustmentResult().get("//errMsg"))) {
                        ProxyRespVO proxyRespVO = proxyService.proxy(ProxyReqVO.builder()
                                .remark("debug4j ssh server")
                                .clientSessionId(processSessionId)
                                .remoteHost("127.0.0.1")
                                .remotePort(22)
                                .build());
                        respVO.getAdjustmentResult().put("ssh_proxy", String.valueOf(proxyRespVO.getProxyPort()));
                    }
                }
                case module_arthas -> {
                    String processSessionId = socketServer.getProcessSessionId(adjustmentReqVO.getClientSessionId());
                    if (StrUtil.isNotBlank(processSessionId) && StrUtil.isBlank(respVO.getAdjustmentResult().get("//errMsg"))) {
                        ProxyRespVO telnetProxy = proxyService.proxy(ProxyReqVO.builder()
                                .remark("debug4j arthas telnet server")
                                .clientSessionId(processSessionId)
                                .remoteHost("127.0.0.1")
                                .remotePort(3658)
                                .build());
                        respVO.getAdjustmentResult().put("arthas_telnet_proxy", String.valueOf(telnetProxy.getProxyPort()));
                        ProxyRespVO webConsole = proxyService.proxy(ProxyReqVO.builder()
                                .remark("debug4j arthas web-console server")
                                .clientSessionId(processSessionId)
                                .remoteHost("127.0.0.1")
                                .remotePort(8563)
                                .build());
                        respVO.getAdjustmentResult().put("arthas_web_console_proxy", String.valueOf(webConsole.getProxyPort()));
                    }
                }
                case module_proxy -> {
                    String processSessionId = socketServer.getProcessSessionId(adjustmentReqVO.getClientSessionId());
                    if (StrUtil.isNotBlank(processSessionId)) {
                        Map<String, String> adjustmentContent = adjustmentReqVO.getAdjustmentContent();
                        boolean status = StrUtil.isNotBlank(adjustmentContent.get("status")) && "enable".equals(adjustmentContent.get("status"));
                        if (status) {
                            ProxyRespVO telnetProxy = proxyService.proxy(ProxyReqVO.builder()
                                    .remark("debug4j http proxy server")
                                    .clientSessionId(processSessionId)
                                    .remoteHost("127.0.0.1")
                                    .remotePort(7980)
                                    .build());
                            respVO.getAdjustmentResult().put("http(s)_proxy", String.valueOf(telnetProxy.getProxyPort()));
                        } else {
                            try {
                                proxyService.proxyRemove(ProxyRemoveReqVO.builder()
                                        .clientSessionId(processSessionId)
                                        .remoteHost("127.0.0.1")
                                        .remotePort(7980)
                                        .build());
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 上传文件
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
        adjustmentContent.put("createIfNotExist", "true");
        ProcessAdjustmentRespVO respVO = adjustment(ProcessAdjustmentReqVO.builder()
                .clientSessionId(clientSessionId)
                .adjustmentType(AdjustmentTypeEnum.file_list)
                .adjustmentContent(adjustmentContent)
                .build());
        if (respVO != null && respVO.getAdjustmentResult() != null && respVO.getAdjustmentResult().get("//errMsg") == null) {
            for (MultipartFile file : multipartFiles) {
                int clientId = HashUtil.fnvHash(UUID.fastUUID().toString(true));
                Map<String, String> fileTemporary = new HashMap<>();
                fileTemporary.put("clientId", String.valueOf(clientId));
                fileTemporary.put("fileDir", fileDir);
                fileTemporary.put("filename", file.getOriginalFilename());
                adjustment(ProcessAdjustmentReqVO.builder()
                        .clientSessionId(clientSessionId)
                        .adjustmentType(AdjustmentTypeEnum.file_upload)
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

    /**
     * 下载文件
     *
     * @param adjustmentReqVO
     * @param response
     */
    public void adjustmentDownload(ProcessAdjustmentReqVO adjustmentReqVO, HttpServletResponse response) {
        adjustmentReqVO.setClientSessionId(attachHub.clientSessionCheck(adjustmentReqVO.getClientSessionId(), socketServer));
        String fileDir = adjustmentReqVO.getAdjustmentContent().get("fileAbsolutePath");
        ProcessAdjustmentRespVO respVO = adjustment(ProcessAdjustmentReqVO.builder()
                .clientSessionId(adjustmentReqVO.getClientSessionId())
                .adjustmentType(AdjustmentTypeEnum.file_list)
                .adjustmentContent(Map.of("fileDir", fileDir))
                .build());
        String reqId = UUID.fastUUID().toString(true);
        int clientId = HashUtil.fnvHash(reqId);
        try {
            if (StrUtil.isNotBlank(fileDir) && respVO != null && respVO.getAdjustmentResult() != null) {
                fileDir = fileDir.trim();
                Map<String, String> adjustmentContent = new HashMap<>();
                adjustmentContent.put("fileAbsolutePath", fileDir);
                adjustmentContent.put("clientId", String.valueOf(clientId));
                attachHub.getAttachFileTask().put(clientId, AttachFileTask.builder().build());
                socketServer.sendMessage(adjustmentReqVO.getClientSessionId(), clientId, ProtocolTypeEnum.COMMAND,
                        CommandProcessAdjustmentReqMessage.buildCommandProcessAdjustmentReqMessage(reqId,
                                AdjustmentTypeEnum.file_download, adjustmentContent));
                response.setContentType("application/octet-stream");
                String errMsg = respVO.getAdjustmentResult().get("//errMsg");
                String filename;
                Path path = Paths.get(fileDir);
                if (StrUtil.isNotBlank(errMsg) && errMsg.equals("fileDir is not directory")) {
                    filename = path.normalize().getFileName().toString();
                } else if (StrUtil.isBlank(errMsg)) {
                    filename = path.normalize().getFileName().toString() + ".zip";
                } else {
                    response.getOutputStream().write("fileDir query failed".getBytes());
                    response.getOutputStream().flush();
                    return;
                }
                response.setHeader("Content-Disposition", "attachment;filename=" + filename);
                do {
                    byte[] take = attachHub.getAttachFileTask().get(clientId).getQueue().poll(100, TimeUnit.MILLISECONDS);
                    if (take == null) continue;
                    response.getOutputStream().write(take);
                    response.getOutputStream().flush();
                } while (!attachHub.getAttachFileTask().get(clientId).getCompleted() || !attachHub.getAttachFileTask().get(clientId).getQueue().isEmpty());
            } else {
                response.getOutputStream().write("fileDir query failed".getBytes());
                response.getOutputStream().flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            attachHub.getAttachFileTask().remove(clientId);
        }
    }
}

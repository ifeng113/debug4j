package com.k4ln.debug4j.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.HashUtil;
import com.k4ln.debug4j.common.protocol.command.message.CommandProcessReqMessage;
import com.k4ln.debug4j.common.protocol.command.message.CommandProcessRespMessage;
import com.k4ln.debug4j.common.protocol.socket.ProtocolTypeEnum;
import com.k4ln.debug4j.controller.vo.ProcessArgReqVO;
import com.k4ln.debug4j.controller.vo.ProcessArgRespVO;
import com.k4ln.debug4j.socket.SocketServer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProcessService {

    @Resource
    AttachHub attachHub;

    @Resource
    SocketServer socketServer;

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
}

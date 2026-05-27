package com.k4ln.debug4j.service;

import cn.hutool.core.bean.BeanUtil;
import com.k4ln.debug4j.common.protocol.command.message.CommandInfoMessage;
import com.k4ln.debug4j.controller.vo.ManageClientRespVO;
import com.k4ln.debug4j.socket.SocketServer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ManageService {

    @Resource
    SocketServer socketServer;

    /**
     * 获取客户端
     *
     * @return
     */
    public List<ManageClientRespVO> getClients() {
        Map<String, CommandInfoMessage> infoMessageMap = socketServer.getInfoMessageMap();
        List<ManageClientRespVO> list = new ArrayList<>(infoMessageMap.values().stream().map(e -> BeanUtil.toBean(e, ManageClientRespVO.class)).toList());
        list.sort(Comparator.comparing(ManageClientRespVO::getApplicationName)
                .thenComparing(ManageClientRespVO::getUniqueId)
                .thenComparing(ManageClientRespVO::getDebug4jMode, Comparator.reverseOrder())
                .thenComparing(ManageClientRespVO::getRootUniqueId, Comparator.reverseOrder()));
        return list;
    }
}

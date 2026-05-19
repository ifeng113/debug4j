package com.k4ln.debug4j.common.utils;

import cn.hutool.core.convert.Convert;
import com.k4ln.debug4j.common.protocol.socket.ProtocolTypeEnum;
import com.k4ln.debug4j.common.protocol.socket.SocketProtocol;
import lombok.extern.slf4j.Slf4j;
import org.smartboot.socket.transport.AioSession;
import org.smartboot.socket.transport.WriteBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 协议工具方法（优化版）
 */
@Slf4j
public class SocketProtocolUtilV2 {

    public static final int READ_BUFFER_SIZE = 4096;

    public static final int BUFFER_LENGTH = 4;

    public static final int BUFFER_HEADER = 12;

    /**
     * 解析协议
     */
    public static SocketProtocol analysisProxyProtocol(ByteBuffer readBuffer) {
        if (readBuffer.remaining() < Integer.BYTES) {
            return null;
        }
        readBuffer.mark();
        int bodyLength = readBuffer.getInt();
        if (bodyLength + BUFFER_HEADER > readBuffer.remaining()) {
            readBuffer.reset();
            return null;
        }
        int version = readBuffer.get() & 0xFF;
        byte[] protocolBytes = new byte[2];
        readBuffer.get(protocolBytes);
        ProtocolTypeEnum protocolType = ProtocolTypeEnum.getProtocolTypeByCode("0x" + Convert.toHex(protocolBytes));
        boolean subcontract = readBuffer.get() == 1;
        int subcontractCount = readBuffer.getShort() & 0xFFFF;
        int subcontractIndex = readBuffer.getShort() & 0xFFFF;
        int clientId = readBuffer.getInt();
        byte[] body = new byte[bodyLength];
        readBuffer.get(body);
        return SocketProtocol.builder()
                .version(version)
                .protocolType(protocolType)
                .subcontract(subcontract)
                .subcontractCount(subcontractCount)
                .subcontractIndex(subcontractIndex)
                .clientId(clientId)
                .body(body)
                .build();
    }

    /**
     * 发送消息
     */
    public static void sendMessage(
            AioSession session,
            Integer clientId,
            ProtocolTypeEnum protocolType,
            byte[] body) {
        if (body == null) {
            body = new byte[0];
        }
        int maxBodyLength = READ_BUFFER_SIZE - BUFFER_LENGTH - BUFFER_HEADER;
        WriteBuffer writeBuffer = session.writeBuffer();
        if (body.length <= maxBodyLength) {
            send(writeBuffer, SocketProtocol.builder()
                    .protocolType(protocolType)
                    .subcontract(false)
                    .clientId(clientId)
                    .body(body)
                    .build()
            );
            return;
        }
        int subcontractCount = (body.length + maxBodyLength - 1) / maxBodyLength;
        for (int part = 1, offset = 0; offset < body.length; part++, offset += maxBodyLength) {
            int bodyLength = Math.min(maxBodyLength, body.length - offset);
            byte[] simple = new byte[bodyLength];
            System.arraycopy(body, offset, simple, 0, bodyLength);
            send(writeBuffer, SocketProtocol.builder()
                    .protocolType(protocolType)
                    .subcontract(true)
                    .subcontractCount(subcontractCount)
                    .subcontractIndex(part)
                    .clientId(clientId)
                    .body(simple)
                    .build()
            );
        }
    }

    /**
     * 文件消息
     */
    public static void sendFileMessage(
            AioSession session,
            Integer clientId,
            byte[] body,
            Integer subcontractCount,
            Integer subcontractIndex) {
        WriteBuffer writeBuffer = session.writeBuffer();
        send(writeBuffer, SocketProtocol.builder()
                .protocolType(ProtocolTypeEnum.FILE)
                .subcontract(true)
                .clientId(clientId)
                .subcontractCount(subcontractCount)
                .subcontractIndex(subcontractIndex)
                .body(body)
                .build());
    }

    /**
     * 发送
     */
    private static void send(
            WriteBuffer writeBuffer,
            SocketProtocol socketProtocol) {
        try {
            byte[] data = buildProxyProtocol(socketProtocol);
            writeBuffer.writeAndFlush(data); // proxy protocol 必须立即推送
        } catch (IOException e) {
            log.error("socket send error", e);
        }
    }

    /**
     * 构建协议
     */
    private static byte[] buildProxyProtocol(SocketProtocol socketProtocol) {
        return buildProxyProtocol(
                socketProtocol.getVersion(),
                socketProtocol.getProtocolType(),
                socketProtocol.getSubcontract(),
                socketProtocol.getSubcontractCount(),
                socketProtocol.getSubcontractIndex(),
                socketProtocol.getClientId(),
                socketProtocol.getBody()
        );
    }

    /**
     * 构建协议数据
     */
    public static byte[] buildProxyProtocol(
            Integer version,
            ProtocolTypeEnum protocolType,
            Boolean subcontract,
            Integer subcontractCount,
            Integer subcontractIndex,
            Integer clientId,
            byte[] body) {
        if (body == null) {
            body = new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_LENGTH + BUFFER_HEADER + body.length);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(body.length);
        buffer.put(version.byteValue());
        buffer.put(Convert.hexToBytes(protocolType.getCode().replace("0x", "")));
        buffer.put((byte) (subcontract ? 1 : 0));
        buffer.putShort(subcontractCount.shortValue());
        buffer.putShort(subcontractIndex.shortValue());
        buffer.putInt(clientId);
        buffer.put(body);
        return buffer.array();
    }
}
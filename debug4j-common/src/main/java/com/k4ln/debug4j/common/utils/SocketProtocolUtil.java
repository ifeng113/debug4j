package com.k4ln.debug4j.common.utils;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ByteUtil;
import cn.hutool.core.util.NumberUtil;
import com.k4ln.debug4j.common.protocol.socket.ProtocolTypeEnum;
import com.k4ln.debug4j.common.protocol.socket.SocketProtocol;
import lombok.extern.slf4j.Slf4j;
import org.smartboot.socket.transport.AioSession;
import org.smartboot.socket.transport.WriteBuffer;

import java.io.IOException;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 协议工具方法
 *
 * @author k4ln
 * @since 2024-04-22
 */
@Slf4j
public class SocketProtocolUtil {

    public static final int READ_BUFFER_SIZE = 4096;

    public static final int BUFFER_LENGTH = 4;

    public static final int BUFFER_HEADER = 12;

    /**
     * 解析代理协议
     *
     * @param readBuffer
     * @return
     */
    public static SocketProtocol analysisProxyProtocol(ByteBuffer readBuffer) {

        int remaining = readBuffer.remaining();
        if (remaining < Integer.BYTES) {
            return null;
        }
        readBuffer.mark();

        int dataLength = readBuffer.getInt();

        int messageBodyLength = dataLength + BUFFER_HEADER;
        if (messageBodyLength > readBuffer.remaining()) {
            readBuffer.reset();
            return null;
        }

        byte[] header = new byte[BUFFER_HEADER];
        readBuffer.get(header);
        readBuffer.mark();

        int version = header[0];
        ProtocolTypeEnum protocolType = ProtocolTypeEnum.getProtocolTypeByCode("0x" + Convert.toHex(ArrayUtil.sub(header, 1, 3)));
        int subcontract = header[3];
        int subcontractCount = ByteUtil.bytesToShort(ArrayUtil.sub(header, 4, 6), ByteOrder.BIG_ENDIAN);
        int subcontractIndex = ByteUtil.bytesToShort(ArrayUtil.sub(header, 6, 8), ByteOrder.BIG_ENDIAN);
        int clientId = ByteUtil.bytesToInt(ArrayUtil.sub(header, 8, 12), ByteOrder.BIG_ENDIAN);

        byte[] body = new byte[dataLength];
        readBuffer.get(body);
        readBuffer.mark();

        return SocketProtocol.builder()
                .version(version)
                .protocolType(protocolType)
                .subcontract(subcontract == 1)
                .subcontractCount(subcontractCount)
                .subcontractIndex(subcontractIndex)
                .clientId(clientId)
                .body(body)
                .build();
    }

    /**
     * 构建代理协议
     *
     * @param socketProtocol
     * @return
     */
    private static byte[] buildProxyProtocol(SocketProtocol socketProtocol) {
        return buildProxyProtocol(socketProtocol.getVersion(), socketProtocol.getProtocolType(), socketProtocol.getSubcontract(),
                socketProtocol.getSubcontractCount(), socketProtocol.getSubcontractIndex(), socketProtocol.getClientId(), socketProtocol.getBody());
    }

    /**
     * 发送数据
     *
     * @param session
     * @param clientId
     * @param protocolType
     * @param body
     */
    public static void sendMessage(AioSession session, Integer clientId, ProtocolTypeEnum protocolType, byte[] body) {
        if (body == null || body.length == 0) {
            body = new byte[0];
        }
        int maxBodyLength = READ_BUFFER_SIZE - BUFFER_LENGTH - BUFFER_HEADER;
        if (body.length > maxBodyLength) {
            double div = NumberUtil.div(body.length, maxBodyLength, 0, RoundingMode.UP);
            int subcontractCount = Double.valueOf(div).intValue();
            for (int i = 0; i < body.length; i += maxBodyLength) {
                int bodyLength = Math.min(maxBodyLength, body.length - i);
                byte[] simple = new byte[bodyLength];
                System.arraycopy(body, i, simple, 0, bodyLength);
                send(session, SocketProtocol.builder()
                        .protocolType(protocolType)
                        .subcontract(true)
                        .subcontractCount(subcontractCount)
                        .subcontractIndex(i / maxBodyLength + 1)
                        .clientId(clientId)
                        .body(simple)
                        .build());
            }
        } else {
            send(session, SocketProtocol.builder()
                    .protocolType(protocolType)
                    .subcontract(false)
                    .clientId(clientId)
                    .body(body)
                    .build());
        }
    }

    /**
     * 发送数据
     *
     * @param session
     * @param clientId
     * @param body
     * @param subcontractCount
     * @param subcontractIndex
     */
    public static void sendFileMessage(AioSession session, Integer clientId, byte[] body, Integer subcontractCount, Integer subcontractIndex) {
        send(session, SocketProtocol.builder()
                .protocolType(ProtocolTypeEnum.FILE)
                .subcontract(true)
                .clientId(clientId)
                .subcontractCount(subcontractCount)
                .subcontractIndex(subcontractIndex)
                .body(body)
                .build());
    }

    /**
     * 发送数据
     *
     * @param session
     * @param socketProtocol
     */
    private static void send(AioSession session, SocketProtocol socketProtocol) {
        try {
            WriteBuffer writeBuffer = session.writeBuffer();
            byte[] data = buildProxyProtocol(socketProtocol);
            writeBuffer.writeAndFlush(data); // proxy protocol 必须立即推送
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 构建代理协议
     *
     * @param version
     * @param protocolType
     * @param subcontract
     * @param subcontractCount
     * @param subcontractIndex
     * @param body
     * @return
     */
    public static byte[] buildProxyProtocol(Integer version, ProtocolTypeEnum protocolType, Boolean subcontract,
                                            Integer subcontractCount, Integer subcontractIndex, Integer clientId, byte[] body) {
        byte[] header = new byte[BUFFER_HEADER];
        byte[] versionBytes = Convert.shortToBytes(version.shortValue());
        System.arraycopy(versionBytes, 0, header, 0, 1);
        byte[] protocolTypeBytes = Convert.hexToBytes(protocolType.getCode().replace("0x", ""));
        System.arraycopy(protocolTypeBytes, 0, header, 1, 2);
        byte subcontractByte = Convert.intToByte(subcontract ? 1 : 0);
        header[3] = subcontractByte;
        byte[] subcontractCountBytes = ByteUtil.shortToBytes(subcontractCount.shortValue(), ByteOrder.BIG_ENDIAN);
        System.arraycopy(subcontractCountBytes, 0, header, 4, 2);
        byte[] subcontractIndexBytes = ByteUtil.shortToBytes(subcontractIndex.shortValue(), ByteOrder.BIG_ENDIAN);
        System.arraycopy(subcontractIndexBytes, 0, header, 6, 2);
        byte[] clientIdBytes = ByteUtil.intToBytes(clientId, ByteOrder.BIG_ENDIAN);
        System.arraycopy(clientIdBytes, 0, header, 8, 4);

        byte[] data = new byte[4 + header.length + body.length];
        byte[] lengthBytes = ByteUtil.intToBytes(body.length, ByteOrder.BIG_ENDIAN);
        System.arraycopy(lengthBytes, 0, data, 0, 4);
        System.arraycopy(header, 0, data, 4, BUFFER_HEADER);
        System.arraycopy(body, 0, data, 4 + BUFFER_HEADER, body.length);
        return data;
    }
}
package com.k4ln.debug4j.common.protocol.command.message;

import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.common.protocol.command.Command;
import com.k4ln.debug4j.common.protocol.command.CommandTypeEnum;
import com.k4ln.debug4j.common.protocol.command.message.enums.ByteCodeTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandAttachRespMessage {

    /**
     * 请求ID
     */
    private String reqId;

    /**
     * 源码
     */
    private String sourceCode;

    /**
     * 源码方法列表
     */
    private List<String> classMethods;

    /**
     * 字节码类型
     */
    private ByteCodeTypeEnum byteCodeType;

    /**
     * 行号
     */
    private List<Integer> lineNumbers;

    /**
     * 所有类
     */
    private List<String> classNames;

    /**
     * 状态（成功/失败）
     */
    private Boolean status;

    public static byte[] buildClassAllRespMessage(String reqId, List<String> classNames) {
        return (JSON.toJSONString(Command.builder()
                .command(CommandTypeEnum.ATTACH_RESP_CLASS_ALL)
                .data(CommandAttachRespMessage.builder()
                        .reqId(reqId)
                        .classNames(classNames)
                        .build())
                .build())
        ).getBytes();
    }

    public static byte[] buildClassSourceRespMessage(String reqId, String sourceCode, List<String> classMethods, ByteCodeTypeEnum byteCodeType, Boolean status) {
        return (JSON.toJSONString(Command.builder()
                .command(CommandTypeEnum.ATTACH_RESP_CLASS_SOURCE)
                .data(CommandAttachRespMessage.builder()
                        .reqId(reqId)
                        .sourceCode(sourceCode)
                        .classMethods(classMethods)
                        .byteCodeType(byteCodeType)
                        .status(status)
                        .build())
                .build())
        ).getBytes();
    }

    public static byte[] buildClassSourceLineRespMessage(String reqId, String sourceCode, List<String> classMethods, List<Integer> lineNumbers) {
        return (JSON.toJSONString(Command.builder()
                .command(CommandTypeEnum.ATTACH_RESP_CLASS_SOURCE_LINE)
                .data(CommandAttachRespMessage.builder()
                        .reqId(reqId)
                        .sourceCode(sourceCode)
                        .lineNumbers(lineNumbers)
                        .classMethods(classMethods)
                        .build())
                .build())
        ).getBytes();
    }
}

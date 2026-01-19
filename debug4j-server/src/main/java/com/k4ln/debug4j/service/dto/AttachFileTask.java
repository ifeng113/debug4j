package com.k4ln.debug4j.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachFileTask {

    /**
     * 是否已完成
     */
    @Builder.Default
    private Boolean completed = false;

    /**
     * 文件流队列
     */
    @Builder.Default
    private BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();

}

package com.k4ln.debug4j.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachTaskEmitter {

    /**
     * 登录ID
     */
    private String loginId;

    /**
     * 推送器
     */
    private SseEmitter sseEmitter;
}

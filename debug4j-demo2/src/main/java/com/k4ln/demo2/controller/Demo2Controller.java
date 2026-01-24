package com.k4ln.demo2.controller;


import com.k4ln.demo2.aop.TestAop;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * 服务端接口类
 *
 * @author k4ln
 * @since 2024-10-22
 */
@Slf4j
@RestController
@RequestMapping("/demo2")
public class Demo2Controller {

    @Value("${debug4j.key}")
    String key;

    @GetMapping
    @TestAop
    public String demo2() {
        log.trace("------------trace------------");
        log.debug("------------debug------------");
        log.info("------------info------------");
        log.warn("------------warn------------");
        log.error("------------error------------");
        Demo2ControllerDto demo2ControllerDto = Demo2ControllerDto.builder().key("w2").build();
        log.info(demo2ControllerDto.getKey());
        return "demo2";
    }

    @GetMapping("/p")
    public String demo2(@RequestParam(required = false) String p) {
        log.error("------------param------------");
        return "demo2_c";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Demo2ControllerDto {

        @Builder.Default
        String key = "777";
    }
}

package com.k4ln.demo2.controller;


import lombok.extern.slf4j.Slf4j;
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

    @GetMapping
    public String demo2() {
        log.trace("------------trace------------");
        log.debug("------------debug------------");
        log.info("------------info------------");
        log.warn("------------warn------------");
        log.error("------------error------------");
        return "demo2";
    }

    @GetMapping("/p")
    public String demo2(@RequestParam(required = false) String p) {
        log.error("------------param------------");
        return "demo2_c";
    }
}

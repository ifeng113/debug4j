package com.k4ln.demo2.controller;


import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.common.response.Result;
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

import java.util.List;


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

    public static String k2 = "k2---";

    public static Result<String> k88 = Result.ok("k88");

    public static Result<String>[] k100 = new Result[]{Result.ok("k100")};

    public static Class<?> k89 = Result.class;

    public Demo2ControllerDto d2 = Demo2ControllerDto.builder().key("d2").build();

    private List<String> k3 = List.of("k3");

    @GetMapping
    @TestAop
    public String demo2() {
        log.trace("------------trace------------");
        log.debug("------------debug------------");
        log.info("------------info------------");
        log.warn("------------warn------------");
        log.error("------------error------------");
        Demo2ControllerDto demo2ControllerDto = Demo2ControllerDto.builder().key("w2").build();
        log.info("222:{}", demo2ControllerDto.getKey());
        demo2ControllerDto.logTest();
        return "demo2";
    }


    @GetMapping("/p")
    public String demo2(@RequestParam(required = false) String p) {
        log.error("------------param------------");

        log.info("k2:{}", k2);
        log.info("d2:{}", JSON.toJSONString(d2));
        log.info("k3:{}", JSON.toJSONString(k3));
        log.info("k88:{}", JSON.toJSONString(k88));
        log.info("k89:{}", JSON.toJSONString(k89));
        log.info("k100:{}", JSON.toJSONString(k100));

        return "demo2_p";
    }

    @GetMapping("/p2")
    public Result<String> demo2(@RequestParam(required = false) List<String> pps, @RequestParam(required = false) String p22) {
        log.info("pps:{}", JSON.toJSONString(pps));
        log.info("p22:{}", p22);
        log.error("------------demo2_p2------------");
        return Result.ok("demo2_p2");
    }

    public void voidTest(Result<String>[] k101) {
        log.info("voidTest:{}", JSON.toJSONString(k101));
    }

    public void voidTest2(String[] k102) {
        log.info("voidTest2:{}", JSON.toJSONString(k102));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Demo2ControllerDto {

        @Builder.Default
        String key = "777";

        private void logTest() {
            log.info("key:{}", key);
        }
    }
}

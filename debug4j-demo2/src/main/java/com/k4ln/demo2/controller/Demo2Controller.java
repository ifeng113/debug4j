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
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.k4ln.demo2.controller.Demo2StaticUtils.hKey;


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
    public String demo2(@RequestParam(required = false) String p, Integer pi) {
        log.error("------------param------------");

        log.info("k2:{}", k2);
        log.info("d2:{}", JSON.toJSONString(d2));
        log.info("k3:{}", JSON.toJSONString(k3));
        log.info("k88:{}", JSON.toJSONString(k88));
        log.info("k89:{}", JSON.toJSONString(k89));
        log.info("k100:{}", JSON.toJSONString(k100));

        log.info("hKey:{}", JSON.toJSONString(hKey));


        log.info("pi:{}", pi);

        return "demo2_p";
    }

    @GetMapping("/p2")
    public Result<String> demo2(@RequestParam(required = false) List<String> pps, @RequestParam(required = false) String p22) {
        log.info("pps:{}", JSON.toJSONString(pps));
        log.info("p22:{}", p22);
        log.error("------------demo2_p2------------");
        return Result.ok("demo2_p2");
    }

    @PostMapping("/p2p")
    public Result<List<String>> demo2(@RequestBody List<String> pps) {
        log.info("pps:{}", JSON.toJSONString(pps));
        log.error("------------p2p------------");
        return Result.ok(pps);
    }

    @PostMapping("/p9")
    public Result<Demo2ControllerDto> demo9(@RequestBody Demo2ControllerDto d2) {
        log.info("p9:{}", JSON.toJSONString(d2));
        log.error("------------p9------------");
        return Result.ok(d2);
    }

    @PostMapping("/p91")
    public Result<DemoNoBeanDto> demo9(@RequestBody DemoNoBeanDto d91) {
        log.info("p91:{}", JSON.toJSONString(d91));
        log.error("------------p91------------");
        return Result.ok(d91);
    }


    @GetMapping("/p3")
    public Result<String> demo3(@RequestParam(required = false, name = "pps") List<String> pps, @RequestParam(required = false, name = "p22") String p22) {
        int a = 9;
        int b = 0;
        int c = a / b;
        return Result.ok("demo2_p2");
    }

    @GetMapping("/p4")
    @TestAop
    public Result<String> demo4() {
        log.info("k1001---:{}", JSON.toJSONString(k100));

        DemoNoBeanDto demoP4 = new DemoNoBeanDto("demo_p4");
        demoP4.noBeanTest();

        return Result.ok("demo_p4");
    }

    @PostMapping("/t1")
    public Result<String> voidTest(@RequestBody Result<String>[] k101) {
        log.info("voidTest:{}", JSON.toJSONString(k101));
        return Result.ok("demo_p4");
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

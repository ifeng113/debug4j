package com.k4ln.debug4j.controller;


import com.k4ln.debug4j.common.response.Result;
import com.k4ln.debug4j.controller.vo.ProxyDetailsRespVO;
import com.k4ln.debug4j.controller.vo.ProxyRemoveReqVO;
import com.k4ln.debug4j.controller.vo.ProxyReqVO;
import com.k4ln.debug4j.controller.vo.ProxyRespVO;
import com.k4ln.debug4j.service.ProxyService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 代理接口类
 *
 * @author k4ln
 * @since 2024-10-22
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/proxy")
public class ProxyController {

    @Resource
    ProxyService proxyService;

    /**
     * 获取代理详情
     *
     * @return
     */
    @GetMapping("/details")
    public Result<List<ProxyDetailsRespVO>> proxyServer(@RequestParam("clientSessionId") String clientSessionId) {
        return Result.ok(proxyService.getProxyServer(clientSessionId));
    }

    /**
     * 创建代理
     *
     * @param proxyReqVO
     * @return
     */
    // 当方法修饰符为private时，接口可被访问但是如果使用@Validated注解，会导致单例模式失效，即proxyService为null
    @PostMapping
    public Result<ProxyRespVO> proxy(@RequestBody @Valid ProxyReqVO proxyReqVO) {
        return Result.ok(proxyService.proxy(proxyReqVO));
    }

    /**
     * 删除代理
     *
     * @param removeReqVO
     * @return
     */
    @PostMapping("/remove")
    public Result<Boolean> proxyRemove(@RequestBody @Valid ProxyRemoveReqVO removeReqVO) {
        proxyService.proxyRemove(removeReqVO);
        return Result.ok(true);
    }

}

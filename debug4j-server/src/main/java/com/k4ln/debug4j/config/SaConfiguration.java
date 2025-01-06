package com.k4ln.debug4j.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.error.SaErrorCode;
import cn.dev33.satoken.exception.NotHttpBasicAuthException;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.util.SaFoxUtil;
import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.common.response.HttpResult;
import com.k4ln.debug4j.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static cn.dev33.satoken.httpauth.basic.SaHttpBasicUtil.getAuthorizationValue;

@Slf4j
@Configuration
public class SaConfiguration {

    @Bean
    public SaServletFilter getSaServletFilter() {
        return new SaServletFilter()
                .addInclude("/manage/**", "/proxy/**", "/attach/**").addExclude("/attach/task")
                .setBeforeAuth(obj -> {
                    SaHolder.getResponse()
                            .setHeader("Access-Control-Allow-Origin", "*")
                            .setHeader("Access-Control-Allow-Methods", "*")
                            .setHeader("Access-Control-Allow-Headers", "*");
                    SaRouter.match(SaHttpMethod.OPTIONS).back();
                })
                .setAuth(obj -> SaRouter.match("/**", () -> {
//                    SaHttpBasicUtil.check();
                    // 屏蔽httpBasic异常导致浏览器弹窗
                    String account = SaManager.getConfig().getHttpBasic();
                    String authorization = getAuthorizationValue();
                    if (SaFoxUtil.isEmpty(authorization) || !authorization.equals(account)) {
                        SaHolder.getResponse().setStatus(401);
                        throw new NotHttpBasicAuthException().setCode(SaErrorCode.CODE_10311);
                    }
                }))
                .setError(e -> {
                    SaHolder.getResponse().setHeader("Content-Type", "application/json;charset=UTF-8");
                    return JSON.toJSONString(Result.result(HttpResult.NO_AUTH, e.getMessage()));
                });
    }
}

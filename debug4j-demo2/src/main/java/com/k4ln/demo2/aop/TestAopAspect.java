package com.k4ln.demo2.aop;


import com.k4ln.debug4j.common.response.exception.abort.BusinessAbort;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * TestAop切面类
 *
 * @author k4ln
 * @since 2024-10-22
 */
@Aspect
@Slf4j
@Component
public class TestAopAspect {

    @Around("@annotation(com.k4ln.demo2.aop.TestAop)")
    @SneakyThrows
    public Object auditAround(ProceedingJoinPoint pjp) {
        Object proceed;
        try {
            Thread.sleep(52);
            proceed = pjp.proceed(pjp.getArgs());
            return proceed;
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessAbort("testAop error:" + e.getMessage());
        }
    }

}

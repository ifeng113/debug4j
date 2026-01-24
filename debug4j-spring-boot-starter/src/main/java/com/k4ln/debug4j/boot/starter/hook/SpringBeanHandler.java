package com.k4ln.debug4j.boot.starter.hook;

import cn.hutool.extra.spring.SpringUtil;

public class SpringBeanHandler {

    /**
     * 获取 Spring Bean
     * 支持debug断点
     * 支持AOP切面
     *
     * @param beanName
     * @return
     */
    public static Object getBean(Object beanName) {
        if (beanName instanceof String) {
            try {
                return SpringUtil.getBean((String) beanName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

}

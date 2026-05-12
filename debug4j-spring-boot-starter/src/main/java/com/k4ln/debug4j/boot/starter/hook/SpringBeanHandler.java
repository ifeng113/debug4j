package com.k4ln.debug4j.boot.starter.hook;

import cn.hutool.extra.spring.SpringUtil;
import org.springframework.aop.framework.AopProxyUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SpringBeanHandler {

    /**
     * 获取所有Spring Bean名称
     *
     * @param obj
     * @return
     */
    public static List<String> discovery(Object obj) {
        return Arrays.asList(SpringUtil.getBeanFactory().getBeanDefinitionNames());
    }

    /**
     * 获取 Spring Bean
     * 支持debug断点
     * objTypeParam=1 支持属性获取修改，不支持aop；objTypeParam=2 支持aop，不支持属性获取修改；
     *
     * @param hookObj
     * @return
     */
    public static Object getBean(Object hookObj) {
        if (hookObj instanceof Map) {
            try {
                Map hookObjMap = (Map) hookObj;
                String objTypeParam = (String) hookObjMap.get("objTypeParam");
                String objName = (String) hookObjMap.get("objName");
                if ("1".equals(objTypeParam)) {
                    Object bean = SpringUtil.getBean(objName);
                    return AopProxyUtils.getSingletonTarget(bean);
                } else {
                    return SpringUtil.getBean(objName);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

}

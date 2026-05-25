package com.k4ln.debug4j.boot.starter.hook;

import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class PropertySourcesHandler {

    private static final String SOURCE_NAME = "debug4jDynamicProperty";

    private static final Map<String, Object> SOURCE_DATA = new ConcurrentHashMap<>();

    /**
     * 获取全参数
     *
     * @param environment
     * @return
     */
    public static Map<String, List<String>> getAllProperties(ConfigurableEnvironment environment) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        MutablePropertySources propertySources = environment.getPropertySources();
        for (PropertySource<?> propertySource : propertySources) {
            Object source = propertySource.getSource();
            if (source instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> sourceMap = (Map<Object, Object>) source;
                map.put(propertySource.getName(), sourceMap.entrySet()
                        .stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .sorted(Comparator.comparing(String::toString))
                        .toList());
            } else if (source instanceof CommandLineArgs commandLineArgs) { // 类遮蔽：https://blog.csdn.net/AlbenXie/article/details/150315384。注意：当被遮蔽的类以libs方式被加载时会失效。
                List<String> nonOptionArgs = commandLineArgs.getNonOptionArgs();
                map.put(propertySource.getName() + "#nonOptionArgs", nonOptionArgs);
                List<String> optionArgs = new ArrayList<>();
                commandLineArgs.getOptionNames().forEach(e -> optionArgs.add(e + "=" + String.join(",", Objects.requireNonNull(commandLineArgs.getOptionValues(e)))));
                map.put(propertySource.getName() + "#optionArgs", optionArgs);
            }
        }
        return map;
    }

    /**
     * 获取全参数（类遮蔽失效时使用）
     *
     * @param environment
     * @return
     */
    public static Map<String, List<String>> getAllProperties2(ConfigurableEnvironment environment) {
        Class<?> aClass = null;
        try {
            aClass = Class.forName("org.springframework.core.env.CommandLineArgs");
        } catch (ClassNotFoundException ignore) {
        }
        Map<String, List<String>> map = new LinkedHashMap<>();
        MutablePropertySources propertySources = environment.getPropertySources();
        for (PropertySource<?> propertySource : propertySources) {
            Object source = propertySource.getSource();
            if (source instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> sourceMap = (Map<Object, Object>) source;
                map.put(propertySource.getName(), sourceMap.entrySet()
                        .stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .sorted(Comparator.comparing(String::toString))
                        .toList());
            } else if (aClass != null && aClass.isInstance(source)) {
                try {
                    Method getNonOptionArgsMethod = aClass.getMethod("getNonOptionArgs");
                    @SuppressWarnings("unchecked")
                    List<String> nonOptionArgs = (List<String>) getNonOptionArgsMethod.invoke(source);
                    map.put(propertySource.getName() + "#nonOptionArgs", nonOptionArgs);
                    List<String> optionArgs = new ArrayList<>();
                    Method getOptionNamesMethod = aClass.getMethod("getOptionNames");
                    @SuppressWarnings("unchecked")
                    Set<String> optionNames = (Set<String>) getOptionNamesMethod.invoke(source);
                    Method getOptionValuesMethod = aClass.getMethod("getOptionValues", String.class);
                    for (String name : optionNames) {
                        @SuppressWarnings("unchecked")
                        List<String> values = (List<String>) getOptionValuesMethod.invoke(source, name);
                        if (values != null) {
                            optionArgs.add(name + "=" + String.join(",", values));
                        }
                    }
                    map.put(propertySource.getName() + "#optionArgs", optionArgs);
                } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException ignore) {
                }
            }
        }
        return map;
    }

    /**
     * 调整参数
     *
     * @param environment
     * @param adjustmentProperties
     * @return
     */
    public synchronized static Map<String, String> adjustmentProperties(ConfigurableEnvironment environment, Object adjustmentProperties) {
        MutablePropertySources sources = environment.getPropertySources();
        if (!sources.contains(SOURCE_NAME)) {
            sources.addFirst(new MapPropertySource(SOURCE_NAME, SOURCE_DATA));
        }
        if (adjustmentProperties instanceof Map map) {
            SOURCE_DATA.clear();
            if (!map.isEmpty()) {
                //noinspection unchecked
                SOURCE_DATA.putAll(map); // 会出现Refresh keys changed: []的情况，暂时不处理，如需解决需引入org.springframework.cloud相关类
            }
            new Thread(() -> {
                try {
                    ApplicationContext applicationContext = SpringUtil.getApplicationContext();
                    Class<?> aClass = Class.forName("org.springframework.cloud.endpoint.event.RefreshEvent");
                    Object refreshEvent = aClass.getDeclaredConstructor(Object.class, Object.class, String.class).newInstance(applicationContext, null, "debug4j config refresh");
                    applicationContext.publishEvent(refreshEvent);
                } catch (Exception e) {
                    log.warn("publishEvent failed with error:{}", e.getCause() + ":" + e.getMessage());
                }
            }).start();
        }
        return SOURCE_DATA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() == null ? "" : String.valueOf(e.getValue()), (a, b) -> b, LinkedHashMap::new));
    }
}

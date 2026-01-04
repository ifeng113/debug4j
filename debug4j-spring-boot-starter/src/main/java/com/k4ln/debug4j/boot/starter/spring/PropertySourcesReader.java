package com.k4ln.debug4j.boot.starter.spring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.CommandLineArgs;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.*;

@Slf4j
public class PropertySourcesReader {

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
            } else if (source instanceof CommandLineArgs commandLineArgs) {
                List<String> nonOptionArgs = commandLineArgs.getNonOptionArgs();
                map.put(propertySource.getName() + "#nonOptionArgs", nonOptionArgs);
                List<String> optionArgs = new ArrayList<>();
                commandLineArgs.getOptionNames().forEach(e -> {
                    optionArgs.add(String.join(",", Objects.requireNonNull(commandLineArgs.getOptionValues(e))));
                });
                map.put(propertySource.getName() + "#optionArgs", optionArgs);
            }
        }
        return map;
    }
}

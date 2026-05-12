package com.k4ln.debug4j.boot.starter;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.k4ln.debug4j.boot.starter.hook.ExtendedHookService;
import com.k4ln.debug4j.boot.starter.hook.PropertySourcesHandler;
import com.k4ln.debug4j.boot.starter.hook.SpringBeanHandler;
import com.k4ln.debug4j.common.daemon.Debug4jCommand;
import com.k4ln.debug4j.common.daemon.enums.ExtendedHookType;
import com.k4ln.debug4j.daemon.Debug4jDaemon;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.k4ln.debug4j.common.daemon.Debug4jCommand.loadDebug4jCommand;

@Slf4j
@ConditionalOnProperty(prefix = "debug4j", value = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(Debug4jProperties.class)
public class Debug4jAutoConfiguration {

    public static final String SPRING_APPLICATION_NAME = "spring.application.name";

    public Debug4jAutoConfiguration(Debug4jProperties debug4jProperties, ConfigurableEnvironment environment, ApplicationArguments args) {
        debug4jProperties.setApplication(StrUtil.isBlank(debug4jProperties.getApplication()) ? environment.getProperty(SPRING_APPLICATION_NAME) : debug4jProperties.getApplication());
        Debug4jCommand debug4jCommand = loadDebug4jCommand(args.getSourceArgs(), debug4jProperties.getReloadMode(), buildExtendedHook(environment));
        debug4jCommand.setReloadCloseHandler(h -> {
            ApplicationContext applicationContext = SpringUtil.getApplicationContext();
            if (applicationContext != null && ((AnnotationConfigServletWebServerApplicationContext) applicationContext).isActive()) {
                SpringApplication.exit(applicationContext, () -> 0);
            }
        });
        debug4jCommand.setReloadStartHandler(h -> SpringApplication.run(debug4jCommand.getCls(), h != null ? h.toArray(new String[0]) : args.getSourceArgs()));
        Debug4jDaemon.start(debug4jProperties.getProxy(), debug4jProperties.getApplication(), debug4jProperties.getPackageName(),
                debug4jProperties.getHost(), debug4jProperties.getPort(), debug4jProperties.getKey(), debug4jCommand, debug4jProperties.getDeveloper());
    }

    public static Map<ExtendedHookType, Function<Object, ?>> buildExtendedHook(ConfigurableEnvironment environment) {
        Map<ExtendedHookType, Function<Object, ?>> extendedHook = new HashMap<>();
        extendedHook.put(ExtendedHookType.HOOK_ARGS, (Function<Object, Map<String, List<String>>>) h -> PropertySourcesHandler.getAllProperties(environment));
        extendedHook.put(ExtendedHookType.HOOK_ARGS_ADJUSTMENT, (Function<Object, Map<String, String>>) h -> PropertySourcesHandler.adjustmentProperties(environment, h));
        extendedHook.put(ExtendedHookType.HOOK_OBJ_DISCOVERY, SpringBeanHandler::discovery);
        extendedHook.put(ExtendedHookType.HOOK_OBJ, SpringBeanHandler::getBean);
        customExtendedHook(extendedHook);
        return extendedHook;
    }

    private static void customExtendedHook(Map<ExtendedHookType, Function<Object, ?>> extendedHook) {
        try {
            SpringUtil.getBean(ExtendedHookService.class).adjustmentExtendedHook(extendedHook);
        } catch (Exception ignore) {
        }
    }
}

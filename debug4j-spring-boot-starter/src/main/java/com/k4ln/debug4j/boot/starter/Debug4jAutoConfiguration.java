package com.k4ln.debug4j.boot.starter;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.k4ln.debug4j.common.daemon.Debug4jCommand;
import com.k4ln.debug4j.daemon.Debug4jDaemon;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static com.k4ln.debug4j.common.daemon.Debug4jCommand.loadDebug4jCommand;

@Slf4j
@ConditionalOnProperty(prefix = "debug4j", value = "enable", matchIfMissing = true)
@EnableConfigurationProperties(Debug4jProperties.class)
public class Debug4jAutoConfiguration {

    public static final String SPRING_APPLICATION_NAME = "spring.application.name";

    public Debug4jAutoConfiguration(Debug4jProperties debug4jProperties, Environment environment, ApplicationArguments args) {
        String applicationName = environment.getProperty(SPRING_APPLICATION_NAME);
        if (StrUtil.isBlank(debug4jProperties.getApplication())) {
            debug4jProperties.setApplication(applicationName);
        }
        Debug4jCommand debug4jCommand = loadDebug4jCommand(args.getSourceArgs(), debug4jProperties.getReloadMode());
        debug4jCommand.setReloadCloseHandler(h -> {
            ApplicationContext applicationContext = SpringUtil.getApplicationContext();
            if (applicationContext != null && ((AnnotationConfigServletWebServerApplicationContext) applicationContext).isActive()) {
                SpringApplication.exit(applicationContext, () -> 0);
            }
        });
        debug4jCommand.setReloadStartHandler(h -> SpringApplication.run(debug4jCommand.getCls(), args.getSourceArgs()));
        if (StrUtil.isNotBlank(debug4jCommand.getRootUniqueId())) {
            debug4jProperties.setProxy(false);
        }
        Debug4jDaemon.start(debug4jProperties.getProxy(), debug4jProperties.getApplication(), debug4jProperties.getPackageName(),
                debug4jProperties.getHost(), debug4jProperties.getPort(), debug4jProperties.getKey(), debug4jCommand);
    }

}

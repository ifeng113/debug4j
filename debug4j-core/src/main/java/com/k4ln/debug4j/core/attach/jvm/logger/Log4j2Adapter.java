package com.k4ln.debug4j.core.attach.jvm.logger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import java.util.Comparator;
import java.util.List;

public class Log4j2Adapter {

    /**
     * 修改等级
     *
     * @param name
     * @param level
     */
    static void set(String name, LoggerOperator.Level level) {
        org.apache.logging.log4j.core.config.Configurator.setLevel(name, org.apache.logging.log4j.Level.valueOf(level.name()));
    }

    /**
     * 获取日志等级
     *
     * @return
     */
    static List<LoggerInfo> dump() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        var config = ctx.getConfiguration();
        return config.getLoggers().values().stream().map(l ->
                new LoggerInfo(
                        l.getName(),
                        l.getLevel() == null ? "INHERITED" : l.getLevel().name(),
                        config.getLoggerConfig(l.getName()).getLevel().name()
                )
        ).sorted(Comparator.comparing(LoggerInfo::getName)).toList();
    }
}

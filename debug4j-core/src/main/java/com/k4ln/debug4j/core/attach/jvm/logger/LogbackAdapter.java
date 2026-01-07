package com.k4ln.debug4j.core.attach.jvm.logger;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

public class LogbackAdapter {

    /**
     * 修改等级
     *
     * @param name
     * @param level
     */
    static void set(String name, LoggerOperator.Level level) {
        Logger logger = (Logger) LoggerFactory.getLogger(name);
        logger.setLevel(ch.qos.logback.classic.Level.valueOf(level.name()));
    }

    /**
     * 获取日志等级
     *
     * @return
     */
    static List<LoggerInfo> dump() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        return ctx.getLoggerList().stream().map(log ->
                new LoggerInfo(
                        log.getName(),
                        log.getLevel() == null ? "INHERITED" : log.getLevel().toString(),
                        log.getEffectiveLevel().toString()
                )
        ).sorted(Comparator.comparing(LoggerInfo::getName)).toList();
    }
}

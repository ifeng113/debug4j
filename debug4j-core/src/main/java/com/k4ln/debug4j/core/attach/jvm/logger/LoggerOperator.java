package com.k4ln.debug4j.core.attach.jvm.logger;

import java.util.List;

public class LoggerOperator {

    public enum Level {TRACE, DEBUG, INFO, WARN, ERROR, OFF}

    /**
     * 设置日志等级
     *
     * @param logger
     * @param level
     */
    public static void setLevel(String logger, Level level) {
        String impl = detectLoggerImpl();
        switch (impl) {
            case "LOGBACK" -> LogbackAdapter.set(logger, level);
            case "LOG4J2" -> Log4j2Adapter.set(logger, level);
            default -> throw new IllegalStateException("Unknown logging impl: " + impl);
        }
    }

    /**
     * 获取日志等级
     *
     * @return
     */
    public static List<LoggerInfo> dump() {
        String impl = detectLoggerImpl();
        return switch (impl) {
            case "LOGBACK" -> LogbackAdapter.dump();
            case "LOG4J2" -> Log4j2Adapter.dump();
            default -> List.of();
        };
    }

    private static String detectLoggerImpl() {
        try {
            Class.forName("ch.qos.logback.classic.Logger");
            return "LOGBACK";
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("org.apache.logging.log4j.core.Logger");
            return "LOG4J2";
        } catch (ClassNotFoundException ignored) {
        }
        return "UNKNOWN";
    }
}

package com.k4ln.debug4j.boot;

import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.common.daemon.Debug4jArgs;
import com.k4ln.debug4j.common.daemon.Debug4jMode;
import com.k4ln.debug4j.core.Debugger;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class Debug4jBoot {

    public static final int LOG_FREQUENCY = 30;

    public static final int LOG_INTERVAL = 10000;

    /**
     * 启动函数
     *
     * @param args
     */
    public static void main(String[] args) {
        log.info("args:{}", JSON.toJSONString(args));
        String arg = args[0];
        if (StrUtil.isNotBlank(arg)) {
            Debug4jArgs debug4jArgs = recoverDebugArgs(arg);
            if (debug4jArgs != null) {
                RuntimeUtil.addShutdownHook(() -> log.error("Debug4j Boot destroyed"));
                checkAppProcess(debug4jArgs);
            } else {
                log.error("Debug4j Boot shutdown with incomplete args");
            }
        } else {
            log.error("Debug4j Boot shutdown with empty args");
        }
    }

    /**
     * 装载参数
     *
     * @param arg
     * @return
     */
    private static Debug4jArgs recoverDebugArgs(String arg) {
        Debug4jArgs debug4jArgs = new Debug4jArgs();
        String[] parameters = arg.split(",");
        for (String parameter : parameters) {
            String[] entry = parameter.split("=");
            if (entry.length == 2) {
                if (entry[0].equals("pid")) {
                    debug4jArgs.setPid(Long.parseLong(entry[1]));
                } else if (entry[0].equals("jdwpPort") && !"null".equals(entry[1])) {
                    debug4jArgs.setJdwpPort(Integer.parseInt(entry[1]));
                } else if (entry[0].equals("application")) {
                    debug4jArgs.setApplication(entry[1].replace("'", ""));
                } else if (entry[0].equals("packageName")) {
                    debug4jArgs.setPackageName(entry[1].replace("'", ""));
                } else if (entry[0].equals("uniqueId")) {
                    debug4jArgs.setUniqueId(entry[1].replace("'", ""));
                } else if (entry[0].equals("host")) {
                    debug4jArgs.setHost(entry[1].replace("'", ""));
                } else if (entry[0].equals("port") && !"null".equals(entry[1])) {
                    debug4jArgs.setPort(Integer.parseInt(entry[1]));
                } else if (entry[0].equals("key")) {
                    debug4jArgs.setKey(entry[1].replace("'", ""));
                }
            }
        }
        if (debug4jArgs.getPid() != null && StrUtil.isNotBlank(debug4jArgs.getHost())
                && debug4jArgs.getPort() != null && StrUtil.isNotBlank(debug4jArgs.getKey())) {
            log.info("recoverDebugArgs debug4jArgs:{}", debug4jArgs);
            return debug4jArgs;
        } else {
            return null;
        }
    }

    /**
     * 开启（代理）调试器
     *
     * @param debug4jArgs
     */
    private static void checkAppProcess(Debug4jArgs debug4jArgs) {
        if (debug4jArgs.getPid() == null) {
            log.error("Debug4j Boot shutdown with empty pid");
            return;
        }

        Debugger.start(debug4jArgs.getApplication(), debug4jArgs.getUniqueId(), debug4jArgs.getPackageName(),
                debug4jArgs.getHost(), debug4jArgs.getPort(), debug4jArgs.getKey(), debug4jArgs.getPid(),
                debug4jArgs.getJdwpPort(), Debug4jMode.process);

        boolean bootRun = true;
        int times = 0;
        while (bootRun) {
            try {
                boolean isAlive = ProcessHandle.of(debug4jArgs.getPid())
                        .map(ProcessHandle::isAlive)
                        .orElse(false);
                if ((times % LOG_FREQUENCY) == 0) {
                    log.info("checkAppProcess pid:{} appProcessId:{} isAlive:{}", ProcessHandle.current().pid(), debug4jArgs.getPid(), isAlive);
                }

                times++;
                if (times >= Integer.MAX_VALUE - 1) {
                    times = 0;
                }
                if (!isAlive) {
                    bootRun = false;
                } else {
                    try {
                        Thread.sleep(LOG_INTERVAL);
                    } catch (Exception e) {
                        log.info("checkAppProcess sleep error:{}", e.getMessage());
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                log.error("checkAppProcess while error:{}", e.getMessage());
                e.printStackTrace();
            }
        }

        Debugger.shutdown();

        log.info("checkAppProcess break with Debug4j Boot shutdown");

        System.exit(0);
    }


}

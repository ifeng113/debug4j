package com.k4ln.demo;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.k4ln.debug4j.common.daemon.Debug4jCommand;
import com.k4ln.debug4j.daemon.Debug4jDaemon;
import lombok.extern.slf4j.Slf4j;

import static com.k4ln.debug4j.common.daemon.Debug4jCommand.loadDebug4jCommand;

@Slf4j
public class Demo1DaemonMain {

    public static boolean stop = false;

    public static void main(String[] args) {

//        Debug4jDaemon.start(true, "demo1-daemon", "com.k4ln","127.0.0.1", 7988, "k4ln");

        Debug4jCommand debug4jCommand = loadDebug4jCommand(args, Debug4jCommand.ReloadMode.Restart);
        debug4jCommand.setReloadCloseHandler(h -> {
            close();
        });
        boolean proxyMode = !StrUtil.isNotBlank(debug4jCommand.getRootUniqueId());
        Debug4jDaemon.start(proxyMode, "demo1-daemon", "com.k4ln","127.0.0.1", 7988, "k4ln", debug4jCommand);

        start();
    }

    private static void start() {
        stop = false;
        for (int i = 0; i < 1000; i++) {
            if (stop) {
                return;
            }
            logNumber(i);
            if (i == 999){
                i = 0;
            }
        }
    }

    private static void close() {
        stop = true;
    }

    private static void logNumber(int i) {
        try {
            Dog dog = Dog.builder().name(RandomUtil.randomNumbers(4)).age(i).build();
            Thread.sleep(5000);
            log.info("random tid:{} pid:{} index:{} dog:{}", Thread.currentThread().getId(), ProcessHandle.current().pid(), i, dog.toString());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
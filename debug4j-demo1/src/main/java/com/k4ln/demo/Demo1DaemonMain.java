package com.k4ln.demo;

import cn.hutool.core.util.RandomUtil;
import com.k4ln.debug4j.common.daemon.Debug4jCommand;
import com.k4ln.debug4j.common.daemon.enums.ReloadMode;
import com.k4ln.debug4j.daemon.Debug4jDaemon;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.k4ln.debug4j.common.daemon.Debug4jCommand.loadDebug4jCommand;

@Slf4j
public class Demo1DaemonMain {

    public static boolean stop = false;

    public static void main(String[] args) {

//        Debug4jDaemon.start(true, "demo1-daemon", "com.k4ln","127.0.0.1", 7988, "k4ln", null, true);

        Debug4jCommand debug4jCommand = loadDebug4jCommand(args, ReloadMode.Restart);
        debug4jCommand.setReloadCloseHandler(h -> close());
        Debug4jDaemon.start(true, "demo1-daemon", "com.k4ln", "127.0.0.1", 7988, "k4ln", debug4jCommand, true);

        start();
    }

    private static void start() {
        stop = false;
        for (int i = 0; i < 1000; i++) {
            if (stop) {
                return;
            }
            logNumber(i);
            if (i == 999) {
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
            Demo1Dto w1 = Demo1Dto.builder().key(i + "").build();
            log.info(w1.getKey());
            log.info("random tid:{} pid:{} index:{} dog:{}", Thread.currentThread().getId(), ProcessHandle.current().pid(), i, dog.toString());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Demo1Dto {

        @Builder.Default
        String key = "777";
    }

}
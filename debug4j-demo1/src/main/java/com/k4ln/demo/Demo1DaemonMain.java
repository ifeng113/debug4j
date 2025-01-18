package com.k4ln.demo;

import cn.hutool.core.util.RandomUtil;
import com.k4ln.debug4j.daemon.Debug4jDaemon;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Demo1DaemonMain {

    public static void main(String[] args) {

        Debug4jDaemon.start(true, "demo1-daemon", "com.k4ln","192.168.1.164", 7988, "k4ln");

        for (int i = 0; i < 1000; i++) {
            logNumber(i);
            if (i == 999){
                i = 0;
            }
        }
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
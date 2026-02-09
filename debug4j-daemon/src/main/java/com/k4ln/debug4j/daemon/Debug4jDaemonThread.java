package com.k4ln.debug4j.daemon;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.ZipUtil;
import com.k4ln.debug4j.common.daemon.Debug4jArgs;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static com.k4ln.debug4j.common.utils.FileUtils.createTempDir;
import static com.k4ln.debug4j.common.utils.SystemUtils.exec;

@Slf4j
public class Debug4jDaemonThread implements Runnable {

    private final Debug4jArgs debug4jArgs;

    private final boolean developer;

    @Getter
    private Process process;

    public Debug4jDaemonThread(Debug4jArgs debug4jArgs, boolean developer) {
        this.debug4jArgs = debug4jArgs;
        this.developer = developer;
    }

    @Override
    public void run() {
        log.info("Daemon thread start pid:{} by:{}", debug4jArgs.getPid(), debug4jArgs.getThreadName());
        URL bootUrl = this.getClass().getClassLoader().getResource("debug4j-boot.zip");
        if (bootUrl != null) {
            try {
                File tempDebug4jDir = createTempDir();
                ZipUtil.unzip(bootUrl.openStream(), tempDebug4jDir, CharsetUtil.CHARSET_UTF_8);
                File debug4jBootJarFile = new File(tempDebug4jDir, "debug4j-boot.jar");
                if (!debug4jBootJarFile.exists()) {
                    throw new IllegalStateException("can not find debug4j-boot.jar under tempDebug4jDir: " + tempDebug4jDir);
                }
                if (developer) {
                    Integer bootJdwpPort = NetUtil.getUsableLocalPort();
                    log.info("Debug4j Boot jdwp transport dt_socket at address: {}", bootJdwpPort);
                    process = exec("java", "-Dfile.encoding=UTF-8", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:" + bootJdwpPort, "-jar", debug4jBootJarFile.getAbsolutePath(), debug4jArgs.toString());
                } else {
                    process = exec("java", "-Dfile.encoding=UTF-8", "-jar", debug4jBootJarFile.getAbsolutePath(), debug4jArgs.toString());
                }
                log.info("Debug4j Boot start with pid:{}", process.pid());
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalArgumentException("can not getResources debug4j-boot.zip");
        }
    }

}

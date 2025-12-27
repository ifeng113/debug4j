package com.k4ln.debug4j.daemon;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.*;
import com.k4ln.debug4j.common.daemon.Debug4jArgs;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static com.k4ln.debug4j.common.utils.FileUtils.createTempDir;

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

    /**
     * 执行命令
     *
     * @param cmds
     * @return
     */
    public static Process exec(String... cmds) {
        Process process;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(handleCmds(cmds)).redirectErrorStream(true);
            // 避免子进程阻塞，支持日志回显【更改logback.xml的配置：<discardingThreshold>100</discardingThreshold>】
            processBuilder.inheritIO();
            process = processBuilder.start();
        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
        return process;
    }

    /**
     * 处理命令
     *
     * @param cmds
     * @return
     */
    private static String[] handleCmds(String... cmds) {
        if (ArrayUtil.isEmpty(cmds)) {
            throw new NullPointerException("Command is empty !");
        }

        // 单条命令的情况
        if (1 == cmds.length) {
            final String cmd = cmds[0];
            if (StrUtil.isBlank(cmd)) {
                throw new NullPointerException("Command is blank !");
            }
            cmds = cmdSplit(cmd);
        }
        return cmds;
    }

    /**
     * 命令拆分
     *
     * @param cmd
     * @return
     */
    private static String[] cmdSplit(String cmd) {
        final List<String> cmds = new ArrayList<>();

        final int length = cmd.length();
        final Stack<Character> stack = new Stack<>();
        boolean inWrap = false;
        final StrBuilder cache = StrUtil.strBuilder();

        char c;
        for (int i = 0; i < length; i++) {
            c = cmd.charAt(i);
            switch (c) {
                case CharUtil.SINGLE_QUOTE:
                case CharUtil.DOUBLE_QUOTES:
                    if (inWrap) {
                        if (c == stack.peek()) {
                            //结束包装
                            stack.pop();
                            inWrap = false;
                        }
                        cache.append(c);
                    } else {
                        stack.push(c);
                        cache.append(c);
                        inWrap = true;
                    }
                    break;
                case CharUtil.SPACE:
                    if (inWrap) {
                        // 处于包装内
                        cache.append(c);
                    } else {
                        cmds.add(cache.toString());
                        cache.reset();
                    }
                    break;
                default:
                    cache.append(c);
                    break;
            }
        }

        if (cache.hasContent()) {
            cmds.add(cache.toString());
        }

        return cmds.toArray(new String[0]);
    }

}

package com.k4ln.debug4j.core.attach.jvm.logger;

import cn.hutool.core.codec.Base64Decoder;
import cn.hutool.core.codec.Base64Encoder;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogReplayHandler {

    /**
     * 日志队列
     */
    public static final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>(10000);

    /**
     * 日志接收器
     */
    public static final Consumer<String> lineConsumer = line -> logQueue.add(line.replaceAll("\u001B\\[[;\\d]*m", ""));

    /**
     * 日志重放映射
     */
    public static final Map<LogReplayInfo, LogReplayFileWriter> replayWriters = new ConcurrentHashMap<>();

    /**
     * 日志重放处理线程
     */
    public static final Thread logReplayThread = new Thread(() -> {
        List<String> lines = new ArrayList<>(100);
        while (true) {
            try {
                lines.clear();
                String first = logQueue.take();
                lines.add(first);
                logQueue.drainTo(lines, 99);
                for (Map.Entry<LogReplayInfo, LogReplayFileWriter> replayWriter : replayWriters.entrySet()) {
                    if (replayWriter.getValue() != null) {
                        List<String> matchLines = new ArrayList<>(100);
                        try {
                            if (replayWriter.getKey().getMatchType().equals(LogReplayInfo.MatchType.ALL)) {
                                replayWriter.getValue().writeLines(lines);
                            } else if (replayWriter.getKey().getMatchType().equals(LogReplayInfo.MatchType.CONTAIN)) {
                                for (String line : lines) {
                                    if (line.contains(replayWriter.getKey().getMatchString())) {
                                        matchLines.add(line);
                                    }
                                }
                            } else if (replayWriter.getKey().getMatchType().equals(LogReplayInfo.MatchType.REGEX)) {
                                for (String line : lines) {
                                    replayWriter.getKey().getMatcher().reset(line);
                                    if (replayWriter.getKey().getMatcher().find()) {
                                        matchLines.add(line);
                                    }
                                }
                            }
                            if (!matchLines.isEmpty()) {
                                replayWriter.getValue().writeLines(matchLines);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    });

    /**
     * 查询/订阅重放日志
     * 当前仅实现对 System.out 内容进行重放（裁剪），不考虑对 System.out 内容进行修改或增强（如：traceId等）
     * 此功能设计初衷是：对标准日志输出（文本内容）结果进行过滤或重放，并无意修改任何原始日志内容与原始日志处理逻辑
     *
     * @param adjustmentContent
     * @return
     */
    public static void replay(Map<String, String> adjustmentContent) {
        String logReplayInfoStr = adjustmentContent.get("logReplayInfo");
        if (StrUtil.isNotBlank(logReplayInfoStr)) {
            LogReplayInfo logReplayInfo = JSON.parseObject(adjustmentContent.get("logReplayInfo"), LogReplayInfo.class);
            if (logReplayInfoCheck(logReplayInfo)) {
                if (!logReplayThread.isAlive()) {
                    logReplayThread.start();
                    LogReplayOutputStream replayOutputStream = new LogReplayOutputStream(System.out, lineConsumer);
                    System.setOut(new PrintStream(replayOutputStream, true));
                    replayListener(LogReplayInfo.builder()
                            .matchType(LogReplayInfo.MatchType.ALL)
                            .build());
                }
                replayListener(logReplayInfo);
            }
        }
    }

    /**
     * 重放记录检查
     *
     * @param logReplayInfo
     * @return
     */
    private static boolean logReplayInfoCheck(LogReplayInfo logReplayInfo) {
        if (logReplayInfo != null && !logReplayInfo.getMatchType().equals(LogReplayInfo.MatchType.ALL)) {
            if (logReplayInfo.getOperationType().equals(LogReplayInfo.OperationType.ADD)) {
                if (StrUtil.isNotBlank(logReplayInfo.getMatchString())) {
                    if (logReplayInfo.getMatchType().equals(LogReplayInfo.MatchType.REGEX)) {
                        return replayWriters.keySet().stream().noneMatch(e -> e.getMatchType().equals(logReplayInfo.getMatchType()) && Base64Encoder.encode(e.getMatchString()).equals(logReplayInfo.getMatchString()));
                    } else if (logReplayInfo.getMatchType().equals(LogReplayInfo.MatchType.CONTAIN)) {
                        return replayWriters.keySet().stream().noneMatch(e -> e.getMatchType().equals(logReplayInfo.getMatchType()) && e.getMatchString().equals(logReplayInfo.getMatchString()));
                    }
                }
            } else {
                return StrUtil.isNotBlank(logReplayInfo.getLogFileName());
            }
        }
        return false;
    }

    /**
     * 日志重放监听器
     *
     * @param logReplayInfo
     */
    private synchronized static void replayListener(LogReplayInfo logReplayInfo) {
        if (logReplayInfo.getOperationType().equals(LogReplayInfo.OperationType.ADD)) {
            try {
                if (logReplayInfo.getMatchType().equals(LogReplayInfo.MatchType.REGEX)) {
                    String realMatchString = Base64Decoder.decodeStr(logReplayInfo.getMatchString());
                    logReplayInfo.setMatchString(realMatchString);
                    Pattern pattern = Pattern.compile(realMatchString);
                    Matcher matcher = pattern.matcher("");
                    logReplayInfo.setMatcher(matcher);
                }
                String logFileName = "debug4j-log-replay-" + System.currentTimeMillis() + ".log";
                logReplayInfo.setLogFileName(logFileName);
                replayWriters.put(logReplayInfo, new LogReplayFileWriter(Path.of(logFileName)));
                Thread.sleep(1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            List<LogReplayInfo> closeLogReplayInfo = replayWriters.keySet().stream().filter(e -> logReplayInfo.getLogFileName().equals(e.getLogFileName())).toList();
            for (LogReplayInfo replayInfo : closeLogReplayInfo) {
                try {
                    LogReplayFileWriter logReplayFileWriter = replayWriters.get(replayInfo);
                    logReplayFileWriter.close();
                    replayWriters.remove(replayInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (replayWriters.size() == 1) {
                Optional<LogReplayInfo> any = replayWriters.keySet().stream().findAny();
                LogReplayInfo matchAllLogReplayInfo = any.get();
                try {
                    replayWriters.get(matchAllLogReplayInfo).close();
                    replayWriters.remove(matchAllLogReplayInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 获取重放配置
     *
     * @return
     */
    public static JSONObject getReplayInfo() {
        List<LogReplayInfo> sortedKeys = new ArrayList<>(replayWriters.keySet());
        sortedKeys.sort(Comparator.comparing(LogReplayInfo::getLogFileName));
        return JSONObject.of("logReplayInfos", sortedKeys);
    }
}

package com.k4ln.debug4j.core.attach.jvm;

import cn.hutool.core.codec.Base64Decoder;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.k4ln.debug4j.common.daemon.Debug4jCommand;
import com.k4ln.debug4j.common.daemon.enums.ExtendedHookType;
import com.k4ln.debug4j.common.daemon.enums.ReloadMode;
import com.k4ln.debug4j.common.protocol.command.message.CommandProcessAdjustmentReqMessage;
import com.k4ln.debug4j.common.protocol.command.message.CommandProcessReqMessage;
import com.k4ln.debug4j.common.utils.StringUtils;
import com.k4ln.debug4j.common.utils.SystemUtils;
import com.k4ln.debug4j.core.Debugger;
import com.k4ln.debug4j.core.attach.Debug4jAttachOperator;
import com.k4ln.debug4j.core.attach.dto.*;
import com.k4ln.debug4j.core.attach.jvm.install.JarResourceExtractor;
import com.k4ln.debug4j.core.attach.jvm.logger.LogReplayHandler;
import com.k4ln.debug4j.core.attach.jvm.logger.LogReplayInfo;
import com.k4ln.debug4j.core.attach.jvm.logger.LoggerInfo;
import com.k4ln.debug4j.core.attach.jvm.logger.LoggerOperator;
import com.k4ln.debug4j.core.attach.jvm.trace.Debug4jTraceInstaller;
import com.sun.management.HotSpotDiagnosticMXBean;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.util.OsUtils;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.RoundingMode;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static com.k4ln.debug4j.common.utils.SocketProtocolUtil.*;
import static com.k4ln.debug4j.common.utils.SystemUtils.getClassName;
import static com.k4ln.debug4j.core.client.SocketClient.callbackFileMessage;

@Slf4j
public class Debug4jProcessOperator {

    /**
     * 新建进程重载模式
     */
    public static ProcessBuilder processBuilder = null;

    public static Process process = null;

    /**
     * SFTP服务
     */
    private static SshServer sshd = null;

    /**
     * SSH安装进程
     */
    private static Process sshInstallProcess = null;

    /**
     * JFR
     * 为什么使用Object：通过类隔离防止 JVM 在加载 Debug4jProcessOperator 类时（初始化 recording 变量时）无法解析 jdk.jfr.Recording，导致启动报错
     * import jdk.jfr.Recording 不会导致异常，JVM 不关心 import
     * private static jdk.jfr.Recording field 会导致异常，类加载阶段解析类型
     */
    private static Object recording = null;

    /**
     * 进程重载
     * jdwp:    -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
     * gc:      -Xlog:gc*,gc+age=trace,gc+heap=info:file=debug4j-gc.log:time,level,tags
     * jmx:     -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=33010 -Dcom.sun.management.jmxremote.rmi.port=33010 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=122.152.214.33
     *
     * @param processReq
     * @return
     */
    public static ProcessArgsInfo reload(CommandProcessReqMessage processReq) {
        if (StrUtil.isBlank(Debugger.getDebug4jCommand().getRootUniqueId())) {
            if (Debugger.getDebug4jCommand().getReloadMode().equals(ReloadMode.Reload)) {
                restartCurrentProcess(processReq);
                return getProcessArgsInfo();
            } else if (Debugger.getDebug4jCommand().getReloadMode().equals(ReloadMode.Restart)) {
                restartChildProcess(processReq);
            }
        }
        return ProcessArgsInfo.builder().build();
    }

    /**
     * 获取进程参数
     *
     * @return
     */
    public static ProcessArgsInfo getProcessArgsInfo() {
        Debug4jCommand debug4jCommand = Debugger.getDebug4jCommand();
        Map<String, List<String>> hookArgs = new LinkedHashMap<>();
        if (debug4jCommand.getExtendedHook() != null && debug4jCommand.getExtendedHook().get(ExtendedHookType.HOOK_ARGS) != null) {
            Object apply = debug4jCommand.getExtendedHook().get(ExtendedHookType.HOOK_ARGS).apply(null);
            if (apply instanceof LinkedHashMap) {
                //noinspection unchecked
                hookArgs = (LinkedHashMap<String, List<String>>) apply;
            }
        }
        return ProcessArgsInfo.builder()
                .jvmArgs(ManagementFactory.getRuntimeMXBean().getInputArguments()) // 保持原始顺序
                .programArgs(debug4jCommand.getOriginalArgs()) // 保持原始顺序
                .properties(System.getProperties()
                        .entrySet()
                        .stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .toList()
                        .stream()
                        .sorted()
                        .toList())
                .envs(System.getenv()
                        .entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .toList())
                .hookArgs(hookArgs)
                .build();
    }

    /**
     * 当前进程重载模式
     *
     * @param processReq
     */
    public static synchronized void restartCurrentProcess(CommandProcessReqMessage processReq) {
        Debugger.getDebug4jCommand().getReloadCloseHandler().accept(null);
        ProcessArgsInfo processArgsInfo = getProcessArgsInfo();
        processReq.getRemoveProperties().forEach(e -> {
            if (e.split("=").length == 2) {
                System.clearProperty(e.split("=")[0]);
            }
        });
        processReq.getAddProperties().forEach(e -> {
            if (e.split("=").length == 2) {
                System.setProperty(e.split("=")[0], e.split("=")[1]);
            }
        });
        processArgsInfo.getProgramArgs().removeAll(processReq.getRemoveProgramArgs());
        processArgsInfo.getProgramArgs().addAll(processReq.getAddProgramArgs());
        Debugger.getDebug4jCommand().getReloadStartHandler().accept(processArgsInfo.getProgramArgs());
    }

    /**
     * 新建子进程模式
     *
     * @param processReq
     */
    public static synchronized void restartChildProcess(CommandProcessReqMessage processReq) {
        try {
            if (processBuilder == null) {
                Debugger.getDebug4jCommand().getReloadCloseHandler().accept(null);
            }
            if (process != null && process.isAlive()) {
                process.destroy();
            }
            processBuilder = new ProcessBuilder(getRestartCommand(processReq));
            processBuilder.redirectErrorStream(true);
            processBuilder.inheritIO();
            Map<String, String> environment = new HashMap<>();
            processReq.getCoverEnvs().forEach(e -> {
                if (e.split("=").length == 2) {
                    environment.put(e.split("=")[0], e.split("=")[1]);
                }
            });
            List<String> environmentList = environment.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .toList();
            log.info("coverEnvs: {}", JSON.toJSONString(environmentList));
            processBuilder.environment().putAll(environment);
            process = processBuilder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取重启指令
     *
     * @return
     */
    private static List<String> getRestartCommand(CommandProcessReqMessage processReqMessage) {
        List<String> command = new ArrayList<>();
        String javaBin = ProcessHandle.current().info().command()
                .orElseGet(() -> System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        command.add(javaBin);
        List<String> originalJvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        if (originalJvmArgs != null && !originalJvmArgs.isEmpty()) {
            log.info("originalJvmArgs: {}", JSON.toJSONString(originalJvmArgs));
            List<String> newJvmArgs = new ArrayList<>(originalJvmArgs.stream()
                    .filter(e -> !processReqMessage.getRemoveJvmArgs().contains(e))
                    .filter(e -> !e.startsWith("-agentlib:jdwp")).toList());
            Optional<String> any = originalJvmArgs.stream()
                    .filter(e -> !processReqMessage.getRemoveJvmArgs().contains(e))
                    .filter(e -> e.startsWith("-agentlib:jdwp")).findAny();
            if (any.isPresent()) {
                String port = StringUtils.extractPort(any.get());
                if (StrUtil.isNotBlank(port)) {
                    String newJdwpArg = any.get().replace(port, String.valueOf(NetUtil.getUsableLocalPort()));
                    newJdwpArg = newJdwpArg.replace("server=n", "server=y");
                    newJdwpArg = newJdwpArg.replace("suspend=y", "suspend=n");
                    newJvmArgs.add(0, newJdwpArg);
                }
            }
            newJvmArgs.addAll(processReqMessage.getAddJvmArgs());
            log.info("newJvmArgs: {}", JSON.toJSONString(newJvmArgs));
            newJvmArgs.removeAll(processReqMessage.getRemoveProperties());
            newJvmArgs.addAll(processReqMessage.getAddProperties());
            log.info("newJvmArgs with properties: {}", JSON.toJSONString(newJvmArgs));
            command.addAll(newJvmArgs);
        }
        if (StrUtil.isNotBlank(Debugger.getDebug4jCommand().getJarPath())) {
            command.add("-jar");
            command.add(Debugger.getDebug4jCommand().getJarPath());
        } else {
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(Debugger.getDebug4jCommand().getCls().getName());
        }
        if (Debugger.getDebug4jCommand().getOriginalArgs() != null && !Debugger.getDebug4jCommand().getOriginalArgs().isEmpty()) {
            log.info("originalProgramArgs: {}", JSON.toJSONString(Debugger.getDebug4jCommand().getOriginalArgs()));
            List<String> newProgramArgs = new ArrayList<>(Debugger.getDebug4jCommand().getOriginalArgs());
            newProgramArgs.removeAll(processReqMessage.getRemoveProgramArgs());
            newProgramArgs.addAll(processReqMessage.getAddProgramArgs());
            log.info("newProgramArgs: {}", JSON.toJSONString(Debugger.getDebug4jCommand().getOriginalArgs()));
            command.addAll(newProgramArgs);
        }
        String rootUniqueId = StrUtil.isNotBlank(Debugger.getDebug4jCommand().getRootUniqueId()) ?
                Debugger.getDebug4jCommand().getRootUniqueId() : Debugger.getCommandInfoMessage().getUniqueId();
        command.add("--debug4j-root-uniqueId=" + rootUniqueId);
        log.info("newCommand:{}", JSON.toJSONString(command));
        return command;
    }

    /**
     * 进程调整
     *
     * @param adjustmentReqMessage
     * @return
     */
    public static ProcessAdjustmentInfo adjustment(CommandProcessAdjustmentReqMessage adjustmentReqMessage) {
        switch (adjustmentReqMessage.getAdjustmentType()) {
            case log -> {
                Map<String, String> adjustmentContent = adjustmentReqMessage.getAdjustmentContent();
                for (String key : adjustmentContent.keySet()) {
                    LoggerOperator.setLevel(key, LoggerOperator.Level.valueOf(adjustmentContent.get(key)));
                }
                Map<String, String> adjustmentResult = LoggerOperator.dump().stream()
                        .sorted(Comparator.comparing(LoggerInfo::getName))
                        .collect(Collectors.toMap(LoggerInfo::getName, LoggerInfo::toString, (a, b) -> a, LinkedHashMap::new));
                return ProcessAdjustmentInfo.builder().adjustmentResult(adjustmentResult).build();
            }
            case log_replay -> {
                LogReplayHandler.replay(adjustmentReqMessage.getAdjustmentContent());
                return ProcessAdjustmentInfo.builder()
                        .adjustmentExtendResult(LogReplayHandler.getReplayInfo())
                        .build();
            }
            case property -> {
                Map<String, String> adjustmentContent = adjustmentReqMessage.getAdjustmentContent();
                for (String key : adjustmentContent.keySet()) {
                    System.setProperty(key, adjustmentContent.get(key));
                }
                Properties props = System.getProperties();
                return ProcessAdjustmentInfo.builder().adjustmentResult(props.stringPropertyNames()
                        .stream()
                        .sorted()
                        .collect(Collectors.toMap(k -> k, props::getProperty, (a, b) -> b, LinkedHashMap::new))).build();
            }
            case property_hook -> {
                Debug4jCommand debug4jCommand = Debugger.getDebug4jCommand();
                if (debug4jCommand.getExtendedHook() != null && debug4jCommand.getExtendedHook().get(ExtendedHookType.HOOK_ARGS_ADJUSTMENT) != null) {
                    Object apply = debug4jCommand.getExtendedHook().get(ExtendedHookType.HOOK_ARGS_ADJUSTMENT).apply(adjustmentReqMessage.getAdjustmentContent());
                    if (apply instanceof LinkedHashMap) {
                        //noinspection unchecked
                        return ProcessAdjustmentInfo.builder().adjustmentResult((LinkedHashMap) apply).build();
                    }
                }
            }
            case sftp_open -> {
                int port = sshd != null ? sshd.getPort() : 0;
                Map<String, String> adjustmentContent = adjustmentReqMessage.getAdjustmentContent();
                String username = adjustmentContent.get("username");
                String password = adjustmentContent.get("password");
                port = openSftpServer(StrUtil.isBlank(username) ? "root" : username, StrUtil.isBlank(password) ? "123456" : password, port);
                return ProcessAdjustmentInfo.builder().adjustmentResult(Map.of("sftpPort", String.valueOf(port))).build();
            }
            case sftp_close -> {
                int port = closeSftpServer();
                return ProcessAdjustmentInfo.builder().adjustmentResult(Map.of("sftpPort", String.valueOf(port))).build();
            }
            case jvm_heap -> {
                try {
                    HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
                    String heapPath = "debug4j-" + System.currentTimeMillis() + ".hprof";
                    bean.dumpHeap(heapPath, true);
                    Path path = Path.of(heapPath);
                    return ProcessAdjustmentInfo.builder().adjustmentResult(Map.of("heapPath", path.toAbsolutePath().toString())).build();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            case jvm_jfr_start -> {
                try {
                    Map<String, String> adjustmentResult = Map.of("jdk.jfr.Recording", "Not supported");
                    if (recording == null) {
                        try {
                            Class.forName("jdk.jfr.Recording");
                            recording = new Recording(Configuration.getConfiguration("profile"));
                            Recording recordingPoint = (Recording) recording;
                            recordingPoint.setMaxAge(Duration.ofHours(1));  // 1小时
                            recordingPoint.setMaxSize(200 * 1024 * 1024L);  // 200M
                            recordingPoint.start();
                            adjustmentResult = recordingPoint.getSettings();
                        } catch (Exception ignore) {
                        }
                    } else {
                        Recording recordingPoint = (Recording) recording;
                        adjustmentResult = recordingPoint.getSettings();
                    }
                    return ProcessAdjustmentInfo.builder().adjustmentResult(adjustmentResult).build();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            case jvm_jfr_end -> {
                Map<String, String> adjustmentResult = Map.of("jdk.jfr.Recording", "Not started");
                if (recording != null) {
                    try {
                        Recording recordingPoint = (Recording) recording;
                        String jfrPath = "debug4j-" + System.currentTimeMillis() + ".jfr";
                        Path path = Path.of(jfrPath);
                        recordingPoint.stop();
                        recordingPoint.dump(path);
                        recordingPoint.close();
                        recording = null;
                        adjustmentResult = Map.of("jfrPath", path.toAbsolutePath().toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return ProcessAdjustmentInfo.builder().adjustmentResult(adjustmentResult).build();
            }
            case jvm_list -> {
                return listFiles("", p -> p.startsWith("debug4j") && (p.endsWith("hprof") || p.endsWith("jfr")), false, false);
            }
            case file_list -> {
                Map<String, String> adjustmentContent = adjustmentReqMessage.getAdjustmentContent();
                String path = StrUtil.isBlank(adjustmentContent.get("fileDir")) ? "" : adjustmentContent.get("fileDir");
                return listFiles(path, p -> true, true, Boolean.TRUE.toString().equals(adjustmentContent.get("createIfNotExist")));
            }
            case file_upload -> {
                try {
                    Map<String, String> adjustmentContent = adjustmentReqMessage.getAdjustmentContent();
                    String filename = adjustmentContent.get("filename");
                    String temporaryFilename = "upload_" + adjustmentContent.get("clientId") + "_" + filename;
                    RandomAccessFile randomAccessFile = new RandomAccessFile(temporaryFilename, "rw");
                    Debugger.getSocketClient().getSessionRandomAccessFile().put(adjustmentContent.get("clientId"), FileInfo.builder()
                            .randomAccessFile(randomAccessFile)
                            .fileDir(adjustmentContent.get("fileDir"))
                            .temporaryFilename(temporaryFilename)
                            .filename(filename)
                            .lastModified(System.currentTimeMillis())
                            .build());
                    Debugger.getSocketClient().getSessionRandomAccessFile().entrySet().removeIf(e -> {
                        if (e.getValue().getLastModified() < System.currentTimeMillis() - 1000 * 60 * 10) {
                            try {
                                e.getValue().getRandomAccessFile().close();
                            } catch (Exception ignored) {
                            }
                            return true;
                        }
                        return false;
                    });
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
            case file_remove -> {
                try {
                    Map<String, String> adjustmentContent = adjustmentReqMessage.getAdjustmentContent();
                    Path path = Paths.get(adjustmentContent.get("fileAbsolutePath")).toAbsolutePath();
                    if (Files.exists(path)) {
                        FileUtil.del(path);
                        return ProcessAdjustmentInfo.builder()
                                .adjustmentResult(Map.of("//absolutePath", path.toAbsolutePath().toString()))
                                .build();
                    } else {
                        return adjustmentError("fileAbsolutePath is not exist");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return adjustmentError(e);
                }
            }
            case file_download -> {
                try {
                    Map<String, String> adjustmentContent = adjustmentReqMessage.getAdjustmentContent();
                    Path path = Paths.get(adjustmentContent.get("fileAbsolutePath")).toAbsolutePath();
                    if (Files.exists(path)) {
                        File downloadFile;
                        if (Files.isDirectory(path)) {
                            File src = path.toFile();
                            downloadFile = new File(src.getParent(), src.getName() + ".zip");
                            ZipUtil.zip(path.toAbsolutePath().toString(), downloadFile.getAbsolutePath());
                        } else {
                            downloadFile = path.toFile();
                        }
                        RandomAccessFile randomAccessFile = new RandomAccessFile(downloadFile.getAbsolutePath(), "r");
                        long length = randomAccessFile.length();
                        int maxBodyLength = READ_BUFFER_SIZE - BUFFER_LENGTH - BUFFER_HEADER;
                        if (length > maxBodyLength) {
                            double div = NumberUtil.div(length, maxBodyLength, 0, RoundingMode.UP);
                            int subcontractCount = Double.valueOf(div).intValue();
                            for (int i = 0; i < length; i += maxBodyLength) {
                                int bodyLength = Math.min(maxBodyLength, Long.valueOf(length - i).intValue());
                                byte[] simple = new byte[bodyLength];
                                randomAccessFile.seek(i);
                                randomAccessFile.read(simple);
                                callbackFileMessage(Integer.parseInt(adjustmentContent.get("clientId")), simple, subcontractCount, i / maxBodyLength + 1);
                            }
                        } else {
                            byte[] simple = new byte[Long.valueOf(length).intValue()];
                            randomAccessFile.seek(0);
                            randomAccessFile.read(simple);
                            callbackFileMessage(Integer.parseInt(adjustmentContent.get("clientId")), simple, 1, 1);
                        }
                        randomAccessFile.close();
                    } else {
                        return adjustmentError("fileAbsolutePath is not exist");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return adjustmentError(e);
                }
            }
            case file_reader -> {
                Map<String, String> adjustmentContent = adjustmentReqMessage.getAdjustmentContent();
                String filePath = adjustmentContent.get("filePath");
                String matchString = adjustmentContent.get("matchString");
                if (StrUtil.isNotBlank(filePath) && StrUtil.isNotBlank(matchString)) {
                    Path path = Paths.get(filePath);
                    if (Files.exists(path) && Files.isDirectory(path)) {
                        JSONObject jsonObject = new JSONObject();
                        List<String> content = new ArrayList<>();
                        jsonObject.put("content", content);
                        try (var stream = "true".equals(adjustmentContent.get("childPath")) ? Files.walk(path) : Files.list(path)) {
                            int matchSize = StrUtil.isNotBlank(adjustmentContent.get("matchSize")) ? Integer.parseInt(adjustmentContent.get("matchSize")) : 1000;
                            String filenameFilter = StrUtil.isNotBlank(adjustmentContent.get("filenameFilter")) ? adjustmentContent.get("filenameFilter") : "*";
                            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + filenameFilter);
                            LogReplayInfo.MatchType matchType = LogReplayInfo.MatchType.REGEX.name().equals(adjustmentContent.get("matchType")) ? LogReplayInfo.MatchType.REGEX : LogReplayInfo.MatchType.CONTAIN;
                            Pattern pattern = matchType.equals(LogReplayInfo.MatchType.REGEX) ? Pattern.compile(Base64Decoder.decodeStr(matchString)) : null;
                            int beforeSize = StrUtil.isNotBlank(adjustmentContent.get("beforeSize")) ? Integer.parseInt(adjustmentContent.get("beforeSize")) : 0;
                            int afterSize = StrUtil.isNotBlank(adjustmentContent.get("afterSize")) ? Integer.parseInt(adjustmentContent.get("afterSize")) : 0;
                            Deque<String> prevLines = new ArrayDeque<>(beforeSize);
                            AtomicInteger afterRemain = new AtomicInteger();
                            stream.filter(Files::isRegularFile).filter(p -> matcher.matches(p.getFileName())).sorted(Comparator.comparing(p -> p.getParent().equals(path) ? 0 : 1)).forEach(p -> {
                                if (content.size() > matchSize) return;
                                try (InputStream is = open(p); BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
//                                    String line;
//                                    while ((line = br.readLine()) != null) {
//                                        line = line.replaceAll("\u001B\\[[;\\d]*m", "");
//                                        if (matchType.equals(LogReplayInfo.MatchType.REGEX) ? pattern.matcher(line).find() : line.contains(matchString)) {
//                                            content.add(p.getParent().toString() + " " + p.getFileName() + " " + line);
//                                            if (content.size() >= matchSize) break;
//                                        }
//                                    }
                                    String prefix = p.getParent() + " " + p.getFileName() + " ";
                                    String line;
                                    while ((line = br.readLine()) != null && content.size() < matchSize) {
                                        line = line.replaceAll("\u001B\\[[;\\d]*m", "");
                                        if (matchType == LogReplayInfo.MatchType.REGEX ? pattern.matcher(line).find() : line.contains(matchString)) {
                                            for (String prev : prevLines) {
                                                if (content.size() >= matchSize) break;
                                                content.add(prefix + prev);
                                            }
                                            prevLines.clear();
                                            if (content.size() < matchSize) {
                                                content.add(prefix + line);
                                            }
                                            afterRemain.set(afterSize);
                                            continue;
                                        }
                                        if (afterRemain.get() > 0) {
                                            if (content.size() < matchSize) {
                                                content.add(prefix + line);
                                            }
                                            afterRemain.getAndDecrement();
                                            continue;
                                        }
                                        if (beforeSize > 0) {
                                            if (prevLines.size() == beforeSize) {
                                                prevLines.pollFirst();
                                            }
                                            prevLines.addLast(line);
                                        }
                                    }
                                } catch (Exception e) {
                                    log.warn("filename:{} fileReader error:{}", p.getFileName(), e.getClass() + ":" + e.getMessage());
                                }
                            });
                        } catch (Exception e) {
                            return adjustmentError(e);
                        }
                        return ProcessAdjustmentInfo.builder()
                                .adjustmentExtendResult(jsonObject)
                                .build();
                    } else {
                        return adjustmentError("filePath does not exist or it is not a directory");
                    }
                } else {
                    return adjustmentError("filePath or matchString is empty");
                }
            }
            case obj_info -> {
                return getObjInfo(adjustmentReqMessage);
            }
            case obj_field -> {
                // 根据objType获取对象：hook模式下直接操作对象，可修改非static final的属性；非hook模式只能修改static属性
                Map<String, String> adjustmentContent = adjustmentReqMessage.getAdjustmentContent();
                String objName = adjustmentContent.get("objName");
                String objType = adjustmentContent.get("objType");
                String objTypeParam = adjustmentContent.get("objTypeParam");
                String fieldInfoString = adjustmentContent.get("fieldInfo");
                ObjFieldInfo fieldInfo = JSON.parseObject(fieldInfoString, ObjFieldInfo.class);
                Object obj = null;
                if ("hook".equals(objType)) {
                    if (fieldInfo.getIsFinal() && fieldInfo.getIsStatic()) {
                        return adjustmentError("The static final field cannot be modified");
                    }
                    Debug4jCommand debug4jCommand = Debugger.getDebug4jCommand();
                    if (debug4jCommand.getExtendedHook() != null && debug4jCommand.getExtendedHook().get(ExtendedHookType.HOOK_OBJ) != null) {
                        obj = debug4jCommand.getExtendedHook().get(ExtendedHookType.HOOK_OBJ).apply(Map.of("objName", objName, "objTypeParam", objTypeParam));
                    }
                } else {
                    if (fieldInfo.getIsFinal() || !fieldInfo.getIsStatic()) {
                        return adjustmentError("Only static field can be modified");
                    }
                    try {
                        Class<?> aClass = Class.forName(objName);
                        obj = aClass.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (obj == null) {
                    return adjustmentError("The object corresponding to objName could not be retrieved successfully");
                }
                try {
                    ReflectUtil.setFieldValue(obj, fieldInfo.getFieldName(), fieldInfo.getFieldValue());
                    adjustmentContent.put("fieldValues", fieldInfo.getFieldName());
                    return getObjInfo(adjustmentReqMessage);
                } catch (Exception e) {
                    e.printStackTrace();
                    return adjustmentError(e);
                }
            }
            case obj_method -> {
                // 根据objType获取对象：hook模式下直接操作对象，可执行所有方法；非hook模式判断方法是否为static，如果是直接执行，如果不是则创建对象执行
                Map<String, String> adjustmentContent = adjustmentReqMessage.getAdjustmentContent();
                String objName = adjustmentContent.get("objName");
                String objType = adjustmentContent.get("objType");
                String objTypeParam = adjustmentContent.get("objTypeParam");
                String methodInfoString = adjustmentContent.get("methodInfo");
                ObjMethodInfo methodInfo = JSON.parseObject(methodInfoString, ObjMethodInfo.class);
                Object obj = null;
                if ("hook".equals(objType)) {
                    Debug4jCommand debug4jCommand = Debugger.getDebug4jCommand();
                    if (debug4jCommand.getExtendedHook() != null && debug4jCommand.getExtendedHook().get(ExtendedHookType.HOOK_OBJ) != null) {
                        obj = debug4jCommand.getExtendedHook().get(ExtendedHookType.HOOK_OBJ).apply(Map.of("objName", objName, "objTypeParam", objTypeParam));
                    }
                } else {
                    try {
                        Class<?> aClass = Class.forName(objName);
                        obj = aClass.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (obj == null) {
                    return adjustmentError("The object corresponding to objName could not be retrieved successfully");
                }
                try {
                    Class[] classes = new Class[methodInfo.getArgTypeList().size()];
                    for (int i = 0; i < methodInfo.getArgTypeList().size(); i++) {
                        classes[i] = loadParamClass(methodInfo.getArgTypeList().get(i));
                    }
                    Method method = ReflectUtil.getMethod(obj.getClass(), methodInfo.getMethodName(), classes);
                    Object returnValue = ReflectUtil.invokeRaw(obj, method, methodInfo.getArgValues().toArray());
                    return ProcessAdjustmentInfo.builder().adjustmentExtendResult(getReturnValue(returnValue)).build();
                } catch (Exception e) {
                    e.printStackTrace();
                    return adjustmentError(e);
                }
            }
            case obj_trace -> {
                Map<String, String> adjustmentContent = adjustmentReqMessage.getAdjustmentContent();
                boolean traceType = StrUtil.isNotBlank(adjustmentContent.get("traceType")) && "install".equals(adjustmentContent.get("traceType"));
                if (StrUtil.isNotBlank(adjustmentContent.get("traceClassInfo"))) {
                    JSONArray jsonArray = JSONArray.parseArray(adjustmentContent.get("traceClassInfo"));
                    if (jsonArray != null && !jsonArray.isEmpty()) {
                        for (int i = 0; i < jsonArray.size(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            if (jsonObject != null && StrUtil.isNotBlank(jsonObject.getString("className"))) {
                                if (traceType) {
                                    Debug4jTraceInstaller.install(Debugger.getInstrumentation(), jsonObject.getString("className"), jsonObject.getString("methodName"));
                                } else {
                                    Debug4jTraceInstaller.uninstall(Debugger.getInstrumentation(), jsonObject.getString("className"));
                                }
                            }
                        }
                    }
                }
                JSONObject jsonObject = new JSONObject();
                for (String className : Debug4jTraceInstaller.getTransformerMap().keySet()) {
                    jsonObject.put(className, Debug4jTraceInstaller.getClassNameMethodMap().get(className) == null ? "" : Debug4jTraceInstaller.getClassNameMethodMap().get(className));
                }
                return ProcessAdjustmentInfo.builder()
                        .adjustmentExtendResult(jsonObject)
                        .build();
            }
            case module_ssh -> {
                if (OsUtils.isUNIX()) {
                    if (!checkSSHServerInstalled()) {
                        if (sshInstallProcess == null || !sshInstallProcess.isAlive()) {
                            JarResourceExtractor.extractInstall(); // 如果脚本运行时失败请自行修改替换，如果运行目录中存在脚本文件，（解压）拷贝时会跳过，不会覆盖
                            sshInstallProcess = JarResourceExtractor.runSSHInstall();
                        }
                        return ProcessAdjustmentInfo.builder()
                                .adjustmentResult(Map.of("openssh-server", "install processing"))
                                .build();
                    } else {
                        return ProcessAdjustmentInfo.builder()
                                .adjustmentResult(Map.of("openssh-server", "running"))
                                .build();
                    }
                } else {
                    return adjustmentError("The current operating system does not support");
                }
            }
        }
        return ProcessAdjustmentInfo.builder().build();
    }

    /**
     * 检查ssh服务是否已安装
     *
     * @return
     */
    private static boolean checkSSHServerInstalled() {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", 22), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取文件流
     *
     * @param file
     * @return
     * @throws IOException
     */
    private static InputStream open(Path file) throws IOException {
        BufferedInputStream in = new BufferedInputStream(Files.newInputStream(file), 64 * 1024);
        in.mark(2);
        int b1 = in.read();
        int b2 = in.read();
        in.reset();
        if (b1 == 0x1F && b2 == 0x8B) {
            return new GZIPInputStream(in, 64 * 1024);
        }
        return in;
    }

    /**
     * 加载参数类
     *
     * @param typeName
     * @return
     */
    private static Class<?> loadParamClass(String typeName) {
        try {
            boolean isArray = false;
            if (typeName.endsWith("[]")) {
                isArray = true;
                typeName = typeName.substring(0, typeName.length() - 2);
            }
            if (typeName.contains("<")) {
                typeName = typeName.substring(0, typeName.indexOf("<"));
            }
            Class<?> clazz = Class.forName(typeName);
            if (isArray) {
                return java.lang.reflect.Array.newInstance(clazz, 0).getClass();
            }
            return clazz;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取返回数据
     *
     * @param returnValue
     * @return
     */
    private static JSONObject getReturnValue(Object returnValue) {
        String keyString = "returnValue";
        JSONObject wrapper = new JSONObject();
        if (returnValue == null) {
            wrapper.put(keyString, null);
            return wrapper;
        }
        if (returnValue instanceof JSONObject) {
            wrapper.put(keyString, returnValue);
            return wrapper;
        }
        if (returnValue instanceof JSONArray) {
            wrapper.put(keyString, returnValue);
            return wrapper;
        }
        if (returnValue instanceof CharSequence
                || returnValue instanceof Number
                || returnValue instanceof Boolean
                || returnValue.getClass().isEnum()) {
            wrapper.put(keyString, returnValue);
            return wrapper;
        }
        Class<?> clazz = returnValue.getClass();
        if (clazz.isArray()) {
            int len = Array.getLength(returnValue);
            JSONArray arr = new JSONArray();
            for (int i = 0; i < len; i++) {
                arr.add(Array.get(returnValue, i));
            }
            wrapper.put(keyString, arr);
            return wrapper;
        }
        wrapper.put(keyString, JSON.toJSON(returnValue));
        return wrapper;
    }

    /**
     * 获取对象信息
     *
     * @param adjustmentReqMessage
     * @return
     */
    private static ProcessAdjustmentInfo getObjInfo(CommandProcessAdjustmentReqMessage adjustmentReqMessage) {
        JSONObject jsonObject = new JSONObject();
        Map<String, String> adjustmentContent = adjustmentReqMessage.getAdjustmentContent();
        String objName = adjustmentContent.get("objName");
        String objType = adjustmentContent.get("objType");
        String objTypeParam = adjustmentContent.get("objTypeParam");
        String fieldValues = adjustmentContent.get("fieldValues");
        Class<?> clazz;
        Object obj;
        if ("hook".equals(objType)) {
            Debug4jCommand debug4jCommand = Debugger.getDebug4jCommand();
            if (debug4jCommand.getExtendedHook() != null && debug4jCommand.getExtendedHook().get(ExtendedHookType.HOOK_OBJ) != null) {
                obj = debug4jCommand.getExtendedHook().get(ExtendedHookType.HOOK_OBJ).apply(Map.of("objName", objName, "objTypeParam", objTypeParam));
                if (obj != null) {
                    clazz = SystemUtils.getClass(obj);
                } else {
                    return adjustmentError("hook not found");
                }
            } else {
                return adjustmentError("hook not found");
            }
        } else {
            try {
                clazz = Class.forName(objName);
                obj = clazz.getDeclaredConstructor().newInstance(); // 默认构造方法
            } catch (Exception e) {
                e.printStackTrace();
                return adjustmentError(e);
            }
        }
        List<ObjFieldInfo> objFieldInfos = new ArrayList<>();
        try {
            List<String> fieldValuesList = StrUtil.isBlank(fieldValues) ? new ArrayList<>() : Arrays.asList(fieldValues.split(","));
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                Object fieldValue = null;
                if (fieldValuesList.contains(field.getName())) {
                    fieldValue = Modifier.isStatic(field.getModifiers()) ? field.get(null) : field.get(obj);
                }
                ObjFieldInfo fieldInfo = ObjFieldInfo.builder()
                        .fieldName(field.getName())
                        .fieldType(field.getGenericType().getTypeName())
                        .fieldValue(fieldValue)
                        .isFinal(Modifier.isFinal(field.getModifiers()))
                        .isStatic(Modifier.isStatic(field.getModifiers()))
                        .build();
                fieldInfo.setSignature(field.toGenericString());
//                fieldInfo.setSignature(fieldInfo.toString());
                objFieldInfos.add(fieldInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return adjustmentError(e);
        }
        try {
            jsonObject.put("fieldInfo", objFieldInfos);
            JSON.toJSONString(jsonObject);
        } catch (Exception e) {
            e.printStackTrace();
            return adjustmentError("The 'invokeValues' contains non-serializable values. Please remove them and try again");
        }
        List<ObjMethodInfo> objMethodInfos = Debug4jAttachOperator.methodSignatureInfo(getClassName(obj));
        jsonObject.put("methodInfo", objMethodInfos);
        return ProcessAdjustmentInfo.builder()
                .adjustmentExtendResult(jsonObject)
                .build();
    }

    /**
     * 获取文件列表
     *
     * @param fileDir
     * @param fileNameFilter
     * @param asc
     * @param createIfNotExist
     * @return
     */
    private static ProcessAdjustmentInfo listFiles(String fileDir, Function<String, Boolean> fileNameFilter, boolean asc, boolean createIfNotExist) {
        Path path = Paths.get(fileDir).toAbsolutePath();
        Comparator<Path> comparator = Comparator.comparing((Path p) -> !Files.isDirectory(p))
                .thenComparing(p -> p.getFileName().toString(), asc ? Comparator.<String>naturalOrder() : Comparator.<String>reverseOrder());
        if (!Files.exists(path)) {
            if (createIfNotExist) {
                try {
                    Files.createDirectories(path);
                } catch (Exception e) {
                    e.printStackTrace();
                    return adjustmentError(e);
                }
            } else {
                return adjustmentError("fileDir is not exist");
            }
        } else {
            if (!Files.isDirectory(path)) {
                return adjustmentError("fileDir is not directory");
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(path)) {
            stream.filter(p -> fileNameFilter.apply(p.getFileName().toString()))
                    .sorted(comparator)
                    .forEach(p -> {
                        try {
                            BasicFileAttributes attributes = Files.readAttributes(p, BasicFileAttributes.class);
                            String attr;
                            if (attributes.isDirectory()) {
                                attr = "\uD83D\uDCC1";
                            } else {
                                long size = attributes.size();
                                FileTime fileTime = attributes.lastModifiedTime();
                                attr = String.format("\uD83D\uDCE6:%.2f KB | \uD83D\uDD52:%s", size / 1024.0, fileTime);
                            }
                            result.put(p.getFileName().toString(), attr);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
            result.put("//errMsg", e.getClass().getName() + ": " + e.getMessage());
        }
        result.put("//absolutePath", path.toAbsolutePath().toString());
        return ProcessAdjustmentInfo.builder()
                .adjustmentResult(result)
                .build();
    }

    /**
     * 开启SFTP服务
     *
     * @param username
     * @param password
     */
    private static int openSftpServer(String username, String password, int port) {
        try {
            if (sshd != null && !sshd.isClosed() && sshd.isStarted()) {
                return sshd.getPort();
            }
            if (sshd == null || sshd.isClosed()) {
                sshd = SshServer.setUpDefaultServer();
            }
            int usableLocalPort = port != 0 ? port : NetUtil.getUsableLocalPort();
            sshd.setPort(usableLocalPort);
            sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Path.of("debug4j-sftp.key")));
            sshd.setPasswordAuthenticator((u, p, s) -> username.equals(u) && password.equals(p));
            sshd.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
            sshd.start();
            return usableLocalPort;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 关闭SFTP服务
     *
     * @return
     */
    private static int closeSftpServer() {
        try {
            if (sshd != null && !sshd.isClosed() && sshd.isStarted()) {
                int port = sshd.getPort();
                sshd.close(true);
                return port;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 调整异常
     *
     * @param msg
     * @return
     */
    private static ProcessAdjustmentInfo adjustmentError(String msg) {
        return ProcessAdjustmentInfo.builder()
                .adjustmentResult(Map.of("//errMsg", msg))
                .build();
    }

    private static ProcessAdjustmentInfo adjustmentError(Exception e) {
        return ProcessAdjustmentInfo.builder()
                .adjustmentResult(Map.of("//errMsg", e.getClass().getName() + ": " + e.getMessage()))
                .build();
    }
}

package com.k4ln.debug4j.core.attach.jvm;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.common.daemon.Debug4jCommand;
import com.k4ln.debug4j.common.daemon.enums.ExtendedHookType;
import com.k4ln.debug4j.common.daemon.enums.ReloadMode;
import com.k4ln.debug4j.common.protocol.command.message.CommandProcessAdjustmentReqMessage;
import com.k4ln.debug4j.common.protocol.command.message.CommandProcessReqMessage;
import com.k4ln.debug4j.common.utils.SocketProtocolUtil;
import com.k4ln.debug4j.common.utils.StringUtils;
import com.k4ln.debug4j.core.Debugger;
import com.k4ln.debug4j.core.attach.dto.FileInfo;
import com.k4ln.debug4j.core.attach.dto.ProcessAdjustmentInfo;
import com.k4ln.debug4j.core.attach.dto.ProcessArgsInfo;
import com.k4ln.debug4j.core.attach.dto.TaskInfo;
import com.k4ln.debug4j.core.attach.jvm.logger.LoggerInfo;
import com.k4ln.debug4j.core.attach.jvm.logger.LoggerOperator;
import com.sun.management.HotSpotDiagnosticMXBean;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.k4ln.debug4j.common.utils.SocketProtocolUtil.*;
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
                        return ProcessAdjustmentInfo.builder()
                                .adjustmentResult(Map.of("//errMsg", "fileAbsolutePath is not exist"))
                                .build();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return ProcessAdjustmentInfo.builder()
                            .adjustmentResult(Map.of("//errMsg", e.getClass().getName() + ": " + e.getMessage()))
                            .build();
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
                        return ProcessAdjustmentInfo.builder()
                                .adjustmentResult(Map.of("//errMsg", "fileAbsolutePath is not exist"))
                                .build();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return ProcessAdjustmentInfo.builder()
                            .adjustmentResult(Map.of("//errMsg", e.getClass().getName() + ": " + e.getMessage()))
                            .build();
                }
            }
            case obj_test -> {
                printStaticMembers(SocketProtocolUtil.class);
            }
        }
        return ProcessAdjustmentInfo.builder().build();
    }

    /**
     * 禁止final变量修改
     *
     * @param clazz
     */
    public static void printStaticMembers(Class<?> clazz) {
        // --------------  变量  --------------
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                try {
                    System.out.println("[FIELD] " + field.getType().getName() + " " + field.getName() + " = " + field.get(null));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        Object fieldValue = ReflectUtil.getFieldValue(SocketProtocolUtil.class, "READ_BUFFER_SIZE");

        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                System.out.println("[METHOD] " + method.getName());
            }
        }

        Debug4jCommand debug4jCommand = Debugger.getDebug4jCommand();
        if (debug4jCommand.getExtendedHook() != null && debug4jCommand.getExtendedHook().get(ExtendedHookType.HOOK_REFLECT) != null) {
            Object bean = debug4jCommand.getExtendedHook().get(ExtendedHookType.HOOK_REFLECT).apply("demo2Controller");
            Object fieldValue2 = ReflectUtil.getFieldValue(bean, "key");
            ReflectUtil.setFieldValue(bean, "key", "value");

            // --------------  函数  --------------
            try {

                Method demoMethod1 = bean.getClass().getMethod("demo2");
                Method demoMethod2 = ReflectUtil.getMethod(bean.getClass(), "demo2");
                Method demoMethod3 = ReflectUtil.getMethodByName(bean.getClass(), "demo2");
                Object oo1 = ReflectUtil.invokeRaw(bean, demoMethod1);
                Object oo2 = ReflectUtil.invokeRaw(bean, demoMethod2);
                Object oo3 = ReflectUtil.invokeRaw(bean, demoMethod3);

                Method staticMethod1 = ReflectUtil.getMethod(StringUtils.class, "extractPort"); // 获取为null
                Method staticMethod2 = ReflectUtil.getMethod(StringUtils.class, "extractPort", String.class);
                Method staticMethod3 = ReflectUtil.getMethodByName(StringUtils.class, "extractPort");
                Method staticMethod4 = StringUtils.class.getMethod("extractPort", String.class);

                Object ok2 = ReflectUtil.invokeRaw(null, staticMethod2, "address=127.0.0.1:5002");
                Object ok3 = ReflectUtil.invokeRaw(null, staticMethod3, "address=127.0.0.1:5003");
                Object ok4 = ReflectUtil.invokeRaw(null, staticMethod4, "address=127.0.0.1:5004");

                // 非spring对象支持不传递参数类型
                TaskInfo taskInfo = new TaskInfo();
                taskInfo.setReqId("77777");
                Method objMethod1 = taskInfo.getClass().getMethod("getReqId");
                Method objMethod2 = ReflectUtil.getMethod(taskInfo.getClass(), "getReqId");
                Method objMethod3 = ReflectUtil.getMethodByName(taskInfo.getClass(), "getReqId");

                // TODO 同名方法，调用哪个？ -> 返回方法签名 通过（JADS）反编译获取 方法签名信息
                // TODO 执行钩子函数前先获取所有函数（返回值、名称、参数），有返回值的返回执行结果
                // TODO 验证参数使用json，调用目标方法
                // TODO 执行代码块（bytebuddy支持类签名修改）【钩子增强】

                log.info("sa");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
                    return ProcessAdjustmentInfo.builder()
                            .adjustmentResult(Map.of("//errMsg", e.getClass().getName() + ": " + e.getMessage()))
                            .build();
                }
            } else {
                return ProcessAdjustmentInfo.builder()
                        .adjustmentResult(Map.of("//errMsg", "fileDir is not exist"))
                        .build();
            }
        } else {
            if (!Files.isDirectory(path)) {
                ;
                return ProcessAdjustmentInfo.builder()
                        .adjustmentResult(Map.of("//errMsg", "fileDir is not directory"))
                        .build();
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
}

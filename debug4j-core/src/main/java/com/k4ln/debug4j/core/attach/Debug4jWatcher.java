package com.k4ln.debug4j.core.attach;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.Tailer;
import cn.hutool.core.util.HashUtil;
import cn.hutool.core.util.StrUtil;
import com.k4ln.debug4j.common.protocol.command.message.CommandTaskReqMessage;
import com.k4ln.debug4j.common.protocol.command.message.CommandTaskTailRespMessage;
import com.k4ln.debug4j.common.protocol.socket.ProtocolTypeEnum;
import com.k4ln.debug4j.core.attach.dto.TaskInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.StreamSupport;

import static com.k4ln.debug4j.core.client.SocketClient.callbackMessage;

@Slf4j
public class Debug4jWatcher {

    /**
     * filePath -> CommandTaskReqMessage
     */
    private static TimedCache<String, TaskInfo> watcher; // 持续30秒，等待服务端心跳

    private static void initWatcher() {
        if (watcher == null) {
            watcher = CacheUtil.newTimedCache(30 * 1000);
            // 仅过期移除触发（获取过程中会先同步检查是否过期，如果过期会优先执行监听器逻辑再返回）
            watcher.setListener((key, cachedObject) -> cachedObject.getTailer().stop());
            watcher.schedulePrune(1000);
        }
    }

    /**
     * 清理所有监听器
     */
    public static void clear() {
        initWatcher();
        watcher.keySet().forEach(e -> watcher.get(e).getTailer().stop());
        watcher.clear();
    }

    /**
     * 获取任务列表
     *
     * @return
     */
    public static List<CommandTaskReqMessage> getTask() {
        initWatcher();
        if (watcher.isEmpty()) {
            return new ArrayList<>();
        }
        Iterator<TaskInfo> iterator = watcher.iterator();
        return StreamSupport.stream(((Iterable<TaskInfo>) () -> iterator).spliterator(), false)
                .map(e -> BeanUtil.toBean(e, CommandTaskReqMessage.class)).toList();
    }

    /**
     * 开启任务
     *
     * @param reqMessage
     * @return
     */
    public synchronized static List<CommandTaskReqMessage> openTask(CommandTaskReqMessage reqMessage) {
        initWatcher();
        Path path = Path.of(reqMessage.getFilePath());
        File file = FileUtil.file(path.toFile());
        if (file.exists() && !file.isDirectory()) {
            TaskInfo watchTask = watcher.get(watcherKey(reqMessage));
            if (watchTask == null) {
                Tailer tailer = new Tailer(file, line -> {
                    if (StrUtil.isNotBlank(line)) {
                        callbackMessage(HashUtil.fnvHash(reqMessage.getReqId()), ProtocolTypeEnum.COMMAND,
                                CommandTaskTailRespMessage.buildTaskTailRespMessage(reqMessage.getFilePath(), line));
                    }
                }, reqMessage.getInitReadLine());
                TaskInfo taskInfo = TaskInfo.builder()
                        .reqId(reqMessage.getReqId())
                        .filePath(reqMessage.getFilePath())
                        .initReadLine(reqMessage.getInitReadLine())
                        .lastListenTime(System.currentTimeMillis())
                        .tailer(tailer)
                        .build();
                watcher.put(watcherKey(reqMessage), taskInfo);
                tailer.start(true);
            } else {
                watchTask.setLastListenTime(System.currentTimeMillis());
            }
        }
        return getTask();
    }

    /**
     * 关闭任务
     *
     * @param reqMessage
     * @return
     */
    public synchronized static List<CommandTaskReqMessage> closeTask(CommandTaskReqMessage reqMessage) {
        initWatcher();
        TaskInfo taskInfo = watcher.get(watcherKey(reqMessage), false);
        if (taskInfo != null) {
            taskInfo.getTailer().stop();
            watcher.remove(watcherKey(reqMessage));
        }
        return getTask();
    }

    private static String watcherKey(CommandTaskReqMessage reqMessage) {
        return reqMessage.getFilePath() + "_" + reqMessage.getLoginId();
    }
}

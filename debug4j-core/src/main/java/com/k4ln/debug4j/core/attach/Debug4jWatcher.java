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
    private final static TimedCache<String, TaskInfo> watcher = CacheUtil.newTimedCache(0);

    public Debug4jWatcher() {
        // 仅过期移除触发
        watcher.setListener((key, cachedObject) -> cachedObject.getTailer().stop());
    }

    /**
     * 清理所有监听器
     */
    public static void clear() {
        watcher.keySet().forEach(e -> {
            watcher.get(e).getTailer().stop();
            watcher.remove(e);
        });
    }

    /**
     * 获取任务列表
     *
     * @return
     */
    public static List<CommandTaskReqMessage> getTask() {
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
        File file = FileUtil.file(reqMessage.getFilePath());
        if (!file.exists()) {
            reqMessage.setFilePath(file.getAbsolutePath() + " not exists");
            reqMessage.setExpire(-1);
        }
        if (file.isDirectory()) {
            reqMessage.setFilePath(file.getAbsolutePath() + " is directory");
            reqMessage.setExpire(-1);
        }
        if (watcher.containsKey(reqMessage.getFilePath())) {
            reqMessage.setFilePath(file.getAbsolutePath() + " is already exists");
            reqMessage.setExpire(-1);
        }
        if (reqMessage.getExpire() != -1) {
            Tailer tailer = new Tailer(file, line -> {
                if (StrUtil.isNotBlank(line)) {
                    callbackMessage(HashUtil.fnvHash(reqMessage.getReqId()), ProtocolTypeEnum.COMMAND,
                            CommandTaskTailRespMessage.buildTaskTailRespMessage(reqMessage.getFilePath(), line));
                }
            }, 10);
            TaskInfo taskInfo = BeanUtil.toBean(reqMessage, TaskInfo.class);
            taskInfo.setTailer(tailer);
            watcher.put(reqMessage.getFilePath(), taskInfo, reqMessage.getExpire() * 60 * 1000);
            tailer.start(true);
        }
        List<CommandTaskReqMessage> task = new ArrayList<>(getTask());
        if (reqMessage.getExpire() == -1) {
            task.add(reqMessage);
        }
        return task;
    }

    /**
     * 关闭任务
     *
     * @param reqMessage
     * @return
     */
    public synchronized static List<CommandTaskReqMessage> closeTask(CommandTaskReqMessage reqMessage) {
        TaskInfo taskInfo = watcher.get(reqMessage.getFilePath());
        if (taskInfo != null) {
            taskInfo.getTailer().stop();
            watcher.remove(reqMessage.getFilePath());
        }
        return getTask();
    }
}

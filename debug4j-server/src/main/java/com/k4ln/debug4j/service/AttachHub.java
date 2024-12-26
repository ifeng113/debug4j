package com.k4ln.debug4j.service;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import com.alibaba.fastjson2.JSON;
import com.k4ln.debug4j.service.dto.AttachTaskEmitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AttachHub {

    /**
     * reqId -> 异步任务
     */
    private final TimedCache<String, CompletableFuture<String>> attachTask = CacheUtil.newTimedCache(60 * 1000);

    /**
     * filePath -> AttachTaskEmitter
     */
    private final TimedCache<String, List<AttachTaskEmitter>> attachTaskEmitters = CacheUtil.newTimedCache(30 * 60 * 1000);

    public AttachHub() {
        // 仅过期移除触发
        attachTaskEmitters.setListener((key, cachedObject) -> cachedObject.forEach(e -> e.getSseEmitter().complete()));
    }

    /**
     * 同步等待
     *
     * @param reqId
     * @param runnable
     * @param clazz
     * @param <T>
     * @return
     */
    public <T> T syncResult(String reqId, Runnable runnable, Class<T> clazz) {
        CompletableFuture<String> future = new CompletableFuture<>();
        attachTask.put(reqId, future);
        try {
            runnable.run();
            String result = future.get(30, TimeUnit.SECONDS);
            attachTask.remove(reqId);
            return JSON.parseObject(result, clazz);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 推送数据
     *
     * @param reqId
     * @param data
     */
    public void pushResult(String reqId, String data) {
        CompletableFuture<String> future = attachTask.get(reqId);
        if (future != null) {
            future.complete(data);
        }
    }

    /**
     * 推送数据至推送器
     *
     * @param key
     * @param data
     */
    public void pushSseEmitter(String key, String data) {
        List<AttachTaskEmitter> taskEmitters = attachTaskEmitters.get(key);
        if (taskEmitters != null) {
            taskEmitters.forEach(e -> {
                try {
                    e.getSseEmitter().send(data);
                } catch (Exception ex) {
                    log.error("pushSseEmitter error:{}", ex.getMessage());
                }
            });
        }
    }

    /**
     * 绑定推送器
     *
     * @param key
     * @param loginId
     * @return
     */
    public SseEmitter getSseEmitter(String key, String loginId) {
        List<AttachTaskEmitter> taskEmitters = attachTaskEmitters.get(key);
        if (taskEmitters != null) {
            for (AttachTaskEmitter emitter : taskEmitters) {
                if (emitter.getLoginID().equals(loginId)) {
                    return emitter.getSseEmitter();
                }
            }
        } else {
            // rd：ArrayList（taskEmitters.forEach会在taskEmitters对象修改时触发ConcurrentModificationException）
            taskEmitters = new CopyOnWriteArrayList<>();
        }
        SseEmitter sseEmitter = new SseEmitter(30 * 60 * 1000L);
        sseEmitter.onCompletion(() -> removeSseEmitter(key, loginId));
        sseEmitter.onError(throwable -> removeSseEmitter(key, loginId));
        sseEmitter.onTimeout(() -> removeSseEmitter(key, loginId));
        AttachTaskEmitter taskEmitter = AttachTaskEmitter.builder()
                .loginID(loginId)
                .sseEmitter(sseEmitter)
                .build();
        taskEmitters.add(taskEmitter);
        attachTaskEmitters.put(key, taskEmitters);
        return taskEmitter.getSseEmitter();
    }

    /**
     * 删除推送器
     *
     * @param key
     * @param loginId
     */
    public void removeSseEmitter(String key, String loginId) {
        List<AttachTaskEmitter> taskEmitters = attachTaskEmitters.get(key);
        if (taskEmitters != null) {
            if (loginId != null){
                for (AttachTaskEmitter emitter : taskEmitters) {
                    if (emitter.getLoginID().equals(loginId)) {
                        emitter.getSseEmitter().complete();
                        taskEmitters.remove(emitter);
                        return;
                    }
                }
            } else {
                for (AttachTaskEmitter emitter : taskEmitters) {
                    emitter.getSseEmitter().complete();
                }
                taskEmitters.clear();
                attachTaskEmitters.remove(key);
            }
        }
    }
}

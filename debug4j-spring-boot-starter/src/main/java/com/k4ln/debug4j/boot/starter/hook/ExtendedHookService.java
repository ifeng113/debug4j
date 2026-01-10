package com.k4ln.debug4j.boot.starter.hook;

import com.k4ln.debug4j.common.daemon.enums.ExtendedHookType;

import java.util.Map;
import java.util.function.Function;

public interface ExtendedHookService {

    /**
     * 自定义扩展钩子
     *
     * @return
     */
    void adjustmentExtendedHook(Map<ExtendedHookType, Function<Object, ?>> extendedHook);
}

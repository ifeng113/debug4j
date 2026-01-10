package com.k4ln.demo2.hook;

import com.k4ln.debug4j.boot.starter.hook.ExtendedHookService;
import com.k4ln.debug4j.common.daemon.enums.ExtendedHookType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

@Component
@Slf4j
public class Demo2ExtendedHookService implements ExtendedHookService {

    @Override
    public void adjustmentExtendedHook(Map<ExtendedHookType, Function<Object, ?>> extendedHook) {
//        extendedHook.remove(ExtendedHookType.HOOK_ARGS);
    }
}

package com.k4ln.debug4j.common.protocol.command.message.deserializer;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReader;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

public class SequentialMapDeserializer implements ObjectReader<Map<String, Object>> {

    @Override
    public Map<String, Object> readObject(JSONReader reader, Type type, Object fieldName, long features) {
        Map<String, Object> temp = reader.readObject();
        return new LinkedHashMap<>(temp);
    }
}

package com.k4ln.debug4j.common.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {

    /**
     * 提取 JDWP 参数中的端口号
     *
     * @param input 输入的参数字符串
     * @return 提取的端口号，找不到时返回 null
     */
    public static String extractPort(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        if (input.contains("127.0.0.1")) {
            // address=127.0.0.1:5005
            String regex = "address=([\\w\\.]+):(\\d+)";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(input);
            return matcher.find() ? matcher.group(2) : null;
        } else {
            // address=*:5005
            String regex = "address=\\*?:?(\\d+)";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(input);
            return matcher.find() ? matcher.group(1) : null;
        }
    }
}

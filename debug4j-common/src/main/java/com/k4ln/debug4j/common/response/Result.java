package com.k4ln.debug4j.common.response;

import com.k4ln.debug4j.common.response.exception.BaseException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 响应结构体
 *
 * @author k4ln
 * @since 2024-04-22
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /**
     * 响应码
     */
    private int code;

    /**
     * 响应描述
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 请求成功
     *
     * @return
     */
    public static <T> Result<T> ok() {
        return result(HttpResult.SUCCESS, null);
    }

    public static <T> Result<T> ok(T data) {
        return result(HttpResult.SUCCESS, data);
    }

    /**
     * 请求失败
     *
     * @return
     */
    public static <T> Result<T> fail() {
        return result(HttpResult.FAIL, null);
    }

    public static <T> Result<T> fail(String message) {
        return result(HttpResult.FAIL.getCode(), message, null);
    }

    public static <T> Result<T> fail(BaseException baseException) {
        return result(baseException.getResult().getCode(), baseException.getResult().getMessage(), null);
    }

    /**
     * 请求装载
     *
     * @param httpResult
     * @param data
     * @param <T>
     * @return
     */
    public static <T> Result<T> result(HttpResult httpResult, T data) {
        return result(httpResult.getCode(), httpResult.getMessage(), data);
    }

    public static <T> Result<T> result(Integer code, String message, T data) {
        return Result.<T>builder()
                .code(code)
                .message(message)
                .data(data)
                .build();
    }
}

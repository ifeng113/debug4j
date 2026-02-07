package com.k4ln.debug4j.core.attach.jvm.trace;

import cn.hutool.core.util.NumberUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Debug4jTraceInfo {

    /**
     * 链路ID
     */
    private String traceId;

    /**
     * 开始时间
     */
    private long start;

    /**
     * 结束时间
     */
    private long end;

    /**
     * 链路ID
     */
    private String method;

    /**
     * 参数
     */
    private String args;

    /**
     * 异常信息
     */
    private String throwable;

    /**
     * 返回值
     */
    private String ret;

    /**
     * 耗时
     *
     * @return
     */
    public BigDecimal getCost() {
        return NumberUtil.div(String.valueOf(end - start), String.valueOf(1_000_000), 6, RoundingMode.UP);
    }

    /**
     * 打印
     */
    public void print() {
        StringBuilder sb = new StringBuilder();
        sb.append("[Debug4j-Trace] ")
                .append(traceId)
                .append(" ")
                .append(method)
                .append(" | cost=").append(getCost()).append("ms");
        if (args != null && !args.isEmpty()) {
            sb.append(" | args=").append(args);
        }
        if (ret != null && !ret.isEmpty()) {
            sb.append(" | ret=").append(ret);
        }
        if (throwable != null && !throwable.isEmpty()) {
            sb.append(" | error=").append(throwable);
        }
        System.out.println(sb);
    }
}



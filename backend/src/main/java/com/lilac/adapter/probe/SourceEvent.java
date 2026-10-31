package com.lilac.adapter.probe;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据源取数事件，成功与失败都记一条，用于判断上游改版频率
 */
@Data
public class SourceEvent implements Serializable {

    /**
     * 数据源名，如 eastmoney / tencent / sina / csindex
     */
    private String source;

    /**
     * 取数目标，如基金代码或标的后缀
     */
    private String target;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 失败类型，成功时为 null
     */
    private String failureKind;

    /**
     * 失败或异常说明，截断到列宽以内
     */
    private String message;

    /**
     * 发生时间
     */
    private LocalDateTime happenedAt;

    private static final long serialVersionUID = 1L;
}

package com.lilac.domain.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 数据源取数留痕，成功与失败都记一条，供判断上游改版频率
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "source_event", camelToUnderline = false, comment = "数据源取数留痕，成功与失败都记一条")
public class SourceEventRecord extends BaseEntity {

    /**
     * 数据源名
     */
    private String source;

    /**
     * 取数目标
     */
    private String target;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 失败类型 STRUCTURE / NETWORK / NO_DATA / THROTTLED
     */
    private String failureKind;

    /**
     * 失败或异常说明
     */
    private String message;

    /**
     * 发生时间
     */
    private LocalDateTime happenedAt;
}

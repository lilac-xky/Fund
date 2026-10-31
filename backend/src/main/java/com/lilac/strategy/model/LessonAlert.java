package com.lilac.strategy.model;

import com.lilac.enums.LessonPattern;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 一条历史教训警示，含模式、触发依据数值与可读的教训文案
 */
@Data
public class LessonAlert implements Serializable {

    /**
     * 命中的错误模式
     */
    private LessonPattern pattern;

    /**
     * 模式中文名
     */
    private String patternLabel;

    /**
     * 涉及品种业务主键
     */
    private String varietyKey;

    /**
     * 6 位代码
     */
    private String varietyCode;

    /**
     * 标的名称
     */
    private String varietyName;

    /**
     * 教训说明，取自使用者原话，必须可读
     */
    private String lesson;

    /**
     * 触发依据文字，逐条带具体数值
     */
    private List<String> remarks = new ArrayList<>();

    /**
     * 触发所依据的数值，如买入价、近期高点、偏离幅度
     */
    private BigDecimal triggerValue;

    /**
     * 参照值，如近期高点或累计收益率
     */
    private BigDecimal referenceValue;

    /**
     * 触发日期
     */
    private LocalDate triggerDate;

    private static final long serialVersionUID = 1L;
}

package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 单条历史教训警示视图，含模式、触发依据数值与可读的教训说明
 */
@Data
public class LessonAlertVO implements Serializable {

    /**
     * 错误模式代码
     */
    private String pattern;

    /**
     * 模式中文名
     */
    private String patternLabel;

    /**
     * 品种业务主键
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
     * 教训说明，取自使用者原话
     */
    private String lesson;

    /**
     * 触发依据文字，逐条带具体数值
     */
    private List<String> remarks;

    /**
     * 触发所依据的数值
     */
    private BigDecimal triggerValue;

    /**
     * 参照值
     */
    private BigDecimal referenceValue;

    /**
     * 触发日期
     */
    private LocalDate triggerDate;

    private static final long serialVersionUID = 1L;
}

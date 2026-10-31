package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 历史教训警示总览，逐条列出命中的模式并附教训说明
 */
@Data
public class LessonAdvisoryVO implements Serializable {

    /**
     * 命中的警示
     */
    private List<LessonAlertVO> items;

    /**
     * 本次检查的变动笔数
     */
    private Integer checkedCount;

    /**
     * 命中条数
     */
    private Integer alertCount;

    /**
     * 参数是否可用，false 时不做判定
     */
    private Boolean paramUsable;

    /**
     * 参数问题清单
     */
    private List<String> paramIssues;

    /**
     * 追高容忍幅度阈值，供页面回显
     */
    private BigDecimal chaseTolerance;

    /**
     * 连续亏损天数阈值
     */
    private Integer panicLossDays;

    /**
     * 是否随邮件发出
     */
    private Boolean participateInAlert;

    /**
     * 逐条摘要，供邮件直接取用
     */
    private List<String> summary;

    /**
     * 操作提示，如无历史变动
     */
    private String notice;

    private static final long serialVersionUID = 1L;
}

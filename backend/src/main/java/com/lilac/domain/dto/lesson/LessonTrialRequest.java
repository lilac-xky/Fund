package com.lilac.domain.dto.lesson;

import com.lilac.enums.LessonPattern;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 警示规则试算请求，用给定参数在历史数据上回看是否触发
 */
@Data
public class LessonTrialRequest implements Serializable {

    /**
     * 只试算这一类，为空时试算全部
     */
    private LessonPattern pattern;

    /**
     * 追高容忍幅度，为空时用库里当前值
     */
    private BigDecimal chaseTolerance;

    /**
     * 追高回看窗口
     */
    private Integer chaseWindow;

    /**
     * 连续亏损天数
     */
    private Integer panicLossDays;

    /**
     * 累计亏损率下限
     */
    private BigDecimal panicLossRate;

    /**
     * 熟悉品种天数
     */
    private Integer familiarDays;

    /**
     * 回看多少天的历史变动
     */
    private Integer days;

    private static final long serialVersionUID = 1L;
}

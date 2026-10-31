package com.lilac.domain.vo;

import com.lilac.enums.LessonPattern;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 警示规则试算结果，用给定参数在历史数据上回看，说明会命中哪些
 */
@Data
public class LessonTrialVO implements Serializable {

    /**
     * 试算所用的参数，必须在结果里回显，否则结果不可复现
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
     * 回看天数
     */
    private Integer days;

    /**
     * 参与试算的变动笔数
     */
    private Integer checkedCount;

    /**
     * 命中的条数
     */
    private Integer hitCount;

    /**
     * 逐类命中数
     */
    private List<PatternCount> perPattern;

    /**
     * 命中的明细，前若干条
     */
    private List<LessonAlertVO> hits;

    /**
     * 说明
     */
    private String note;

    private static final long serialVersionUID = 1L;

    /**
     * 单个模式的命中计数
     */
    @Data
    public static class PatternCount implements Serializable {

        /**
         * 模式代码
         */
        private String pattern;

        /**
         * 模式中文名
         */
        private String label;

        /**
         * 命中条数
         */
        private Integer count;

        private static final long serialVersionUID = 1L;
    }
}

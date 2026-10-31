package com.lilac.strategy.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史教训警示的参数，含三类错误模式各自的阈值
 */
@Data
public class LessonPlan implements Serializable {

    /**
     * 买入价高于近期高点的容忍幅度，百分点
     */
    private BigDecimal chaseTolerance;

    /**
     * 判断近期高点所用的交易日数
     */
    private Integer chaseWindow;

    /**
     * 连续亏损多少个交易日才提示恐慌卖出
     */
    private Integer panicLossDays;

    /**
     * 回看窗口内累计亏损率下限，百分点
     */
    private BigDecimal panicLossRate;

    /**
     * 换入品种登记不足多少天即视为不熟悉
     */
    private Integer familiarDays;

    /**
     * 是否随收盘前邮件发出
     */
    private Boolean participateInAlert;

    /**
     * 参数问题清单，空表示可用
     */
    private List<String> issues = new ArrayList<>();

    /**
     * 参数是否齐全且自洽
     *
     * @return 可用返回 true
     */
    public boolean complete() {
        return issues.isEmpty() && chaseTolerance != null && chaseWindow != null
                && panicLossDays != null && panicLossRate != null && familiarDays != null;
    }

    /**
     * 是否参与邮件提醒，未设置时按参与处理
     *
     * @return 参与返回 true
     */
    public boolean alertEnabled() {
        return !Boolean.FALSE.equals(participateInAlert);
    }

    private static final long serialVersionUID = 1L;
}

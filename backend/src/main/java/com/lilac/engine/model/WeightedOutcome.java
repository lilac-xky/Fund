package com.lilac.engine.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 持仓加权结果，带出覆盖率与停牌标的
 */
@Data
public class WeightedOutcome {

    /**
     * 加权涨跌幅，百分点；残差按 0 计
     */
    private BigDecimal change;

    /**
     * 披露持仓的合计权重，百分点
     */
    private BigDecimal disclosedWeight;

    /**
     * 参与加权的权重，百分点；无行情的标的已剔除
     */
    private BigDecimal usedWeight;

    /**
     * 被剔除的标的数
     */
    private int excludedCount;

    /**
     * 停牌标的的代码与名称，形如 600870 退市海润
     */
    private String suspendedSymbols;

    /**
     * 是否所有标的都取不到行情
     *
     * @return 全部不可用返回 true
     */
    public boolean empty() {
        return change == null || usedWeight == null || usedWeight.signum() <= 0;
    }
}

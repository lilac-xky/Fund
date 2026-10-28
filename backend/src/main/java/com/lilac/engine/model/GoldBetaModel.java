package com.lilac.engine.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 黄金 β 与 AR(1) 的滚动估计结果
 */
@Data
public class GoldBetaModel {

    /**
     * 对黄金 ETF 的仓位系数 β
     */
    private BigDecimal beta;

    /**
     * 回归截距 α，百分点
     */
    private BigDecimal alpha;

    /**
     * 误差一阶自相关系数 ρ，已限制在 ±0.9 内
     */
    private BigDecimal rho;

    /**
     * 参与估计的配对样本数
     */
    private int samples;

    /**
     * 窗口内最后一个交易日的残差，百分点
     */
    private BigDecimal lastResidual;

    /**
     * 窗口内残差绝对值的 P90，百分点；供 β+AR(1) 估计的误差区间交叉校验
     */
    private BigDecimal residualP90;

    /**
     * 是否因样本不足退化为直取代理
     */
    private boolean fallback;

    /**
     * 用给定代理涨跌幅预测当日涨跌幅
     *
     * @param proxyChange 代理标的当日涨跌幅，百分点
     * @return 预估涨跌幅，百分点
     */
    public BigDecimal predict(BigDecimal proxyChange) {
        if (proxyChange == null) {
            return null;
        }
        BigDecimal value = beta.multiply(proxyChange);
        if (alpha != null) {
            value = value.add(alpha);
        }
        if (rho != null && lastResidual != null) {
            value = value.add(rho.multiply(lastResidual));
        }
        return value;
    }
}

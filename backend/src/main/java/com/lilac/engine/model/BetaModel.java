package com.lilac.engine.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 代理标的的 α 与 β 回归结果，指数直取分支用它把代理涨跌折算成基金涨跌
 */
@Data
public class BetaModel {

    /**
     * 对代理标的的仓位系数 β
     */
    private BigDecimal beta;

    /**
     * 回归截距 α，百分点
     */
    private BigDecimal alpha;

    /**
     * 参与估计的配对样本数
     */
    private int samples;

    /**
     * 用代理涨跌幅预测基金涨跌幅
     *
     * @param proxyChange 代理标的当日涨跌幅，百分点
     * @return 预估涨跌幅，百分点；入参为 null 时返回 null
     */
    public BigDecimal predict(BigDecimal proxyChange) {
        if (proxyChange == null || beta == null) {
            return null;
        }
        BigDecimal value = beta.multiply(proxyChange);
        return alpha == null ? value : value.add(alpha);
    }
}

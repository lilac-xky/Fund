package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 回测实际生效的参数，结果里必须原样回显，否则两次回测不可比
 */
@Data
public class BacktestParamVO implements Serializable {

    /**
     * A 档点估计的中位绝对误差上限，百分点
     */
    private BigDecimal aMaeLimit;

    /**
     * 指数直取 β 回归窗口，交易日；0 表示不启用 β 校正
     */
    private Integer indexBetaWindow;

    /**
     * 黄金 β 滚动窗口，交易日
     */
    private Integer goldWindow;

    /**
     * 债券传导系数滚动窗口，交易日
     */
    private Integer bondWindow;

    /**
     * 区间半宽的 sigma 倍数
     */
    private BigDecimal sigmaFactor;

    /**
     * 股票型误差阈值，百分点
     */
    private BigDecimal stockErrorThreshold;

    /**
     * 债券型误差阈值，百分点
     */
    private BigDecimal bondErrorThreshold;

    private static final long serialVersionUID = 1L;
}

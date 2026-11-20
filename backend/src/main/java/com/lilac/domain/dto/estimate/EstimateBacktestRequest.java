package com.lilac.domain.dto.estimate;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 因子回测请求，全部参数可空，为空时沿用当前生效配置
 */
@Data
public class EstimateBacktestRequest implements Serializable {

    /**
     * 品种业务主键，为空时取全部持有中的持仓
     */
    private List<String> varietyKeys;

    /**
     * 回测起始日，含
     */
    private String startDate;

    /**
     * 回测截止日，含
     */
    private String endDate;

    /**
     * A 档点估计的中位绝对误差上限，百分点
     */
    private BigDecimal aMaeLimit;

    /**
     * 指数直取的 β 回归窗口，交易日；传 0 表示不启用 β 校正、直接沿用代理涨跌
     */
    private Integer indexBetaWindow;

    /**
     * 黄金 β 的滚动窗口，交易日
     */
    private Integer goldWindow;

    /**
     * 债券传导系数的滚动窗口，交易日
     */
    private Integer bondWindow;

    /**
     * 区间半宽的 sigma 倍数
     */
    private BigDecimal sigmaFactor;

    /**
     * 股票型的误差阈值，百分点
     */
    private BigDecimal stockErrorThreshold;

    /**
     * 债券型的误差阈值，百分点
     */
    private BigDecimal bondErrorThreshold;

    private static final long serialVersionUID = 1L;
}

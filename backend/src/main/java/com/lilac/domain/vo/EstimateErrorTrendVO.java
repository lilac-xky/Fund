package com.lilac.domain.vo;

import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 单只基金的误差趋势，供复盘页与「是否停用该基金预估」判断
 */
@Data
public class EstimateErrorTrendVO implements Serializable {

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
     * 基金类型
     */
    private FundCategory category;

    /**
     * 基金类型中文名
     */
    private String categoryLabel;

    /**
     * 滚动窗口内的样本数
     */
    private Integer sampleCount;

    /**
     * 滚动窗口内绝对误差的中位数，百分点
     */
    private BigDecimal medianAbsError;

    /**
     * 滚动窗口内的平均绝对误差，百分点
     */
    private BigDecimal mae;

    /**
     * 滚动窗口内的方向准确率
     */
    private BigDecimal directionAccuracy;

    /**
     * 滚动窗口内的区间覆盖率，混合型才有
     */
    private BigDecimal intervalCoverage;

    /**
     * 最近一次滚动判定的档位
     */
    private EstimateTier rollingTier;

    /**
     * 最近一次校准的交易日
     */
    private java.time.LocalDate latestTradeDate;

    /**
     * 连续超阈值期数
     */
    private Integer consecutiveOverCount;

    /**
     * 是否建议停用该基金预估
     */
    private Boolean suggestSuspend;

    /**
     * 逐日误差序列，按交易日升序
     */
    private List<EstimateErrorVO> series;

    private static final long serialVersionUID = 1L;
}

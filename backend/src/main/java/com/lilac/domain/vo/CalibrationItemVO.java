package com.lilac.domain.vo;

import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 单只基金的晚间校准结果，含误差、阈值结论与当时档位
 */
@Data
public class CalibrationItemVO implements Serializable {

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
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 当时输出档位
     */
    private EstimateTier outputTier;

    /**
     * 当时给出的预估涨跌幅，百分点；B、C 档为 null
     */
    private BigDecimal estimatedChange;

    /**
     * 当日实际涨跌幅，百分点
     */
    private BigDecimal actualChange;

    /**
     * 误差，预估减实际，百分点
     */
    private BigDecimal errorValue;

    /**
     * 本次判定所用阈值，百分点；混合型为 null
     */
    private BigDecimal thresholdValue;

    /**
     * 是否超阈值
     */
    private Boolean overThreshold;

    /**
     * 方向是否命中，混合型才有
     */
    private Boolean directionHit;

    /**
     * 实际值是否落在区间内，混合型才有
     */
    private Boolean inInterval;

    /**
     * 当时区间下限，百分点
     */
    private BigDecimal intervalLow;

    /**
     * 当时区间上限，百分点
     */
    private BigDecimal intervalHigh;

    /**
     * 当时区间宽度，百分点
     */
    private BigDecimal intervalWidth;

    /**
     * 校准后的滚动档位
     */
    private EstimateTier rollingTier;

    /**
     * 滚动窗口内绝对误差的中位数，百分点
     */
    private BigDecimal medianAbsError;

    /**
     * 滚动窗口内的方向准确率
     */
    private BigDecimal directionAccuracy;

    /**
     * 滚动窗口内的区间覆盖率
     */
    private BigDecimal intervalCoverage;

    /**
     * 连续超阈值期数
     */
    private Integer consecutiveOverCount;

    /**
     * 是否建议停用该基金预估
     */
    private Boolean suggestSuspend;

    /**
     * 黄金 β，非黄金为 null
     */
    private BigDecimal beta;

    /**
     * 黄金 AR(1) 系数，非黄金为 null
     */
    private BigDecimal rho;

    /**
     * 黄金残差 P90，百分点
     */
    private BigDecimal residualP90;

    /**
     * 当时持仓截止日
     */
    private LocalDate holdingCutoffDate;

    /**
     * 当时持仓披露日
     */
    private LocalDate holdingDisclosureDate;

    /**
     * 预估来源，SNAPSHOT 或 RECOMPUTED
     */
    private String estimateSource;

    /**
     * 本条的说明文字，每条一句
     */
    private List<String> remarks;

    private static final long serialVersionUID = 1L;
}

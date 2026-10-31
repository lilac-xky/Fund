package com.lilac.domain.vo;

import com.lilac.enums.IndicatorSignal;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 单只标的的技术指标视图，三指标数值与参数一并给出，便于与行情软件核对
 */
@Data
public class IndicatorVO implements Serializable {

    /**
     * 带交易所前缀的完整代码
     */
    private String symbol;

    /**
     * 品种业务主键，按品种查询时有值
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
     * RSI 当前值
     */
    private BigDecimal rsi;

    /**
     * KDJ 的 K 值
     */
    private BigDecimal k;

    /**
     * KDJ 的 D 值
     */
    private BigDecimal d;

    /**
     * KDJ 的 J 值
     */
    private BigDecimal j;

    /**
     * MACD 快慢线差值 DIF
     */
    private BigDecimal dif;

    /**
     * MACD 信号线 DEA
     */
    private BigDecimal dea;

    /**
     * MACD 柱，按 (DIF−DEA)×2 口径
     */
    private BigDecimal macdBar;

    /**
     * 触发的信号
     */
    private List<IndicatorSignal> signals;

    /**
     * 信号中文名，逐条可读
     */
    private List<String> signalLabels;

    /**
     * 本次计算所用参数的中文摘要，页面必须与之一致
     */
    private String paramSummary;

    /**
     * 最新日线日期
     */
    private LocalDate barDate;

    /**
     * 参与计算的日线根数
     */
    private Integer samples;

    /**
     * 日线是否前复权
     */
    private Boolean adjusted;

    /**
     * 日线取数来源
     */
    private String source;

    /**
     * 是否算出全部三个指标
     */
    private Boolean complete;

    /**
     * 是否命中缓存
     */
    private Boolean cached;

    /**
     * 计算说明，逐条带具体数值
     */
    private List<String> remarks;

    /**
     * 数据不足或无法计算的原因
     */
    private String unavailableReason;

    private static final long serialVersionUID = 1L;
}

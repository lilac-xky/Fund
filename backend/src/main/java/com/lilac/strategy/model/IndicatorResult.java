package com.lilac.strategy.model;

import com.lilac.enums.IndicatorSignal;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 一只标的的技术指标计算结果，含三指标的当前值与触发的信号
 */
@Data
public class IndicatorResult implements Serializable {

    /**
     * 6 位代码或带前缀完整代码
     */
    private String symbol;

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
     * 上一根的 DIF，判断金叉死叉用
     */
    private BigDecimal previousDif;

    /**
     * 上一根的 DEA
     */
    private BigDecimal previousDea;

    /**
     * 最新日线的日期
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
     * 触发的信号，可能为空
     */
    private List<IndicatorSignal> signals = new ArrayList<>();

    /**
     * 计算说明，逐条带具体数值
     */
    private List<String> remarks = new ArrayList<>();

    /**
     * 数据不足或无法计算时的原因
     */
    private String unavailableReason;

    /**
     * 是否算出了全部三个指标
     *
     * @return 算出返回 true
     */
    public boolean complete() {
        return rsi != null && k != null && d != null && dif != null && dea != null;
    }

    private static final long serialVersionUID = 1L;
}

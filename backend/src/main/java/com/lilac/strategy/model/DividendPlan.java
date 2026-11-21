package com.lilac.strategy.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 红利策略参数，按标的各存一套，金额单位元，股息率阈值单位百分点
 */
@Data
public class DividendPlan {

    /**
     * 所属品种业务主键
     */
    private String varietyKey;

    /**
     * 所属品种名称，供页面显示这套参数管的是哪只
     */
    private String varietyName;

    /**
     * 基准仓位金额，单位元
     */
    private BigDecimal baseAmount;

    /**
     * 保底分红金额，单位元，减仓后剩余金额不得低于该值
     */
    private BigDecimal floorAmount;

    /**
     * 加仓股息率阈值，单位百分点
     */
    private BigDecimal addYield;

    /**
     * 减仓股息率阈值，单位百分点
     */
    private BigDecimal reduceYield;

    /**
     * 参数问题清单，为空表示可用于出建议
     */
    private List<String> issues = new ArrayList<>();

    /**
     * 参数提示，如「正在沿用旧版共用参数」；不影响参数是否可用
     */
    private List<String> notes = new ArrayList<>();

    /**
     * 参数是否齐全且互相自洽
     *
     * @return 可用返回 true
     */
    public boolean complete() {
        return issues.isEmpty() && baseAmount != null && floorAmount != null
                && addYield != null && reduceYield != null;
    }
}

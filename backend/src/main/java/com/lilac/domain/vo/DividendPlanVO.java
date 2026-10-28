package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 红利策略参数视图，附问题清单供设置页提示不合法组合
 */
@Data
public class DividendPlanVO implements Serializable {

    /**
     * 基准仓位金额，单位元
     */
    private BigDecimal baseAmount;

    /**
     * 保底分红金额，单位元
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
     * 参数是否齐全且自洽
     */
    private Boolean usable;

    /**
     * 参数问题清单，可用时为空
     */
    private List<String> issues;

    private static final long serialVersionUID = 1L;
}

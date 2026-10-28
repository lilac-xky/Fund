package com.lilac.domain.dto.dividend;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 红利策略参数保存请求，金额单位元，股息率阈值为百分点
 */
@Data
public class DividendParamSaveRequest implements Serializable {

    /**
     * 基准仓位金额，单位元
     */
    private BigDecimal baseAmount;

    /**
     * 保底分红金额，单位元，不得高于基准仓位
     */
    private BigDecimal floorAmount;

    /**
     * 加仓股息率阈值，单位百分点
     */
    private BigDecimal addYield;

    /**
     * 减仓股息率阈值，单位百分点，必须低于加仓阈值
     */
    private BigDecimal reduceYield;

    private static final long serialVersionUID = 1L;
}

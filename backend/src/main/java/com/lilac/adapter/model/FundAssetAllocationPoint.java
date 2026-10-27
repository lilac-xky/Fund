package com.lilac.adapter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 基金季报披露的仓位快照，股票、债券、现金占净比
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundAssetAllocationPoint {

    /**
     * 报告期截止日，为季末最后一个自然日
     */
    private LocalDate reportDate;

    /**
     * 股票占净比，单位百分点
     */
    private BigDecimal stockRatio;

    /**
     * 债券占净比，单位百分点
     */
    private BigDecimal bondRatio;

    /**
     * 现金占净比，单位百分点
     */
    private BigDecimal cashRatio;
}

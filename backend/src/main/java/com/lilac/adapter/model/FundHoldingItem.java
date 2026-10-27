package com.lilac.adapter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 披露持仓中的一只标的
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundHoldingItem {

    /**
     * 证券代码，港股为 5 位数字
     */
    private String code;

    /**
     * 证券名称
     */
    private String name;

    /**
     * 占净值比例，单位百分点
     */
    private BigDecimal weight;
}

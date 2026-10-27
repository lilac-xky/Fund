package com.lilac.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 持仓明细中的一只标的
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HoldingItemVO implements Serializable {

    /**
     * 证券代码
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

    private static final long serialVersionUID = 1L;
}

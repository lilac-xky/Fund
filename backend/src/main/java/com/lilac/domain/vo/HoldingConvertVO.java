package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 基金转换结果，转出与转入两个品种的变动后状态成对返回
 */
@Data
public class HoldingConvertVO implements Serializable {

    /**
     * 转出品种的变动后剩余份额
     */
    private HoldingVarietyVO fromVariety;

    /**
     * 转入品种的变动后持有概况
     */
    private HoldingVarietyVO toVariety;

    /**
     * 转出只数
     */
    private Integer outCount;

    /**
     * 转入只数
     */
    private Integer inCount;

    /**
     * 转换说明，如「已转出 100 份、转入 95.24 份」
     */
    private String note;

    private static final long serialVersionUID = 1L;
}

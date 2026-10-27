package com.lilac.adapter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 基金全集中的一行，只保留消歧用到的代码、名称与类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundCatalogItem {

    /**
     * 6 位基金代码
     */
    private String code;

    /**
     * 基金简称
     */
    private String name;

    /**
     * 天天基金原始类型，如 混合型-灵活
     */
    private String type;
}

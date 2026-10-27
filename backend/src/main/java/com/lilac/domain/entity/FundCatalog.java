package com.lilac.domain.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 基金全集缓存，按 6 位代码存天天基金的名称与类型，供消歧查表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "fund_catalog", camelToUnderline = false, comment = "基金全集缓存，供代码消歧查表")
public class FundCatalog extends BaseEntity {

    /**
     * 6 位基金代码
     */
    private String fundCode;

    /**
     * 基金简称
     */
    private String fundName;

    /**
     * 天天基金原始类型，如 混合型-灵活
     */
    private String fundType;
}

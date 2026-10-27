package com.lilac.domain.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 基金历史净值，一天一基金一行，供指标计算与晚间校准复用
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "fund_net_worth", camelToUnderline = false, comment = "基金历史净值序列，可从上游重建")
public class FundNetWorth extends BaseEntity {

    /**
     * 6 位基金代码
     */
    private String fundCode;

    /**
     * 净值日期
     */
    private LocalDate netWorthDate;

    /**
     * 单位净值
     */
    private BigDecimal unitNetWorth;

    /**
     * 当日净值回报，单位百分点
     */
    private BigDecimal equityReturn;
}

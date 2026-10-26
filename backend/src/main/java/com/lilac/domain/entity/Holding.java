package com.lilac.domain.entity;

import com.lilac.enums.HoldingStatus;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 持仓记录，一条记录对应一个品种的一次建仓
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "holding", camelToUnderline = false, comment = "持仓记录，一条记录对应一个品种的一次建仓")
public class Holding extends BaseEntity {

    /**
     * 逻辑删除标记，0 正常 1 已删除
     */
    @Column(isLogicDelete = true)
    private Integer deleted;

    /**
     * 所持品种的业务主键，与品种注册表 varietyKey 对应
     */
    private String varietyKey;

    /**
     * 持有份额
     */
    private BigDecimal shares;

    /**
     * 成本价，单位净值或成交价
     */
    private BigDecimal costPrice;

    /**
     * 建仓日期
     */
    private LocalDate openDate;

    /**
     * 持仓状态
     */
    private HoldingStatus status;
}

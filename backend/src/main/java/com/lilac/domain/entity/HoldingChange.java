package com.lilac.domain.entity;

import com.lilac.enums.HoldingChangeType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 持仓变动历史，买入与卖出逐笔留存
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "holding_change", camelToUnderline = false, comment = "持仓变动历史，买入与卖出逐笔留存，不做覆盖与删除")
public class HoldingChange extends BaseEntity {

    /**
     * 所持品种的业务主键，与品种注册表 varietyKey 对应
     */
    private String varietyKey;

    /**
     * 变动类型，买入或卖出
     */
    private HoldingChangeType changeType;

    /**
     * 本次变动份额，以正数记录
     */
    private BigDecimal shares;

    /**
     * 成交单价，买入取成本价，卖出取被扣份额的加权成本
     */
    private BigDecimal unitPrice;

    /**
     * 变动后该品种合计份额
     */
    private BigDecimal afterShares;

    /**
     * 备注，买入带身份回显，卖出带扣减说明
     */
    private String note;
}

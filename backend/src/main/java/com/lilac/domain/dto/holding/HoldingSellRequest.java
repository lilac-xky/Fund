package com.lilac.domain.dto.holding;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 持仓卖出请求，按先进先出扣减已有持仓
 */
@Data
public class HoldingSellRequest implements Serializable {

    /**
     * 所持品种的业务主键，如 OF:000001
     */
    private String varietyKey;

    /**
     * 卖出份额，必须为正数且不超过当前持有合计；与 amount 二选一
     */
    private BigDecimal shares;

    /**
     * 卖出金额，元；支付宝订单只有金额时用它，份额按卖出日净值反算
     */
    private BigDecimal amount;

    /**
     * 卖出业务日期，为空时取当日；补录早前的卖出用它还原真实顺序
     */
    private LocalDate tradeDate;

    /**
     * 卖出净值，元/份；补录时按支付宝订单填写，留空按 FIFO 加权成本记账
     */
    private BigDecimal unitPrice;

    /**
     * 来源交易号，账单导入时带上，用于跨次导入去重
     */
    private String tradeNo;

    /**
     * 备注，账单导入的转换转出侧用它写明「由转换产生」，手工卖出留空
     */
    private String note;

    private static final long serialVersionUID = 1L;
}

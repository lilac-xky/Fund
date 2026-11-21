package com.lilac.strategy.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 红利阶梯的一档：股息率不低于该值时，这只基金应持有多少元
 */
@Data
public class DividendLadderRung {

    /**
     * 该档的股息率门槛，单位百分点；股息率不低于它时按住金额持有
     */
    private BigDecimal yield;

    /**
     * 该档对应的应持有金额，单位元；最低一档即保底，永远不卖的底仓
     */
    private BigDecimal amount;
}

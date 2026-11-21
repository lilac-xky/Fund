package com.lilac.strategy.model;

import com.lilac.enums.DividendAction;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次加减仓判断的结论，金额单位元并已取整，目标金额取自命中的阶梯档
 */
@Data
public class DividendDecision {

    /**
     * 动作，加仓、减仓、持有或不给动作
     */
    private DividendAction action;

    /**
     * 命中的阶梯档位股息率，单位百分点；不给动作时为 null
     */
    private BigDecimal hitYield;

    /**
     * 该档对应的目标持有金额，单位元
     */
    private BigDecimal targetAmount;

    /**
     * 当前持有金额，单位元
     */
    private BigDecimal currentAmount;

    /**
     * 建议买卖金额，带方向由 action 表达，不为负
     */
    private BigDecimal suggestAmount;

    /**
     * 买卖之后的持有金额，其余动作为 null
     */
    private BigDecimal remainingAmount;

    /**
     * 是否因命中最低一档（保底）而放弃了减仓
     */
    private Boolean floorProtected;

    /**
     * 本轮是否压制了同标的的网格提醒
     */
    private Boolean gridSuppressed;

    /**
     * 判断依据文字，逐条可读
     */
    private List<String> remarks = new ArrayList<>();
}

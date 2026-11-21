package com.lilac.strategy.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 红利策略参数，按基金各存一套：一条股息率阶梯，每档给出该持有多少元
 */
@Data
public class DividendPlan {

    /**
     * 所属品种业务主键
     */
    private String varietyKey;

    /**
     * 所属品种名称，供页面显示这套参数管的是哪只
     */
    private String varietyName;

    /**
     * 阶梯，按股息率降序存放；最低一档即保底
     */
    private List<DividendLadderRung> ladder = new ArrayList<>();

    /**
     * 参数问题清单，为空表示可用于出建议
     */
    private List<String> issues = new ArrayList<>();

    /**
     * 参数提示，如「正在沿用旧版共用参数」；不影响参数是否可用
     */
    private List<String> notes = new ArrayList<>();

    /**
     * 按股息率降序排好的阶梯，调用方拿到的一定是有序副本
     *
     * @return 阶梯副本，高股息率在前
     */
    public List<DividendLadderRung> sortedLadder() {
        List<DividendLadderRung> sorted = new ArrayList<>(ladder == null ? List.of() : ladder);
        sorted.sort(Comparator.comparing(DividendLadderRung::getYield, Comparator.reverseOrder()));
        return sorted;
    }

    /**
     * 最高一档的应持有金额，即这只基金的仓位上限
     *
     * @return 金额，阶梯为空返回 null
     */
    public BigDecimal topAmount() {
        List<DividendLadderRung> sorted = sortedLadder();
        return sorted.isEmpty() ? null : sorted.get(0).getAmount();
    }

    /**
     * 最低一档的应持有金额，即永远不卖的底仓
     *
     * @return 金额，阶梯为空返回 null
     */
    public BigDecimal bottomAmount() {
        List<DividendLadderRung> sorted = sortedLadder();
        return sorted.isEmpty() ? null : sorted.get(sorted.size() - 1).getAmount();
    }

    /**
     * 参数是否齐全且互相自洽
     *
     * @return 可用返回 true
     */
    public boolean complete() {
        return issues.isEmpty() && ladder != null && ladder.size() >= 2;
    }
}

package com.lilac.adapter.fund;

import com.lilac.enums.FundCategory;
import org.springframework.stereotype.Component;

/**
 * 基金类型推断器，把天天基金的三类原始类型与名称关键词折算成 PRD 的六类
 */
@Component
public class FundCategoryResolver {

    private static final String[] GOLD_KEYWORDS = {"黄金", "贵金属", "上海金"};
    private static final String[] GOLD_EQUITY_KEYWORDS = {"黄金股", "黄金产业", "金矿"};
    private static final String[] DIVIDEND_KEYWORDS = {"红利", "股息"};
    private static final String[] REFORM_KEYWORDS = {"改革", "混改"};

    /**
     * 推断基金类型，无法确定时返回 UNKNOWN 交由使用者确认
     *
     * @param rawType        天天基金原始类型，如 混合型-灵活
     * @param fundName       基金简称
     * @param exchangeTraded 是否场内交易
     * @return 基金类型
     */
    public FundCategory resolve(String rawType, String fundName, boolean exchangeTraded) {
        String type = rawType == null ? "" : rawType.trim();
        String name = fundName == null ? "" : fundName;
        if (type.startsWith("债券型") || "指数型-固收".equals(type)) {
            return FundCategory.BOND;
        }
        if (type.startsWith("混合型")) {
            return containsAny(name, DIVIDEND_KEYWORDS) && !containsAny(name, REFORM_KEYWORDS)
                    ? FundCategory.DIVIDEND : FundCategory.MIXED;
        }
        if (type.startsWith("股票型")) {
            return containsAny(name, DIVIDEND_KEYWORDS) && !containsAny(name, REFORM_KEYWORDS)
                    ? FundCategory.DIVIDEND : FundCategory.STOCK;
        }
        if (type.startsWith("指数型")) {
            return resolveIndexFund(type, name, exchangeTraded);
        }
        return FundCategory.UNKNOWN;
    }

    /**
     * 指数型内部再分黄金、红利、固收与普通权益
     *
     * @param type           原始类型
     * @param name           基金简称
     * @param exchangeTraded 是否场内交易
     * @return 基金类型
     */
    private FundCategory resolveIndexFund(String type, String name, boolean exchangeTraded) {
        if ("指数型-其他".equals(type)) {
            return containsAny(name, GOLD_KEYWORDS) && !containsAny(name, GOLD_EQUITY_KEYWORDS)
                    ? FundCategory.GOLD : FundCategory.UNKNOWN;
        }
        if (containsAny(name, GOLD_EQUITY_KEYWORDS)) {
            return FundCategory.STOCK;
        }
        if (containsAny(name, GOLD_KEYWORDS)) {
            // QDII 黄金跟踪境外金价并带汇率，不能套用国内黄金 ETF 代理
            return type.startsWith("QDII") ? FundCategory.UNKNOWN : FundCategory.GOLD;
        }
        if (containsAny(name, DIVIDEND_KEYWORDS)) {
            return FundCategory.DIVIDEND;
        }
        return exchangeTraded ? FundCategory.ETF : FundCategory.STOCK;
    }

    /**
     * 判断名称是否命中任一关键词
     *
     * @param name     基金简称
     * @param keywords 关键词数组
     * @return 命中返回 true
     */
    public boolean containsAny(String name, String[] keywords) {
        for (String keyword : keywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 名称是否带黄金关键词
     *
     * @param name 基金简称
     * @return 命中返回 true
     */
    public boolean isGold(String name) {
        return containsAny(name, GOLD_KEYWORDS) && !containsAny(name, GOLD_EQUITY_KEYWORDS);
    }

    /**
     * 名称是否指向黄金股而非黄金本身
     *
     * @param name 基金简称
     * @return 命中返回 true
     */
    public boolean isGoldEquity(String name) {
        return containsAny(name, GOLD_EQUITY_KEYWORDS);
    }
}

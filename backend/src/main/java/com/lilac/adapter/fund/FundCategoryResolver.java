package com.lilac.adapter.fund;

import com.lilac.enums.FundCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * 列出名称里命中的全部关键词，供页面说明推断依据
     *
     * @param name 基金简称
     * @return 命中的关键词
     */
    public List<String> hintsOf(String name) {
        List<String> hints = new ArrayList<>();
        String value = name == null ? "" : name;
        addHits(hints, value, GOLD_KEYWORDS);
        addHits(hints, value, GOLD_EQUITY_KEYWORDS);
        addHits(hints, value, DIVIDEND_KEYWORDS);
        addHits(hints, value, REFORM_KEYWORDS);
        return hints;
    }

    /**
     * 把命中的关键词加入清单
     *
     * @param target   收集清单
     * @param name     基金简称
     * @param keywords 关键词数组
     */
    private void addHits(List<String> target, String name, String[] keywords) {
        for (String keyword : keywords) {
            if (name.contains(keyword) && !target.contains(keyword)) {
                target.add(keyword);
            }
        }
    }

    /**
     * 判断是否属于「黄金或红利无法自动识别」的高风险情形，给出可读原因
     *
     * @param rawType  原始类型
     * @param fundName 基金简称
     * @return 原因，确认不了返回 null
     */
    public String pendingReasonOf(String rawType, String fundName) {
        String type = rawType == null ? "" : rawType.trim();
        String name = fundName == null ? "" : fundName;
        if (type.startsWith("QDII") && isGold(name)) {
            // QDII 黄金跟踪境外金价并带汇率，不能套用国内黄金 ETF 代理
            return "QDII 黄金跟踪境外金价并带汇率折算，不能套用国内黄金 ETF 作代理，请手工确认类型";
        }
        if ("指数型-其他".equals(type) && !isGold(name) && !containsAny(name, DIVIDEND_KEYWORDS)) {
            return "指数型-其他既没命中黄金关键词也没命中红利关键词，请手工确认类型与跟踪指数";
        }
        if (containsAny(name, GOLD_KEYWORDS) && isGoldEquity(name)) {
            // isGold 本身已排除黄金股，这里要直接看是否同时出现两类词，否则永远判不到
            return "名称同时含「黄金」与「黄金股/金矿」类词，无法判断是跟踪金价还是黄金股";
        }
        if (type.isEmpty()) {
            return "上游没有给出类型，只能按名称猜，请手工确认";
        }
        return null;
    }
}

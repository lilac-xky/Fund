package com.lilac.engine;

import com.lilac.adapter.fund.BenchmarkIndexRegistry;
import com.lilac.adapter.model.MarketSymbol;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代理标的解析器，把基金折算成对标指数、黄金 ETF 或债券 ETF 的行情代码
 */
@Slf4j
@Component
public class BenchmarkResolver {

    // 基金简称里出现的 6 位指数代码，如 中证1000指数(000852)
    private static final Pattern CODE_IN_NAME = Pattern.compile("\\((\\d{6})\\)");

    private final EstimateThresholds thresholds;

    /**
     * 注入阈值配置，用于取黄金与债券的代理标的
     *
     * @param thresholds 阈值配置
     */
    public BenchmarkResolver(EstimateThresholds thresholds) {
        this.thresholds = thresholds;
    }

    /**
     * 解析一只基金的对标标的，解析不到时返回 null 交由引擎决定是否可预估
     *
     * @param variety     品种信息
     * @param rawFundType 天天基金原始类型
     * @return 带前缀完整代码，解析不到返回 null
     */
    public String resolve(VarietyVO variety, String rawFundType) {
        FundCategory category = variety.getCategory();
        if (category == FundCategory.GOLD) {
            return symbolOf(thresholds.getGoldProxyCode());
        }
        if (category == FundCategory.BOND) {
            return symbolOf(thresholds.getBondProxyCode());
        }
        // 场内 ETF 自身就是可交易标的，不需要找对标指数
        if (variety.getVarietyType() == VarietyType.ETF) {
            return symbolOf(variety.getVarietyCode());
        }
        String byTrackIndex = firstNonNull(variety.getTrackIndexCode(), byCodeInName(variety.getVarietyName()));
        if (byTrackIndex != null) {
            return symbolOf(byTrackIndex);
        }
        String byName = BenchmarkIndexRegistry.findByFundName(variety.getVarietyName());
        if (byName != null) {
            return symbolOf(byName);
        }
        if (rawFundType != null && rawFundType.startsWith("指数型")) {
            log.info("指数型基金未解析到对标指数 code={} name={}", variety.getVarietyCode(), variety.getVarietyName());
        }
        return null;
    }

    /**
     * 从基金名称里取出括号中的指数代码，用于名称与登记表都对不上的场合
     *
     * @param name 基金简称
     * @return 6 位指数代码，未命中返回 null
     */
    public String byCodeInName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Matcher matcher = CODE_IN_NAME.matcher(name);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 代码转行情标的完整代码，指数代码按号段推断市场，000 段由行情适配器补查另一市场
     *
     * @param code 6 位代码
     * @return 带前缀完整代码，空代码返回 null
     */
    public static String symbolOf(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return MarketSymbol.of(code.trim()).symbol();
        } catch (RuntimeException e) {
            log.warn("代理标的代码无法识别：{}", code);
            return null;
        }
    }

    /**
     * 取第一个非空值
     *
     * @param first  优先值
     * @param second 兜底值
     * @return 非空值，都为 null 返回 null
     */
    private String firstNonNull(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}

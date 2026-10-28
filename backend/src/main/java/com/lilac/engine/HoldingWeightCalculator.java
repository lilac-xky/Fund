package com.lilac.engine;

import com.lilac.adapter.model.ForexRate;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.model.MarketSymbol;
import com.lilac.adapter.model.QuoteStatus;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.domain.vo.HoldingItemVO;
import com.lilac.engine.model.WeightedOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 持仓加权计算器，按重仓股权重加权当日涨跌，残差按 0 计，港股先折算汇率
 */
@Slf4j
@Component
public class HoldingWeightCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 6;
    private static final int MAX_SUSPENDED_DETAIL = 5;

    /**
     * 加权计算一只基金当日的预估涨跌幅
     *
     * @param holdings 披露持仓
     * @param quotes   持仓标的行情，按带前缀完整代码索引
     * @param forex    港币兑人民币汇率，可为 null 表示按汇率不变处理
     * @return 加权结果
     */
    public WeightedOutcome weighted(List<HoldingItemVO> holdings, Map<String, MarketQuote> quotes,
                                    ForexRate forex) {
        WeightedOutcome outcome = new WeightedOutcome();
        if (holdings == null || holdings.isEmpty() || quotes == null || quotes.isEmpty()) {
            outcome.setDisclosedWeight(BigDecimal.ZERO);
            outcome.setUsedWeight(BigDecimal.ZERO);
            return outcome;
        }
        BigDecimal disclosed = BigDecimal.ZERO;
        BigDecimal used = BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        int excluded = 0;
        List<String> suspended = new ArrayList<>();
        for (HoldingItemVO item : holdings) {
            BigDecimal weight = item.getWeight() == null ? BigDecimal.ZERO : item.getWeight();
            disclosed = disclosed.add(weight);
            MarketQuote quote = quotes.get(symbolOf(item.getCode()));
            if (quote == null || !quote.usable()) {
                // 取不到行情或停牌的标的直接剔除，绝不当 0% 参与加权
                excluded++;
                if (quote != null && quote.getStatus() == QuoteStatus.SUSPENDED) {
                    addSuspended(suspended, item);
                }
                continue;
            }
            sum = sum.add(weight.multiply(cnyChange(quote, forex)));
            used = used.add(weight);
        }
        outcome.setDisclosedWeight(disclosed);
        outcome.setUsedWeight(used);
        outcome.setExcludedCount(excluded);
        outcome.setSuspendedSymbols(suspended.isEmpty() ? null : String.join("、", suspended));
        if (used.signum() <= 0) {
            return outcome;
        }
        // 残差 1 − Σw 是债券与现金，按 0 计，所以只累加已取到行情那部分的加权和
        outcome.setChange(sum.divide(HUNDRED, SCALE, RoundingMode.HALF_UP));
        return outcome;
    }

    /**
     * 港股收益按 (1 + r_hk)(1 + r_fx) − 1 折算人民币，A 股原样返回
     *
     * @param quote 标的行情
     * @param forex 汇率，为 null 或不可用时按汇率不变处理
     * @return 人民币计价涨跌幅，百分点
     */
    public BigDecimal cnyChange(MarketQuote quote, ForexRate forex) {
        BigDecimal change = quote.getChangePercent();
        if (change == null || quote.getSymbol() == null || !quote.getSymbol().hongKong()) {
            return change;
        }
        BigDecimal fxChange = forex != null && forex.usable() ? forex.getChangeRate() : null;
        return MarketQuoteAdapter.toCnyReturn(change, fxChange);
    }

    /**
     * 标的代码转行情完整代码，港股按 5 位、其余按号段推断市场
     *
     * @param code 持仓中的证券代码
     * @return 带前缀完整代码，无法识别时返回空串
     */
    public static String symbolOf(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        try {
            return MarketSymbol.of(code.trim()).symbol();
        } catch (RuntimeException e) {
            log.warn("持仓代码无法识别为行情标的：{}", code);
            return "";
        }
    }

    /**
     * 追加停牌标的说明，最多保留五只避免文案过长
     *
     * @param suspended 输出列表
     * @param item      持仓项
     */
    private void addSuspended(List<String> suspended, HoldingItemVO item) {
        if (suspended.size() < MAX_SUSPENDED_DETAIL) {
            suspended.add(item.getCode() + " " + item.getName());
        }
    }
}

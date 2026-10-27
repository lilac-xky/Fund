package com.lilac.adapter.quote;

import com.lilac.adapter.model.MarketHistory;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.model.MarketSymbol;

import java.util.List;
import java.util.Map;

/**
 * 行情数据源接口，每个实现只负责一种响应格式，回退与缓存由上层编排
 */
public interface QuoteSource {

    /**
     * 数据源名称，写入行情结果的 source 字段并用于日志
     *
     * @return 数据源名
     */
    String name();

    /**
     * 是否支持该标的，港股与北交所的支持面不同
     *
     * @param symbol 行情标的
     * @return 支持返回 true
     */
    boolean supports(MarketSymbol symbol);

    /**
     * 批量取当日行情，单个标的不得抛异常影响同批其他标的
     *
     * @param symbols 行情标的列表
     * @return 标的完整代码到行情的有序映射，未取到的标的不出现在结果里
     */
    Map<String, MarketQuote> fetchQuotes(List<MarketSymbol> symbols);

    /**
     * 取单只标的的日线
     *
     * @param symbol 行情标的
     * @param limit  返回根数
     * @return 日线序列
     */
    MarketHistory fetchHistory(MarketSymbol symbol, int limit);
}

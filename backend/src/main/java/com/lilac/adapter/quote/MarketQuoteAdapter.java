package com.lilac.adapter.quote;

import com.lilac.adapter.model.ForexRate;
import com.lilac.adapter.model.MarketHistory;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.model.MarketSymbol;
import com.lilac.adapter.model.QuoteStatus;
import com.lilac.adapter.probe.SourceEventRecorder;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 行情编排适配器，负责多源回退、批量拆分、缓存与可用性标注，是取行情的唯一入口
 */
@Slf4j
@Component
public class MarketQuoteAdapter {

    private final List<QuoteSource> sources;
    private final QuoteCache quoteCache;
    private final SinaForexSource sinaForexSource;
    private final SourceEventRecorder sourceEventRecorder;

    @Value("${fund.adapter.quote.batch-size:60}")
    private int batchSize;

    @Value("${fund.adapter.quote.history-limit:60}")
    private int historyLimit;

    /**
     * 按回退顺序注入数据源，腾讯在前、新浪在后
     *
     * @param sources             数据源列表
     * @param quoteCache          行情缓存
     * @param sinaForexSource     汇率源
     * @param sourceEventRecorder 取数留痕
     */
    public MarketQuoteAdapter(List<QuoteSource> sources, QuoteCache quoteCache,
                              SinaForexSource sinaForexSource, SourceEventRecorder sourceEventRecorder) {
        this.sources = sources;
        this.quoteCache = quoteCache;
        this.sinaForexSource = sinaForexSource;
        this.sourceEventRecorder = sourceEventRecorder;
    }

    /**
     * 批量取当日行情，逐个标的按「缓存 → 腾讯 → 新浪」回退，任一源失败自动切换
     *
     * @param rawCodes 裸代码或带前缀代码
     * @return 标的完整代码到行情的有序映射，取不到的标的以 UNAVAILABLE 返回
     */
    public Map<String, MarketQuote> quotes(List<String> rawCodes) {
        Map<String, MarketQuote> result = new LinkedHashMap<>();
        if (rawCodes == null || rawCodes.isEmpty()) {
            return result;
        }
        List<MarketSymbol> symbols = distinctSymbols(rawCodes);
        Map<String, MarketQuote> cached = readCache(symbols);
        result.putAll(cached);
        List<MarketSymbol> missing = symbols.stream()
                .filter(symbol -> !cached.containsKey(symbol.symbol()))
                .toList();
        LocalDateTime now = LocalDateTime.now();
        if (!missing.isEmpty()) {
            for (List<MarketSymbol> batch : partition(missing)) {
                result.putAll(fetchBatch(batch));
            }
        }
        resolveSiblings(result);
        return annotate(result, now);
    }

    /**
     * 对 000 段代码补查另一个市场：000300 这类上证指数按号段会被推到深市，深市取不到时改查沪市
     *
     * @param result 已取到的行情，原地替换
     */
    private void resolveSiblings(Map<String, MarketQuote> result) {
        List<Map.Entry<String, MarketQuote>> pending = result.entrySet().stream()
                .filter(entry -> entry.getValue().getStatus() == QuoteStatus.NO_DATA
                        || entry.getValue().getStatus() == QuoteStatus.UNAVAILABLE)
                .filter(entry -> entry.getValue().getSymbol().ambiguous())
                .toList();
        if (pending.isEmpty()) {
            return;
        }
        List<MarketSymbol> siblings = pending.stream()
                .map(entry -> entry.getValue().getSymbol().sibling())
                .filter(java.util.Objects::nonNull)
                .toList();
        if (siblings.isEmpty()) {
            return;
        }
        Map<String, MarketQuote> fetched = fetchBatch(siblings);
        for (Map.Entry<String, MarketQuote> entry : pending) {
            MarketSymbol sibling = entry.getValue().getSymbol().sibling();
            if (sibling == null) {
                continue;
            }
            MarketQuote quote = fetched.get(sibling.symbol());
            if (quote != null && quote.usable()) {
                // 补查成功时用另一市场的行情替换，key 保持调用方传入的形式
                log.info("代码 {} 在推断市场无行情，改查 {} 命中", entry.getKey(), sibling.symbol());
                MarketSymbol.rememberMarket(sibling.getCode(), sibling.getPrefix());
                result.put(entry.getKey(), quote);
            }
        }
    }    /**
     * 取单只标的的当日行情
     *
     * @param rawCode 裸代码或带前缀代码
     * @return 行情，永不返回 null
     */
    public MarketQuote quote(String rawCode) {
        MarketSymbol symbol = MarketSymbol.of(rawCode);
        return quotes(List.of(symbol.symbol())).getOrDefault(symbol.symbol(),
                MarketQuote.unavailable(symbol, QuoteStatus.UNAVAILABLE, "行情取数失败"));
    }

    /**
     * 判断当前是否处于交易时段，供批量结果标注「盘中 / 已收盘」
     *
     * @param rawCodes 本次请求的标的
     * @return 任一标的在交易时段内返回 true
     */
    public boolean tradingNow(List<String> rawCodes) {
        LocalDateTime now = LocalDateTime.now();
        return rawCodes.stream().map(MarketSymbol::of)
                .anyMatch(symbol -> TradingSession.isTrading(symbol, now));
    }

    /**
     * 取单只标的的日线，腾讯前复权在前，全部失败返回空序列
     *
     * @param rawCode 裸代码或带前缀代码
     * @param limit   返回根数，小于等于 0 时用配置默认值
     * @return 日线序列，全部源失败时根数为 0 且 adjusted=false
     */
    public MarketHistory history(String rawCode, int limit) {
        MarketSymbol symbol = MarketSymbol.of(rawCode);
        int rows = limit <= 0 ? historyLimit : limit;
        List<String> failures = new ArrayList<>();
        for (QuoteSource source : sources) {
            if (!source.supports(symbol)) {
                continue;
            }
            try {
                MarketHistory history = source.fetchHistory(symbol, rows);
                if (!history.enough(2)) {
                    failures.add(source.name() + ":根数不足");
                    // 根数不足多半是「该标的无日线」，与上游改版分开记，避免健康页误报结构变化
                    sourceEventRecorder.failure(source.name(), symbol.symbol(),
                            SourceEventRecorder.FailureKind.NO_DATA, "日线根数不足");
                    continue;
                }
                sourceEventRecorder.success(source.name(), symbol.symbol());
                return history;
            } catch (RuntimeException e) {
                failures.add(source.name() + ":" + e.getMessage());
                log.warn("日线取数失败 {} 来自 {}：{}", symbol.symbol(), source.name(), e.getMessage());
                sourceEventRecorder.failure(source.name(), symbol.symbol(), failureKindOf(e), e.getMessage());
            }
        }
        log.warn("日线全部数据源失败 {}：{}", symbol.symbol(), String.join("；", failures));
        MarketHistory empty = new MarketHistory();
        empty.setSymbol(symbol);
        empty.setBars(List.of());
        empty.setAdjusted(Boolean.FALSE);
        empty.setSource("none");
        empty.setFetchDate(LocalDateTime.now().toLocalDate());
        return empty;
    }

    /**
     * 取港币兑人民币汇率，港股持仓收益折算用
     *
     * @return 汇率，可用性由调用方判断
     */
    public ForexRate hkdCny() {
        return sinaForexSource.fetchHkdCny();
    }

    /**
     * 按一个批次取行情并写缓存，前一个源取不到的标的由下一个源补
     *
     * @param batch 本批标的
     * @return 本批行情
     */
    private Map<String, MarketQuote> fetchBatch(List<MarketSymbol> batch) {
        Map<String, MarketQuote> fetched = new LinkedHashMap<>();
        Map<String, String> noDataReasons = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        for (QuoteSource source : sources) {
            // 前一个源没取到或明确无行情的标的，交给下一个源再试
            List<MarketSymbol> candidates = batch.stream()
                    .filter(symbol -> needsAnotherSource(fetched.get(symbol.symbol())))
                    .filter(source::supports)
                    .toList();
            if (candidates.isEmpty()) {
                continue;
            }
            try {
                Map<String, MarketQuote> part = source.fetchQuotes(candidates);
                part.forEach((key, quote) -> {
                    fetched.put(key, quote);
                    if (quote.getStatus() == QuoteStatus.NO_DATA) {
                        noDataReasons.put(key, quote.getUnavailableReason());
                    }
                    if (quote.usable()) {
                        quoteCache.save(quote);
                        sourceEventRecorder.success(source.name(), key);
                    } else {
                        // 取到响应但无可用行情，多半是上游字段变了或标的停牌，必须分开记
                        String kind = quote.getStatus() == QuoteStatus.NO_DATA
                                || quote.getStatus() == QuoteStatus.SUSPENDED
                                ? SourceEventRecorder.FailureKind.NO_DATA
                                : SourceEventRecorder.FailureKind.STRUCTURE;
                        sourceEventRecorder.failure(source.name(), key, kind,
                                quote.getUnavailableReason());
                    }
                });
            } catch (RuntimeException e) {
                // 一个源整体失效不影响后续源，这正是多源回退要保证的
                failures.add(source.name() + ":" + e.getMessage());
                log.warn("行情数据源整体失败 {}，切换到下一个源：{}", source.name(), e.getMessage());
                sourceEventRecorder.failure(source.name(), String.join(",", symbolsOf(candidates)),
                        failureKindOf(e), e.getMessage());
            }
        }
        for (MarketSymbol symbol : batch) {
            fetched.computeIfAbsent(symbol.symbol(), key -> {
                // 各源都取不到时按「数据源无该标的」而不是「服务宕机」上报，920xxx 北交所与退市股属于这一类
                String reason = noDataReasons.get(key);
                if (reason != null) {
                    return MarketQuote.unavailable(symbol, QuoteStatus.NO_DATA, reason);
                }
                return MarketQuote.unavailable(symbol, QuoteStatus.UNAVAILABLE,
                        "全部行情源不可用 " + String.join("；", failures));
            });
        }
        return fetched;
    }

    /**
     * 按异常归类失败类型，结构变化与网络问题要分开统计
     *
     * @param e 异常
     * @return 失败类型
     */
    private String failureKindOf(RuntimeException e) {
        if (e instanceof BusinessException business
                && business.getCode() != null
                && business.getCode().equals(HttpsCodeEnum.DATA_SOURCE_BROKEN.getCode())) {
            return SourceEventRecorder.FailureKind.STRUCTURE;
        }
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("限流") || message.contains("验证页") || message.contains("拒绝访问")) {
            return SourceEventRecorder.FailureKind.THROTTLED;
        }
        return SourceEventRecorder.FailureKind.NETWORK;
    }

    /**
     * 取本批标的的完整代码，供留痕记录
     *
     * @param symbols 标的列表
     * @return 逗号分隔的代码
     */
    private String symbolsOf(List<MarketSymbol> symbols) {
        return symbols.stream().map(MarketSymbol::symbol).limit(5).collect(Collectors.joining(","));
    }

    /**
     * 判断某标的是否还需要换源重试：没取到、无行情、或整源不可用都需要
     *
     * @param quote 已取到的行情，可能为 null
     * @return 需要换源返回 true
     */
    private boolean needsAnotherSource(MarketQuote quote) {
        if (quote == null) {
            return true;
        }
        // 停牌是标的自身的真实状态，换源也不会变成有成交
        return quote.getStatus() == QuoteStatus.NO_DATA || quote.getStatus() == QuoteStatus.UNAVAILABLE;
    }

    /**
     * 读缓存，只认可用于预估的行情
     *
     * @param symbols 标的列表
     * @return 命中的行情
     */
    private Map<String, MarketQuote> readCache(List<MarketSymbol> symbols) {
        Map<String, MarketQuote> cached = new LinkedHashMap<>();
        for (MarketSymbol symbol : symbols) {
            quoteCache.read(symbol.symbol()).filter(MarketQuote::usable)
                    .ifPresent(quote -> cached.put(symbol.symbol(), quote));
        }
        return cached;
    }

    /**
     * 统一标注实时性：数据日期不是今天即标为非实时，页面据此显示「上一交易日」
     *
     * @param result 行情集合
     * @param now    取数时间
     * @return 同一集合
     */
    private Map<String, MarketQuote> annotate(Map<String, MarketQuote> result, LocalDateTime now) {
        result.values().forEach(quote -> {
            boolean todayData = quote.getTradeDate() != null && quote.getTradeDate().equals(now.toLocalDate());
            quote.setRealtime(todayData);
        });
        long usable = result.values().stream().filter(MarketQuote::usable).count();
        long stale = result.values().stream().filter(quote -> Boolean.FALSE.equals(quote.getRealtime())).count();
        log.info("行情取数完成，共 {} 个标的，可用 {} 个，非当日 {} 个", result.size(), usable, stale);
        return result;
    }

    /**
     * 按批大小切分请求，避免一次请求过多被限流
     *
     * @param symbols 标的列表
     * @return 分批结果
     */
    private List<List<MarketSymbol>> partition(List<MarketSymbol> symbols) {
        List<List<MarketSymbol>> batches = new ArrayList<>();
        int size = batchSize <= 0 ? 60 : batchSize;
        for (int from = 0; from < symbols.size(); from += size) {
            batches.add(symbols.subList(from, Math.min(from + size, symbols.size())));
        }
        return batches;
    }

    /**
     * 去重并保持顺序，同一标的只请求一次
     *
     * @param rawCodes 裸代码或带前缀代码
     * @return 标的列表
     */
    private List<MarketSymbol> distinctSymbols(List<String> rawCodes) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<MarketSymbol> symbols = new ArrayList<>(rawCodes.size());
        for (String raw : rawCodes) {
            MarketSymbol symbol = MarketSymbol.of(raw);
            if (seen.add(symbol.symbol())) {
                symbols.add(symbol);
            }
        }
        return symbols;
    }

    /**
     * 取缓存中某只标的的行情，供排障使用
     *
     * @param symbol 标的完整代码
     * @return 行情
     */
    public Optional<MarketQuote> cached(String symbol) {
        return quoteCache.read(symbol);
    }

    /**
     * 按 (1 + 标的涨跌幅)(1 + 汇率变动) − 1 折算港股人民币收益
     *
     * @param hkChangePercent 港币计价涨跌幅，百分点
     * @param fxChangeRate    汇率变动，小数
     * @return 人民币计价涨跌幅，百分点
     */
    public static BigDecimal toCnyReturn(BigDecimal hkChangePercent, BigDecimal fxChangeRate) {
        if (hkChangePercent == null) {
            return null;
        }
        BigDecimal fx = fxChangeRate == null ? BigDecimal.ZERO : fxChangeRate;
        BigDecimal hk = hkChangePercent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        return hk.add(fx).add(hk.multiply(fx)).multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);
    }
}

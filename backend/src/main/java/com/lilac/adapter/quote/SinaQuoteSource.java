package com.lilac.adapter.quote;

import com.lilac.adapter.http.TextFetcher;
import com.lilac.adapter.model.MarketBar;
import com.lilac.adapter.model.MarketHistory;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.model.MarketSymbol;
import com.lilac.adapter.model.QuoteStatus;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新浪行情源，作为腾讯被限流时的备用源；日线不复权，取到后必须标注口径
 */
@Slf4j
@Component
@Order(2)
public class SinaQuoteSource implements QuoteSource {

    private static final String NAME = "sina";
    private static final Charset GBK = Charset.forName("GBK");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("var hq_str_([a-z]{2}\\d{5,6})=\"([^\"]*)\"");
    private static final Pattern KLINE_PATTERN = Pattern.compile(
            "\\{\"day\":\"(\\d{4}-\\d{2}-\\d{2})\",\"open\":\"([^\"]*)\",\"high\":\"([^\"]*)\","
                    + "\"low\":\"([^\"]*)\",\"close\":\"([^\"]*)\",\"volume\":\"([^\"]*)\"\\}");
    private static final int A_NAME = 0;
    private static final int A_PREV_CLOSE = 2;
    private static final int A_PRICE = 3;
    private static final int A_VOLUME = 8;
    private static final int A_DATE = 30;
    private static final int A_TIME = 31;
    private static final int HK_NAME = 1;
    private static final int HK_OPEN = 2;
    private static final int HK_PREV_CLOSE = 3;
    private static final int HK_PRICE = 6;
    private static final int HK_CHANGE_PERCENT = 8;
    private static final int HK_DATE = 17;
    private static final int HK_TIME = 18;

    private final TextFetcher textFetcher;
    private final String quoteUrl;
    private final String klineUrl;
    private final String referer;

    /**
     * 注入取数封装与新浪行情地址
     *
     * @param textFetcher 文本取数接口
     * @param quoteUrl    实时行情地址
     * @param klineUrl    日线地址
     * @param referer     新浪接口不带 Referer 返回 Forbidden
     */
    public SinaQuoteSource(TextFetcher textFetcher,
                           @Value("${fund.adapter.sina.quote-url}") String quoteUrl,
                           @Value("${fund.adapter.sina.kline-url}") String klineUrl,
                           @Value("${fund.adapter.sina.referer}") String referer) {
        this.textFetcher = textFetcher;
        this.quoteUrl = quoteUrl;
        this.klineUrl = klineUrl;
        this.referer = referer;
    }

    /**
     * 数据源名称
     *
     * @return sina
     */
    @Override
    public String name() {
        return NAME;
    }

    /**
     * 新浪支持 A 股、指数、港股与北交所实时行情
     *
     * @param symbol 行情标的
     * @return 始终支持
     */
    @Override
    public boolean supports(MarketSymbol symbol) {
        return symbol != null;
    }

    /**
     * 批量取当日行情，单只异常不影响同批
     *
     * @param symbols 行情标的列表
     * @return 标的完整代码到行情的有序映射
     */
    @Override
    public Map<String, MarketQuote> fetchQuotes(List<MarketSymbol> symbols) {
        String url = quoteUrl + String.join(",", symbols.stream().map(MarketSymbol::symbol).toList());
        String text = textFetcher.getText(url, GBK, Map.of("Referer", referer));
        if (text.contains("Forbidden")) {
            throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "新浪行情拒绝访问，疑似缺少 Referer 或被限流");
        }
        Map<String, MarketQuote> result = new LinkedHashMap<>();
        for (MarketSymbol symbol : symbols) {
            try {
                MarketQuote quote = parseQuote(symbol, text);
                if (quote != null) {
                    result.put(symbol.symbol(), quote);
                }
            } catch (RuntimeException e) {
                log.warn("新浪行情解析跳过 {}：{}", symbol.symbol(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * 取单只标的日线，新浪日线不复权，返回对象里 adjusted 为 false
     *
     * @param symbol 行情标的
     * @param limit  返回根数
     * @return 日线序列
     */
    @Override
    public MarketHistory fetchHistory(MarketSymbol symbol, int limit) {
        String url = klineUrl.replace("{symbol}", symbol.symbol()).replace("{limit}", String.valueOf(limit));
        String text = textFetcher.getText(url, StandardCharsets.UTF_8);
        return parseHistory(symbol, text);
    }

    /**
     * 解析单只实时行情，未命中返回 null
     *
     * @param symbol 行情标的
     * @param text   响应原文
     * @return 行情，无该标的时返回 null
     */
    MarketQuote parseQuote(MarketSymbol symbol, String text) {
        Matcher matcher = QUOTE_PATTERN.matcher(text);
        while (matcher.find()) {
            if (matcher.group(1).equals(symbol.symbol())) {
                return toQuote(symbol, matcher.group(2));
            }
        }
        return null;
    }

    /**
     * 按 A 股与港股两种字段布局解析，字段缺失即视作无行情
     *
     * @param symbol 行情标的
     * @param body   引号内的字段串
     * @return 行情
     */
    private MarketQuote toQuote(MarketSymbol symbol, String body) {
        if (body == null || body.isBlank()) {
            return MarketQuote.unavailable(symbol, QuoteStatus.NO_DATA, "新浪行情无数据");
        }
        String[] fields = body.split(",", -1);
        return symbol.hongKong() ? toHkQuote(symbol, fields) : toAShareQuote(symbol, fields);
    }

    /**
     * A 股与指数字段：名称、开盘、昨收、现价、最高、最低、买一、卖一、成交量…日期、时间
     *
     * @param symbol 行情标的
     * @param fields 字段数组
     * @return 行情
     */
    private MarketQuote toAShareQuote(MarketSymbol symbol, String[] fields) {
        if (fields.length <= A_TIME) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN, "新浪行情字段数不足 " + symbol.symbol());
        }
        BigDecimal price = toDecimal(fields[A_PRICE]);
        if (price == null || price.signum() <= 0) {
            return MarketQuote.unavailable(symbol, QuoteStatus.NO_DATA, "新浪行情无有效价格");
        }
        MarketQuote quote = new MarketQuote();
        quote.setSymbol(symbol);
        quote.setName(fields[A_NAME]);
        quote.setPrice(price);
        quote.setPreviousClose(toDecimal(fields[A_PREV_CLOSE]));
        quote.setChangePercent(changeByClose(price, quote.getPreviousClose()));
        quote.setTradeDate(toDate(fields[A_DATE]));
        quote.setQuoteTime(toDateTime(fields[A_DATE], fields[A_TIME]));
        quote.setSource(NAME);
        quote.setAdjusted(Boolean.TRUE);
        quote.setRealtime(Boolean.FALSE);
        BigDecimal volume = toDecimal(fields[A_VOLUME]);
        if (volume != null && volume.signum() == 0) {
            quote.setStatus(QuoteStatus.SUSPENDED);
            quote.setUnavailableReason("当日无成交（停牌或未交易）");
            return quote;
        }
        if (quote.getChangePercent() == null || quote.getTradeDate() == null) {
            return MarketQuote.unavailable(symbol, QuoteStatus.NO_DATA, "新浪行情缺涨跌幅或日期");
        }
        quote.setStatus(QuoteStatus.OK);
        return quote;
    }

    /**
     * 港股字段：英文名、中文名、开盘、昨收、最高、最低、现价、涨跌、涨跌幅…日期、时间
     *
     * @param symbol 行情标的
     * @param fields 字段数组
     * @return 行情
     */
    private MarketQuote toHkQuote(MarketSymbol symbol, String[] fields) {
        if (fields.length <= HK_TIME) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN, "新浪港股字段数不足 " + symbol.symbol());
        }
        BigDecimal price = toDecimal(fields[HK_PRICE]);
        if (price == null || price.signum() <= 0) {
            return MarketQuote.unavailable(symbol, QuoteStatus.NO_DATA, "新浪港股无有效价格");
        }
        MarketQuote quote = new MarketQuote();
        quote.setSymbol(symbol);
        quote.setName(fields[HK_NAME]);
        quote.setPrice(price);
        quote.setPreviousClose(toDecimal(fields[HK_PREV_CLOSE]));
        quote.setChangePercent(toDecimal(fields[HK_CHANGE_PERCENT]));
        quote.setTradeDate(toDate(fields[HK_DATE]));
        quote.setQuoteTime(toDateTime(fields[HK_DATE], fields[HK_TIME]));
        quote.setSource(NAME);
        quote.setAdjusted(Boolean.TRUE);
        quote.setRealtime(Boolean.FALSE);
        if (quote.getChangePercent() == null || quote.getTradeDate() == null) {
            return MarketQuote.unavailable(symbol, QuoteStatus.NO_DATA, "新浪港股缺涨跌幅或日期");
        }
        quote.setStatus(QuoteStatus.OK);
        return quote;
    }

    /**
     * 解析不复权日线，上游返回 null 或空数组时抛出结构变化
     *
     * @param symbol 行情标的
     * @param text   响应原文
     * @return 日线序列
     */
    MarketHistory parseHistory(MarketSymbol symbol, String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN, "新浪日线返回空响应 " + symbol.symbol());
        }
        if (!text.trim().startsWith("[")) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN, "新浪日线返回非数组 " + symbol.symbol());
        }
        Matcher matcher = KLINE_PATTERN.matcher(text);
        List<MarketBar> bars = new ArrayList<>();
        while (matcher.find()) {
            BigDecimal close = toDecimal(matcher.group(5));
            if (close != null) {
                // 分组顺序为 日期、开、高、低、收、量，新浪日线自带最高最低价
                bars.add(new MarketBar(LocalDate.parse(matcher.group(1)), toDecimal(matcher.group(2)), close,
                        toDecimal(matcher.group(3)), toDecimal(matcher.group(4)), toDecimal(matcher.group(6))));
            }
        }
        MarketHistory history = new MarketHistory();
        history.setSymbol(symbol);
        history.setBars(bars);
        history.setAdjusted(Boolean.FALSE);
        history.setSource(NAME);
        history.setFetchDate(LocalDate.now());
        return history;
    }

    /**
     * 按最新价与昨收计算涨跌幅
     *
     * @param price     最新价
     * @param prevClose 昨收
     * @return 涨跌幅，百分点
     */
    private BigDecimal changeByClose(BigDecimal price, BigDecimal prevClose) {
        if (price == null || prevClose == null || prevClose.signum() == 0) {
            return null;
        }
        return price.subtract(prevClose).multiply(BigDecimal.valueOf(100))
                .divide(prevClose, 4, RoundingMode.HALF_UP);
    }

    /**
     * 文本转数值，空串返回 null
     *
     * @param text 文本
     * @return 数值
     */
    private BigDecimal toDecimal(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析日期，兼容 2026-10-27 与 2026/10/27
     *
     * @param text 日期文本
     * @return 日期，无法解析返回 null
     */
    private LocalDate toDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim().replace('/', '-'));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 拼接日期与时间
     *
     * @param date 日期文本
     * @param time 时间文本
     * @return 行情时间，无法解析返回 null
     */
    private LocalDateTime toDateTime(String date, String time) {
        LocalDate day = toDate(date);
        if (day == null || time == null || time.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(day + "T" + (time.length() == 5 ? time + ":00" : time));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

package com.lilac.adapter.quote;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 腾讯行情源，实时行情走 qt.gtimg.cn，日线走 fqkline（港股走 hkfqkline），全部为前复权
 */
@Slf4j
@Component
@Order(1)
public class TencentQuoteSource implements QuoteSource {

    private static final String NAME = "tencent";
    private static final Charset GBK = Charset.forName("GBK");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("v_([a-z]{2}\\d{5,6})=\"([^\"]*)\"");
    private static final Pattern A_SHARE_TIME = Pattern.compile("^\\d{14}$");
    private static final Pattern HK_TIME = Pattern.compile("^(\\d{4}/\\d{2}/\\d{2})[ T](\\d{2}:\\d{2}:\\d{2})?");
    private static final int FIELD_NAME = 1;
    private static final int FIELD_PRICE = 3;
    private static final int FIELD_PREV_CLOSE = 4;
    private static final int FIELD_VOLUME = 6;
    private static final int FIELD_TIME = 30;
    private static final int FIELD_CHANGE_PERCENT = 32;

    private final TextFetcher textFetcher;
    private final String quoteUrl;
    private final String klineUrl;
    private final String hkKlineUrl;

    /**
     * 注入取数封装与腾讯行情地址
     *
     * @param textFetcher 文本取数接口
     * @param quoteUrl    实时行情地址
     * @param klineUrl    A 股与指数日线地址
     * @param hkKlineUrl  港股日线地址
     */
    public TencentQuoteSource(TextFetcher textFetcher,
                              @Value("${fund.adapter.tencent.quote-url}") String quoteUrl,
                              @Value("${fund.adapter.tencent.kline-url}") String klineUrl,
                              @Value("${fund.adapter.tencent.hk-kline-url}") String hkKlineUrl) {
        this.textFetcher = textFetcher;
        this.quoteUrl = quoteUrl;
        this.klineUrl = klineUrl;
        this.hkKlineUrl = hkKlineUrl;
    }

    /**
     * 数据源名称
     *
     * @return tencent
     */
    @Override
    public String name() {
        return NAME;
    }

    /**
     * 腾讯支持 A 股、指数、港股与北交所
     *
     * @param symbol 行情标的
     * @return 始终支持
     */
    @Override
    public boolean supports(MarketSymbol symbol) {
        return symbol != null;
    }

    /**
     * 一次请求取回多个标的的当日行情，单只异常不影响同批
     *
     * @param symbols 行情标的列表
     * @return 标的完整代码到行情的有序映射
     */
    @Override
    public Map<String, MarketQuote> fetchQuotes(List<MarketSymbol> symbols) {
        String text = textFetcher.getText(quoteUrl + joinSymbols(symbols), GBK);
        // 腾讯被限流时返回 501 验证页，这里必须显式识别，不能当空数据
        if (looksLikeHtml(text)) {
            throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "腾讯行情返回验证页，疑似被限流");
        }
        Map<String, MarketQuote> result = new LinkedHashMap<>();
        for (MarketSymbol symbol : symbols) {
            try {
                MarketQuote quote = parseQuote(symbol, text);
                if (quote != null) {
                    result.put(symbol.symbol(), quote);
                }
            } catch (RuntimeException e) {
                // 单只解析失败只跳过该标的，同批其他标的照常返回
                log.warn("腾讯行情解析跳过 {}/{}：{}", symbol.symbol(), symbol.getCode(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * 取单只标的的前复权日线，港股走专用接口
     *
     * @param symbol 行情标的
     * @param limit  返回根数
     * @return 日线序列
     */
    @Override
    public MarketHistory fetchHistory(MarketSymbol symbol, int limit) {
        String template = symbol.hongKong() ? hkKlineUrl : klineUrl;
        String url = template.replace("{symbol}", symbol.symbol()).replace("{limit}", String.valueOf(limit));
        String text = textFetcher.getText(url, StandardCharsets.UTF_8);
        if (looksLikeHtml(text)) {
            throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "腾讯日线返回验证页，疑似被限流");
        }
        return parseHistory(symbol, text);
    }

    /**
     * 解析一条实时行情，未命中或字段缺失返回 null
     *
     * @param symbol 行情标的
     * @param text   响应原文
     * @return 行情，无该标的时返回 null
     */
    MarketQuote parseQuote(MarketSymbol symbol, String text) {
        Matcher matcher = QUOTE_PATTERN.matcher(text);
        while (matcher.find()) {
            if (!matcher.group(1).equals(symbol.symbol())) {
                continue;
            }
            String[] fields = matcher.group(2).split("~", -1);
            return toQuote(symbol, fields);
        }
        return null;
    }

    /**
     * 字段数组转行情，价格或时间为空按无行情处理
     *
     * @param symbol 行情标的
     * @param fields 字段数组
     * @return 行情
     */
    private MarketQuote toQuote(MarketSymbol symbol, String[] fields) {
        if (fields.length <= FIELD_TIME) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN, "腾讯行情字段数不足 " + symbol.symbol());
        }
        MarketQuote quote = new MarketQuote();
        quote.setSymbol(symbol);
        quote.setName(fields[FIELD_NAME]);
        quote.setPrice(toDecimal(fields[FIELD_PRICE]));
        quote.setPreviousClose(toDecimal(fields[FIELD_PREV_CLOSE]));
        quote.setSource(NAME);
        quote.setAdjusted(Boolean.TRUE);
        quote.setRealtime(Boolean.FALSE);
        quote.setQuoteTime(parseTime(symbol, fields[FIELD_TIME]));
        quote.setTradeDate(quote.getQuoteTime() == null ? null : quote.getQuoteTime().toLocalDate());
        BigDecimal changePercent = toDecimal(fields[FIELD_CHANGE_PERCENT]);
        if (quote.getPrice() == null || quote.getPrice().signum() <= 0 || quote.getTradeDate() == null) {
            // 价格或时间为 0 说明这只标的在数据源没有有效行情
            return MarketQuote.unavailable(symbol, QuoteStatus.NO_DATA, "腾讯行情无有效价格");
        }
        quote.setChangePercent(changePercent != null ? changePercent : changeByClose(quote.getPrice(), quote.getPreviousClose()));
        if (quote.getChangePercent() == null) {
            return MarketQuote.unavailable(symbol, QuoteStatus.NO_DATA, "腾讯行情缺涨跌幅与昨收");
        }
        if (toDecimal(fields[FIELD_VOLUME]) == null || toDecimal(fields[FIELD_VOLUME]).signum() == 0) {
            // 当日成交量为 0 说明停牌或无成交，不能按 0% 计入预估
            return suspended(symbol, quote, "当日无成交（停牌或未交易）");
        }
        quote.setStatus(QuoteStatus.OK);
        return quote;
    }

    /**
     * 把已解析的行情改标为停牌
     *
     * @param symbol 行情标的
     * @param quote  原行情
     * @param reason 原因
     * @return 停牌行情
     */
    private MarketQuote suspended(MarketSymbol symbol, MarketQuote quote, String reason) {
        MarketQuote suspended = MarketQuote.unavailable(symbol, QuoteStatus.SUSPENDED, reason);
        suspended.setName(quote.getName());
        suspended.setPrice(quote.getPrice());
        suspended.setPreviousClose(quote.getPreviousClose());
        suspended.setChangePercent(quote.getChangePercent());
        suspended.setQuoteTime(quote.getQuoteTime());
        suspended.setTradeDate(quote.getTradeDate());
        suspended.setSource(NAME);
        return suspended;
    }

    /**
     * 解析日线响应，优先 qfqday，缺失时退回 day
     *
     * @param symbol 行情标的
     * @param text   响应原文
     * @return 日线序列
     */
    MarketHistory parseHistory(MarketSymbol symbol, String text) {
        JSONObject root = JSONUtil.parseObj(text);
        JSONObject data = root.getJSONObject("data");
        if (data == null) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN, "腾讯日线缺少 data 字段 " + symbol.symbol());
        }
        JSONObject node = data.getJSONObject(symbol.symbol());
        if (node == null) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN, "腾讯日线缺少标的数据 " + symbol.symbol());
        }
        JSONArray rows = node.getJSONArray("qfqday");
        if (rows == null) {
            rows = node.getJSONArray("day");
        }
        List<MarketBar> bars = new ArrayList<>();
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                JSONArray row = rows.getJSONArray(i);
                if (row == null || row.size() < 3) {
                    continue;
                }
                bars.add(new MarketBar(LocalDate.parse(row.getStr(0)), row.getBigDecimal(2)));
            }
        }
        MarketHistory history = new MarketHistory();
        history.setSymbol(symbol);
        history.setBars(bars);
        history.setAdjusted(Boolean.TRUE);
        history.setSource(NAME);
        history.setFetchDate(LocalDate.now());
        return history;
    }

    /**
     * 解析腾讯时间字段，A 股为 14 位数字、港股为斜杠分隔
     *
     * @param symbol 行情标的
     * @param time   时间原文
     * @return 行情时间，无法解析返回 null
     */
    private LocalDateTime parseTime(MarketSymbol symbol, String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        if (A_SHARE_TIME.matcher(time).matches()) {
            return LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        }
        Matcher matcher = HK_TIME.matcher(time);
        if (matcher.find()) {
            String date = matcher.group(1).replace('/', '-');
            String clock = matcher.group(2) == null ? "00:00:00" : matcher.group(2);
            return LocalDateTime.parse(date + "T" + clock);
        }
        log.warn("腾讯行情时间格式无法识别 {}：{}", symbol.symbol(), time);
        return null;
    }

    /**
     * 按最新价与昨收计算涨跌幅，除权日以数据源给的昨收为准
     *
     * @param price       最新价
     * @param prevClose   昨收
     * @return 涨跌幅，百分点
     */
    private BigDecimal changeByClose(BigDecimal price, BigDecimal prevClose) {
        if (price == null || prevClose == null || prevClose.signum() == 0) {
            return null;
        }
        return price.subtract(prevClose).multiply(BigDecimal.valueOf(100))
                .divide(prevClose, 4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 拼接查询串
     *
     * @param symbols 行情标的列表
     * @return 逗号分隔的完整代码
     */
    private String joinSymbols(List<MarketSymbol> symbols) {
        return String.join(",", symbols.stream().map(MarketSymbol::symbol).toList());
    }

    /**
     * 判断响应是否为 HTML 页面，验证页与错误页都不含行情数据
     *
     * @param text 响应原文
     * @return 是 HTML 返回 true
     */
    private boolean looksLikeHtml(String text) {
        return text != null && text.trim().startsWith("<");
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
}

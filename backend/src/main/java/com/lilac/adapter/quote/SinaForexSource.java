package com.lilac.adapter.quote;

import com.lilac.adapter.http.TextFetcher;
import com.lilac.adapter.model.ForexRate;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新浪外汇源，取港币兑人民币日线；东财 133.HKDCNH 被限流、腾讯无历史，这是唯一可用源
 */
@Slf4j
@Component
public class SinaForexSource {

    private static final String PAIR = "fx_shkdcny";
    private static final String PAIR_NAME = "HKDCNY";
    private static final String SOURCE = "sina";
    private static final Charset GBK = Charset.forName("GBK");
    // 日线字段顺序为「日期,开盘,最高,最低,收盘」——已用 3979 根实测复核，high/low 与 open/close 的极值关系无一条违例
    private static final int FIELD_DATE = 0;
    private static final int FIELD_CLOSE = 4;
    private static final Pattern BODY_PATTERN = Pattern.compile("\\(\"(.*)\"\\)", Pattern.DOTALL);
    // 实时行情字段：0=时间、1=现价、5=昨收、17=日期——已用真实返回逐段核对
    private static final int RT_PRICE = 1;
    private static final int RT_PREV_CLOSE = 5;
    private static final int RT_DATE = 17;

    private final TextFetcher textFetcher;
    private final String forexDailyUrl;
    private final String forexQuoteUrl;
    private final String referer;

    /**
     * 注入取数封装与外汇地址
     *
     * @param textFetcher   文本取数接口
     * @param forexDailyUrl 外汇日线地址模板，含 {pair}
     * @param forexQuoteUrl 外汇实时地址模板，含 {pair}
     * @param referer       新浪接口不带 Referer 返回 Forbidden
     */
    public SinaForexSource(TextFetcher textFetcher,
                           @Value("${fund.adapter.sina.forex-daily-url}") String forexDailyUrl,
                           @Value("${fund.adapter.sina.forex-quote-url}") String forexQuoteUrl,
                           @Value("${fund.adapter.sina.referer}") String referer) {
        this.textFetcher = textFetcher;
        this.forexDailyUrl = forexDailyUrl;
        this.forexQuoteUrl = forexQuoteUrl;
        this.referer = referer;
    }

    /**
     * 取港币兑人民币汇率与当日变动，日线先取最近两根，实时行情只用来补当日
     *
     * @return 汇率，取不到时 rate 为 null 且 changeRate 为 null
     */
    public ForexRate fetchHkdCny() {
        ForexRate rate = new ForexRate();
        rate.setPair(PAIR_NAME);
        rate.setSource(SOURCE);
        List<BigDecimal> closes = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>();
        try {
            parseDaily(textFetcher.getText(forexDailyUrl.replace("{pair}", PAIR), StandardCharsets.UTF_8),
                    dates, closes);
        } catch (BusinessException e) {
            log.warn("汇率日线取数失败：{}", e.getMessage());
        }
        if (closes.isEmpty()) {
            fetchLiveChange(rate);
            return rate;
        }
        rate.setRate(closes.get(closes.size() - 1));
        rate.setTradeDate(dates.get(dates.size() - 1));
        if (closes.size() >= 2) {
            rate.setPreviousRate(closes.get(closes.size() - 2));
            rate.setChangeRate(change(closes.get(closes.size() - 2), closes.get(closes.size() - 1)));
        }
        // 日线可能滞后一天，实时行情有当日价时以它为准
        fetchLiveChange(rate);
        log.info("汇率取数完成 {} 现价={} 数据日期={} 变动={}", PAIR_NAME, rate.getRate(), rate.getTradeDate(), rate.getChangeRate());
        return rate;
    }

    /**
     * 解析日线 JSONP 正文，字段为日期与四价，收盘价取第 5 列
     *
     * @param text   响应原文
     * @param dates  输出日期
     * @param closes 输出收盘价
     */
    void parseDaily(String text, List<LocalDate> dates, List<BigDecimal> closes) {
        Matcher matcher = BODY_PATTERN.matcher(text);
        if (!matcher.find()) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN, "汇率日线返回结构变化，找不到正文");
        }
        String body = matcher.group(1);
        for (String row : body.split("\\|")) {
            String[] fields = row.split(",");
            if (fields.length < 5) {
                continue;
            }
            try {
                dates.add(LocalDate.parse(fields[FIELD_DATE]));
                closes.add(new BigDecimal(fields[FIELD_CLOSE]));
            } catch (RuntimeException e) {
                log.warn("汇率日线跳过一行：{}", row);
            }
        }
    }

    /**
     * 取实时汇率，当日价以它为准，日线只用于补上一交易日
     *
     * @param rate 汇率结果，直接写入
     */
    private void fetchLiveChange(ForexRate rate) {
        try {
            String text = textFetcher.getText(forexQuoteUrl.replace("{pair}", PAIR), GBK, Map.of("Referer", referer));
            Matcher matcher = Pattern.compile("hq_str_" + PAIR + "=\"([^\"]*)\"").matcher(text);
            if (!matcher.find() || matcher.group(1).isBlank()) {
                return;
            }
            String[] fields = matcher.group(1).split(",", -1);
            if (fields.length <= RT_DATE) {
                return;
            }
            BigDecimal price = toDecimal(fields[RT_PRICE]);
            if (price == null || price.signum() == 0) {
                return;
            }
            LocalDate liveDate = parseDate(fields, RT_DATE);
            if (liveDate == null) {
                return;
            }
            LocalDate dailyDate = rate.getTradeDate();
            BigDecimal dailyClose = rate.getRate();
            // 日线落后于实时行情时，用日线最新价作为基准重算当日变动；同一天则沿用日线算好的变动
            rate.setRate(price);
            rate.setTradeDate(liveDate);
            if (dailyDate != null && liveDate.isAfter(dailyDate) && dailyClose != null
                    && dailyClose.signum() != 0) {
                rate.setPreviousRate(dailyClose);
                rate.setChangeRate(change(dailyClose, price));
            } else if (rate.getChangeRate() == null) {
                BigDecimal prevClose = toDecimal(fields[RT_PREV_CLOSE]);
                rate.setPreviousRate(prevClose);
                rate.setChangeRate(change(prevClose, price));
            }
        } catch (RuntimeException e) {
            log.warn("汇率实时行情取数失败：{}", e.getMessage());
        }
    }

    /**
     * 解析实时行情里的日期字段，可能带引号
     *
     * @param fields 字段数组
     * @param index  日期下标
     * @return 日期，解析失败返回 null
     */
    private LocalDate parseDate(String[] fields, int index) {
        if (fields.length <= index) {
            return null;
        }
        String text = fields[index].replace("\"", "").trim();
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 计算汇率变动
     *
     * @param previous 上一日汇率
     * @param current  当日汇率
     * @return 小数形式变动
     */
    private BigDecimal change(BigDecimal previous, BigDecimal current) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).divide(previous, 8, RoundingMode.HALF_UP);
    }

    /**
     * 文本转数值
     *
     * @param text 文本
     * @return 数值，空或非法返回 null
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

package com.lilac.adapter.fund;

import com.lilac.adapter.http.TextFetcher;
import com.lilac.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基金档案页适配器，只取「跟踪标的」一栏用于红利指数映射
 */
@Slf4j
@Component
public class FundDetailAdapter {

    private static final Pattern TRACK_INDEX_PATTERN =
            Pattern.compile("<th>\\s*跟踪标的\\s*</th>\\s*<td[^>]*>(.*?)</td>", Pattern.DOTALL);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final String NO_TRACK_INDEX = "该基金无跟踪标的";

    private final TextFetcher textFetcher;
    private final String detailUrlTemplate;

    /**
     * 注入取数封装与档案页地址模板
     *
     * @param textFetcher       文本取数接口
     * @param detailUrlTemplate 档案页地址模板，含 {code} 占位
     */
    public FundDetailAdapter(TextFetcher textFetcher,
                             @Value("${fund.adapter.eastmoney.fund-detail-url}") String detailUrlTemplate) {
        this.textFetcher = textFetcher;
        this.detailUrlTemplate = detailUrlTemplate;
    }

    /**
     * 读取跟踪标的名称，取不到时返回空而不抛异常
     *
     * @param code 6 位基金代码
     * @return 跟踪标的名称
     */
    public Optional<String> fetchTrackIndexName(String code) {
        String url = detailUrlTemplate.replace("{code}", code);
        try {
            String html = textFetcher.getText(url);
            Matcher matcher = TRACK_INDEX_PATTERN.matcher(html);
            if (!matcher.find()) {
                return Optional.empty();
            }
            String name = TAG_PATTERN.matcher(matcher.group(1)).replaceAll("").trim();
            return name.isBlank() || NO_TRACK_INDEX.equals(name) ? Optional.empty() : Optional.of(name);
        } catch (BusinessException e) {
            log.warn("跟踪标的获取失败 code={}：{}", code, e.getMessage());
            return Optional.empty();
        }
    }
}

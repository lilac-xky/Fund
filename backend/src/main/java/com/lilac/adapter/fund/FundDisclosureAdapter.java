package com.lilac.adapter.fund;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lilac.adapter.http.TextFetcher;
import com.lilac.adapter.model.FundReportDisclosure;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基金公告适配器，取定期报告披露日，决定持仓快照何时可用
 */
@Slf4j
@Component
public class FundDisclosureAdapter {

    private static final Pattern QUARTER_REPORT = Pattern.compile("第([1-4])季度报告");
    private static final String MID_YEAR_REPORT = "中期报告";
    private static final String ANNUAL_REPORT = "年度报告";
    private static final String ABSTRACT_SUFFIX = "摘要";
    private static final String FIELD_TITLE = "TITLE";
    private static final String FIELD_DATE = "PUBLISHDATEDesc";
    private static final int Q2 = 2;
    private static final int Q4 = 4;

    private final TextFetcher textFetcher;
    private final String announcementUrlTemplate;
    private final String referer;

    /**
     * 注入取数封装、公告地址模板与必带的 Referer
     *
     * @param textFetcher             文本取数接口
     * @param announcementUrlTemplate 地址模板，含 {code}
     * @param referer                 公告接口不带 Referer 会返回空数据
     */
    public FundDisclosureAdapter(TextFetcher textFetcher,
                                 @Value("${fund.adapter.eastmoney.fund-announcement-url}") String announcementUrlTemplate,
                                 @Value("${fund.adapter.eastmoney.referer}") String referer) {
        this.textFetcher = textFetcher;
        this.announcementUrlTemplate = announcementUrlTemplate;
        this.referer = referer;
    }

    /**
     * 取各报告期的定期报告披露记录，按披露日升序
     *
     * @param code 6 位基金代码
     * @return 披露记录，同一报告期只保留最早的一条
     */
    public List<FundReportDisclosure> fetchRegularReports(String code) {
        String url = announcementUrlTemplate.replace("{code}", code);
        String text = textFetcher.getText(url, StandardCharsets.UTF_8, Map.of("Referer", referer));
        return parse(code, text);
    }

    /**
     * 解析公告列表，只保留定期报告，每个报告期只留一条
     *
     * @param code 6 位基金代码
     * @param text 接口原文
     * @return 披露记录，按披露日升序
     */
    List<FundReportDisclosure> parse(String code, String text) {
        JSONObject root;
        try {
            // Hutool 对非 JSON 文本不报错而是返回空对象，先按首字符拦一道
            if (!text.trim().startsWith("{")) {
                throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                        "公告接口返回非 JSON code=" + code + "，结构已变化");
            }
            root = JSONUtil.parseObj(text);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                    "公告接口返回非 JSON code=" + code + "，结构已变化");
        }
        // 无定期报告时上游给 Data:""，属正常状态而不是结构变化
        Object rawData = root.get("Data");
        if (rawData == null) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                    "公告接口缺少 Data 字段 code=" + code + "，结构已变化");
        }
        if (rawData instanceof CharSequence textData) {
            return List.of();
        }
        JSONArray data = root.getJSONArray("Data");
        Map<String, FundReportDisclosure> byPeriod = new LinkedHashMap<>();
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            String title = item.getStr(FIELD_TITLE);
            String published = item.getStr(FIELD_DATE);
            if (title == null || published == null || title.contains(ABSTRACT_SUFFIX)) {
                continue;
            }
            FundReportDisclosure disclosure = toDisclosure(title, LocalDate.parse(published));
            if (disclosure == null) {
                continue;
            }
            // 同一报告期先发季报、后发中报，取最早那条作为快照首次可用时间
            byPeriod.merge(disclosure.getPeriod(), disclosure, (left, right) ->
                    left.getPublishDate().isBefore(right.getPublishDate()) ? left : right);
        }
        List<FundReportDisclosure> result = new ArrayList<>(byPeriod.values());
        result.sort(Comparator.comparing(FundReportDisclosure::getPublishDate));
        log.info("披露日解析完成 code={} 定期报告 {} 份", code, result.size());
        return result;
    }

    /**
     * 从标题推断报告期，第四季度与年报在次年第一季度披露，年份要减一
     *
     * @param title       公告标题
     * @param publishDate 披露日
     * @return 披露记录，非定期报告返回 null
     */
    private FundReportDisclosure toDisclosure(String title, LocalDate publishDate) {
        if (title.contains(MID_YEAR_REPORT)) {
            return new FundReportDisclosure(publishDate.getYear() + "Q" + Q2, publishDate, Boolean.TRUE, title);
        }
        if (title.contains(ANNUAL_REPORT)) {
            return new FundReportDisclosure((publishDate.getYear() - 1) + "Q" + Q4, publishDate, Boolean.TRUE, title);
        }
        Matcher matcher = QUARTER_REPORT.matcher(title);
        if (!matcher.find()) {
            return null;
        }
        int quarter = Integer.parseInt(matcher.group(1));
        int year = quarter == Q4 ? publishDate.getYear() - 1 : publishDate.getYear();
        return new FundReportDisclosure(year + "Q" + quarter, publishDate, Boolean.FALSE, title);
    }
}

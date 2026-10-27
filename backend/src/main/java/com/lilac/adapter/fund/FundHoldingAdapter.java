package com.lilac.adapter.fund;

import com.lilac.adapter.http.TextFetcher;
import com.lilac.adapter.model.FundHoldingItem;
import com.lilac.adapter.model.FundHoldingSnapshot;
import com.lilac.adapter.model.HoldingKind;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 天天基金 fundf10 持仓适配器，解析季报与中报披露的股票、债券明细
 */
@Slf4j
@Component
public class FundHoldingAdapter {

    private static final String BOX_MARKER = "<div class='boxitem w790'>";
    private static final Pattern BOX_SPLIT = Pattern.compile(BOX_MARKER, Pattern.LITERAL);
    private static final String BOX_END = "</div></div>";
    private static final Pattern HEADER_PATTERN = Pattern.compile("<h4[^>]*>(.*?)</h4>", Pattern.DOTALL);
    private static final Pattern PERIOD_PATTERN = Pattern.compile("(\\d{4})年(\\d)季度");
    private static final Pattern CUTOFF_PATTERN = Pattern.compile("截止至：<font[^>]*>(\\d{4}-\\d{2}-\\d{2})</font>");
    private static final Pattern ROW_PATTERN = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern CELL_PATTERN = Pattern.compile("<t[dh][^>]*>(.*?)</t[dh]>", Pattern.DOTALL);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern PERCENT_PATTERN = Pattern.compile("^(-?\\d+(\\.\\d+)?)%$");
    private static final String EMPTY_CONTENT = "content:\"\"";
    private static final String WEIGHT_COLUMN = "占净值比例";
    private static final String CODE_COLUMN = "代码";
    private static final String NAME_COLUMN = "名称";

    private final TextFetcher textFetcher;
    private final String holdingUrlTemplate;
    private final String referer;

    /**
     * 注入取数封装、持仓地址模板与必带的 Referer
     *
     * @param textFetcher        文本取数接口
     * @param holdingUrlTemplate 地址模板，含 {kind}、{code}、{year}、{month}
     * @param referer            fundf10 接口不带 Referer 会返回 404
     */
    public FundHoldingAdapter(TextFetcher textFetcher,
                              @Value("${fund.adapter.eastmoney.fund-holding-url}") String holdingUrlTemplate,
                              @Value("${fund.adapter.eastmoney.referer}") String referer) {
        this.textFetcher = textFetcher;
        this.holdingUrlTemplate = holdingUrlTemplate;
        this.referer = referer;
    }

    /**
     * 取最近一个季末对应的全部披露快照，该基金确无此类持仓时返回空列表
     *
     * @param code  6 位基金代码
     * @param kind  股票或债券
     * @param today 取数当日，决定请求哪个报告期
     * @return 快照列表，按截止日升序
     */
    public List<FundHoldingSnapshot> fetchAll(String code, HoldingKind kind, LocalDate today) {
        String url = holdingUrlTemplate
                .replace("{kind}", kind.getType())
                .replace("{code}", code)
                .replace("{year}", String.valueOf(today.getYear()))
                .replace("{month}", String.valueOf(latestQuarterEnd(today).getMonthValue()));
        String text = textFetcher.getText(url, StandardCharsets.UTF_8, Map.of("Referer", referer));
        List<FundHoldingSnapshot> snapshots = parseAll(code, kind, text);
        snapshots.forEach(snapshot -> log.info("{}解析完成 code={} 期次={} 截止={} 行数={} 合计={}%",
                kind.getTitle(), code, snapshot.getPeriod(), snapshot.getCutoffDate(),
                snapshot.getItems().size(), snapshot.totalWeight()));
        return snapshots;
    }

    /**
     * 按指定报告期取数并解析，返回该次响应里的全部报告期
     *
     * @param code       6 位基金代码
     * @param kind       股票或债券
     * @param quarterEnd 报告期截止日，决定请求的年份与月份
     * @return 快照列表，按截止日升序
     */
    List<FundHoldingSnapshot> fetchAt(String code, HoldingKind kind, LocalDate quarterEnd) {
        // month 必须传报告期所在月份，只有 6 月与 12 月才会展开中报、年报的全量披露
        String url = holdingUrlTemplate
                .replace("{kind}", kind.getType())
                .replace("{code}", code)
                .replace("{year}", String.valueOf(quarterEnd.getYear()))
                .replace("{month}", String.valueOf(quarterEnd.getMonthValue()));
        String text = textFetcher.getText(url, StandardCharsets.UTF_8, Map.of("Referer", referer));
        return parseAll(code, kind, text);
    }

    /**
     * 解析返回片段中的全部报告期，按截止日升序
     *
     * @param code 6 位基金代码
     * @param kind 股票或债券
     * @param text 原始 HTML 片段
     * @return 快照列表，上游确无此类持仓时返回空列表
     */
    List<FundHoldingSnapshot> parseAll(String code, HoldingKind kind, String text) {
        if (text.trim().startsWith("<")) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                    "fundf10 返回页面而非持仓片段 code=" + code + "，接口已改版");
        }
        // 该基金没有此类持仓时上游给空 content，这是正常状态而不是结构变化
        if (text.contains(EMPTY_CONTENT)) {
            return List.of();
        }
        String[] sections = BOX_SPLIT.split(text);
        List<FundHoldingSnapshot> snapshots = new ArrayList<>(4);
        // 第一段是表格外的包装，从第二段起每段对应一个报告期，按自身的收尾标签截断
        for (int i = 1; i < sections.length; i++) {
            String section = sections[i];
            int end = section.indexOf(BOX_END);
            String box = end < 0 ? section : section.substring(0, end);
            FundHoldingSnapshot snapshot = parseBox(code, kind, box);
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        if (snapshots.isEmpty()) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                    "未解析到任何报告期 code=" + code + "，fundf10 返回结构已变化");
        }
        snapshots.sort(Comparator.comparing(FundHoldingSnapshot::getCutoffDate));
        return snapshots;
    }

    /**
     * 解析单个报告期区块，缺少报告期或截止日时说明结构已变
     *
     * @param code 6 位基金代码
     * @param kind 股票或债券
     * @param box  区块 HTML
     * @return 快照，该期无明细时返回 null
     */
    private FundHoldingSnapshot parseBox(String code, HoldingKind kind, String box) {
        Matcher headerMatcher = HEADER_PATTERN.matcher(box);
        String header = headerMatcher.find() ? stripTags(headerMatcher.group(1)) : "";
        Matcher periodMatcher = PERIOD_PATTERN.matcher(header);
        Matcher cutoffMatcher = CUTOFF_PATTERN.matcher(box);
        if (!periodMatcher.find() || !cutoffMatcher.find()) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                    "报告期或截止日缺失 code=" + code + "，fundf10 返回结构已变化");
        }
        String period = periodMatcher.group(1) + "Q" + periodMatcher.group(2);
        LocalDate cutoffDate = LocalDate.parse(cutoffMatcher.group(1));
        List<FundHoldingItem> items = parseRows(code, box);
        if (items.isEmpty()) {
            return null;
        }
        return new FundHoldingSnapshot(period, cutoffDate, kind, items);
    }

    /**
     * 按表头定位代码、名称、占净值比例三列后逐行解析
     *
     * @param code 6 位基金代码
     * @param box  区块 HTML
     * @return 持仓明细，按权重降序
     */
    private List<FundHoldingItem> parseRows(String code, String box) {
        Matcher rowMatcher = ROW_PATTERN.matcher(box);
        List<List<String>> rows = new ArrayList<>();
        while (rowMatcher.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cellMatcher = CELL_PATTERN.matcher(rowMatcher.group(1));
            while (cellMatcher.find()) {
                cells.add(stripTags(cellMatcher.group(1)));
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        if (rows.isEmpty()) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                    "持仓表为空 code=" + code + "，fundf10 返回结构已变化");
        }
        // 季报的表头比中报多出「最新价/涨跌幅」两列，必须按列名定位而不是写死下标
        List<String> header = rows.get(0);
        int codeIndex = indexOfColumn(header, CODE_COLUMN);
        int nameIndex = indexOfColumn(header, NAME_COLUMN);
        int weightIndex = indexOfColumn(header, WEIGHT_COLUMN);
        if (codeIndex < 0 || nameIndex < 0 || weightIndex < 0) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                    "持仓表头缺少代码或占净值比例列 code=" + code + "，fundf10 返回结构已变化");
        }
        List<FundHoldingItem> items = new ArrayList<>(rows.size());
        for (List<String> cells : rows.subList(1, rows.size())) {
            if (cells.size() <= weightIndex) {
                continue;
            }
            BigDecimal weight = parsePercent(cells.get(weightIndex));
            // 权重列不是百分数的行不是持仓行，例如表尾的说明行
            if (weight == null) {
                continue;
            }
            items.add(new FundHoldingItem(cells.get(codeIndex), cells.get(nameIndex), weight));
        }
        items.sort(Comparator.comparing(FundHoldingItem::getWeight).reversed());
        return items;
    }

    /**
     * 按列名找下标，兼容「股票代码」「债券代码」与「股票名称」两种写法
     *
     * @param header      表头
     * @param columnToken 列名关键词
     * @return 下标，未命中返回 -1
     */
    private int indexOfColumn(List<String> header, String columnToken) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).contains(columnToken)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 解析百分数文本
     *
     * @param text 形如 3.03%
     * @return 百分点数值，格式不符返回 null
     */
    private BigDecimal parsePercent(String text) {
        Matcher matcher = PERCENT_PATTERN.matcher(text.trim());
        if (!matcher.find()) {
            return null;
        }
        return new BigDecimal(matcher.group(1));
    }

    /**
     * 去标签并去掉千分位逗号
     *
     * @param html 单元格内容
     * @return 纯文本
     */
    private String stripTags(String html) {
        return TAG_PATTERN.matcher(html).replaceAll("").replace("&nbsp;", " ").replace(",", "").trim();
    }

    /**
     * 取不晚于给定日期的最近一个季末
     *
     * @param today 取数当日
     * @return 季末日期
     */
    private LocalDate latestQuarterEnd(LocalDate today) {
        int year = today.getYear();
        if (!today.isBefore(LocalDate.of(year, 12, 31))) {
            return LocalDate.of(year, 12, 31);
        }
        if (!today.isBefore(LocalDate.of(year, 9, 30))) {
            return LocalDate.of(year, 9, 30);
        }
        if (!today.isBefore(LocalDate.of(year, 6, 30))) {
            return LocalDate.of(year, 6, 30);
        }
        if (!today.isBefore(LocalDate.of(year, 3, 31))) {
            return LocalDate.of(year, 3, 31);
        }
        return LocalDate.of(year - 1, 12, 31);
    }
}

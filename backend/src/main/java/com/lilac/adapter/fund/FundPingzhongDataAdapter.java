package com.lilac.adapter.fund;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lilac.adapter.http.TextFetcher;
import com.lilac.adapter.model.FundAssetAllocationPoint;
import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.FundPingzhongData;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 天天基金 pingzhongdata 适配器，解析历史净值与季末股票、债券、现金占净比
 */
@Slf4j
@Component
public class FundPingzhongDataAdapter {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String NET_WORTH_VAR = "Data_netWorthTrend";
    private static final String AC_NET_WORTH_VAR = "Data_ACWorthTrend";
    private static final String ASSET_ALLOCATION_VAR = "Data_assetAllocation";
    private static final String NAME_VAR = "fS_name";
    private static final String STOCK_SERIES = "股票占净比";
    private static final String BOND_SERIES = "债券占净比";
    private static final String CASH_SERIES = "现金占净比";

    private final TextFetcher textFetcher;
    private final String pingzhongUrlTemplate;

    /**
     * 注入取数封装与 pingzhongdata 地址模板
     *
     * @param textFetcher          文本取数接口
     * @param pingzhongUrlTemplate 地址模板，含 {code} 占位
     */
    public FundPingzhongDataAdapter(TextFetcher textFetcher,
                                    @Value("${fund.adapter.eastmoney.fund-pingzhong-url}") String pingzhongUrlTemplate) {
        this.textFetcher = textFetcher;
        this.pingzhongUrlTemplate = pingzhongUrlTemplate;
    }

    /**
     * 拉取并解析净值与仓位，结构变化时抛具名异常不返回空对象
     *
     * @param code 6 位基金代码
     * @return 解析结果
     */
    public FundPingzhongData fetch(String code) {
        return parse(code, textFetcher.getText(pingzhongUrlTemplate.replace("{code}", code)));
    }

    /**
     * 从 JS 文本中取净值序列与季末仓位
     *
     * @param code 6 位基金代码
     * @param text 原始 JS 文本
     * @return 解析结果
     */
    FundPingzhongData parse(String code, String text) {
        // 代码不存在时上游返回 404 页面，先按 HTML 拦掉，避免后续正则误取
        if (text.trim().startsWith("<")) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                    "pingzhongdata 返回页面而非 JS，代码 " + code + " 可能不存在或接口已改版");
        }
        FundPingzhongData data = new FundPingzhongData();
        data.setCode(code);
        data.setName(parseName(text));
        List<FundNetWorthPoint> points = parseNetWorth(code, requireVar(text, NET_WORTH_VAR, code));
        fillAccumulatedNetWorth(points, text);
        data.setNetWorthPoints(points);
        data.setAssetAllocations(parseAssetAllocation(text));
        log.info("净值解析完成 code={} 名称={} 净值 {} 条 最新 {} 仓位 {} 期",
                code, data.getName(), data.getNetWorthPoints().size(), data.latestNetWorthDate(),
                data.getAssetAllocations().size());
        return data;
    }

    /**
     * 把累计净值挂到对应日期上，缺这个变量时整列留空而不是让解析失败
     *
     * @param points 净值序列，原地补累计净值
     * @param text   原始 JS 文本
     */
    private void fillAccumulatedNetWorth(List<FundNetWorthPoint> points, String text) {
        String json = findVar(text, AC_NET_WORTH_VAR);
        if (json == null || json.isBlank()) {
            return;
        }
        Map<LocalDate, BigDecimal> byDate;
        try {
            byDate = parseAccumulated(json);
        } catch (Exception e) {
            // 累计净值只用于分红展示，解析不了就当没有，绝不影响单位净值与预估
            log.warn("累计净值解析失败，按无累计净值处理：{}", e.getMessage());
            return;
        }
        points.forEach(point -> point.setAccumulatedNetWorth(byDate.get(point.getNetWorthDate())));
    }

    /**
     * 解析累计净值数组，每项是 [毫秒时间戳, 累计净值]
     *
     * @param json 数组原文
     * @return 日期到累计净值的映射
     */
    private Map<LocalDate, BigDecimal> parseAccumulated(String json) {
        JSONArray array = JSONUtil.parseArray(json);
        Map<LocalDate, BigDecimal> byDate = new LinkedHashMap<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONArray row = array.getJSONArray(i);
            if (row == null || row.size() < 2) {
                continue;
            }
            Long timestamp = row.getLong(0);
            BigDecimal value = row.getBigDecimal(1);
            if (timestamp == null || value == null) {
                continue;
            }
            byDate.put(toDate(timestamp), value);
        }
        return byDate;
    }

    /**
     * 取基金简称，字段缺失时返回 null
     *
     * @param text 原始 JS 文本
     * @return 基金简称
     */
    private String parseName(String text) {
        String raw = findVar(text, NAME_VAR);
        return raw == null ? null : raw.replace("\"", "").trim();
    }

    /**
     * 解析净值数组，x 为毫秒时间戳、y 为单位净值、equityReturn 为当日回报
     *
     * @param code 6 位基金代码
     * @param json 净值数组原文
     * @return 净值序列，按日期升序
     */
    private List<FundNetWorthPoint> parseNetWorth(String code, String json) {
        JSONArray array;
        try {
            array = JSONUtil.parseArray(json);
        } catch (Exception e) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                    "新净值序列解析失败 code=" + code);
        }
        if (array.isEmpty()) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                    "净值序列为空 code=" + code + "，新基金或接口已改版，今日无法预估");
        }
        List<FundNetWorthPoint> points = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONObject row = array.getJSONObject(i);
            // 每行必须同时有日期与净值，缺一个就说明结构变了
            Long timestamp = row.getLong("x");
            BigDecimal netWorth = row.getBigDecimal("y");
            if (timestamp == null || netWorth == null) {
                throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                        "净值行缺字段 code=" + code + " 第 " + i + " 行");
            }
            points.add(new FundNetWorthPoint(toDate(timestamp), netWorth, row.getBigDecimal("equityReturn")));
        }
        points.sort(Comparator.comparing(FundNetWorthPoint::getNetWorthDate));
        return points;
    }

    /**
     * 解析季末仓位，股票、债券、现金三条序列按报告期对齐
     *
     * @param text 原始 JS 文本
     * @return 仓位快照，无系列时返回空列表
     */
    private List<FundAssetAllocationPoint> parseAssetAllocation(String text) {
        String json = findVar(text, ASSET_ALLOCATION_VAR);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JSONObject root;
        try {
            root = JSONUtil.parseObj(json);
        } catch (Exception e) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN, "季末仓位解析失败");
        }
        JSONArray categories = root.getJSONArray("categories");
        JSONArray series = root.getJSONArray("series");
        if (categories == null || series == null || categories.isEmpty()) {
            return List.of();
        }
        JSONArray stock = seriesByName(series, STOCK_SERIES);
        // 货币基金没有股票/债券/现金占净比，只有申赎份额，按无仓位处理
        if (stock == null) {
            return List.of();
        }
        JSONArray bond = seriesByName(series, BOND_SERIES);
        JSONArray cash = seriesByName(series, CASH_SERIES);
        List<FundAssetAllocationPoint> points = new ArrayList<>(categories.size());
        for (int i = 0; i < categories.size(); i++) {
            points.add(new FundAssetAllocationPoint(LocalDate.parse(categories.getStr(i)),
                    valueAt(stock, i), valueAt(bond, i), valueAt(cash, i)));
        }
        points.sort(Comparator.comparing(FundAssetAllocationPoint::getReportDate));
        return points;
    }

    /**
     * 按名称取出一条序列
     *
     * @param series 序列数组
     * @param name   序列名
     * @return 序列，未命中返回 null
     */
    private JSONArray seriesByName(JSONArray series, String name) {
        for (int i = 0; i < series.size(); i++) {
            JSONObject item = series.getJSONObject(i);
            if (name.equals(item.getStr("name"))) {
                return item.getJSONArray("data");
            }
        }
        return null;
    }

    /**
     * 按下标取序列值，越界或非数字返回 null
     *
     * @param data  序列数组
     * @param index 下标
     * @return 数值
     */
    private BigDecimal valueAt(JSONArray data, int index) {
        if (data == null || index >= data.size()) {
            return null;
        }
        return data.getBigDecimal(index);
    }

    /**
     * 取必须存在的 JS 变量原文
     *
     * @param text 原始 JS 文本
     * @param name 变量名
     * @param code 6 位基金代码
     * @return 变量原文
     */
    private String requireVar(String text, String name, String code) {
        String value = findVar(text, name);
        if (value == null || value.isBlank()) {
            // 货币基金与部分新发基金本就没有净值曲线，与结构改版要分开说清
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                    "pingzhongdata 无净值序列 code=" + code + "（缺少 " + name
                            + "），货币基金或新发基金暂无法预估");
        }
        return value;
    }

    /**
     * 取 JS 变量原文，取不到返回 null
     *
     * @param text 原始 JS 文本
     * @param name 变量名
     * @return 变量原文
     */
    private String findVar(String text, String name) {
        String head = "var " + name + " =";
        int start = text.indexOf(head);
        if (start < 0) {
            head = "var " + name + "=";
            start = text.indexOf(head);
            if (start < 0) {
                return null;
            }
        }
        int valueStart = start + head.length();
        int valueEnd = text.indexOf(';', valueStart);
        if (valueEnd < 0) {
            return null;
        }
        return text.substring(valueStart, valueEnd).trim();
    }

    /**
     * 毫秒时间戳转东八区日期
     *
     * @param timestamp 毫秒时间戳
     * @return 日期
     */
    private LocalDate toDate(Long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(CHINA_ZONE).toLocalDate();
    }
}

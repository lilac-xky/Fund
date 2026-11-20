package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.MarketBar;
import com.lilac.adapter.model.MarketHistory;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.domain.dto.estimate.EstimateBacktestRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.BacktestItemVO;
import com.lilac.domain.vo.BacktestParamVO;
import com.lilac.domain.vo.BacktestResultVO;
import com.lilac.domain.vo.BacktestSummaryVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.engine.BetaRegressor;
import com.lilac.engine.BondBaselineEstimator;
import com.lilac.engine.BondMultiProxyEstimator;
import com.lilac.engine.BenchmarkResolver;
import com.lilac.engine.EstimateThresholds;
import com.lilac.engine.GoldBetaEstimator;
import com.lilac.engine.model.BetaModel;
import com.lilac.engine.model.GoldBetaModel;
import com.lilac.enums.EstimateBranch;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.VarietyType;
import com.lilac.service.EstimateBacktestService;
import com.lilac.service.FundNetWorthService;
import com.lilac.service.VarietyService;
import com.lilac.setting.SettingOverrideResolver;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 因子回测业务实现，按交易日逐日重放代理分支，每一日只用该日之前的数据避免前视偏差
 */
@Slf4j
@Service
public class EstimateBacktestServiceImpl implements EstimateBacktestService {

    private static final int DEFAULT_RANGE_DAYS = 90;
    private static final int MAX_RANGE_DAYS = 500;
    private static final int RESULT_SCALE = 4;
    private static final int PERCENTILE_P90 = 90;
    private static final int PROXY_HISTORY_LIMIT = 400;

    @Resource
    private VarietyService varietyService;

    @Resource
    private FundNetWorthService fundNetWorthService;

    @Resource
    private BenchmarkResolver benchmarkResolver;

    @Resource
    private BetaRegressor betaRegressor;

    @Resource
    private GoldBetaEstimator goldBetaEstimator;

    @Resource
    private BondBaselineEstimator bondBaselineEstimator;

    @Resource
    private BondMultiProxyEstimator bondMultiProxyEstimator;

    @Resource
    private MarketQuoteAdapter marketQuoteAdapter;

    @Resource
    private EstimateThresholds thresholds;

    @Resource
    private SettingOverrideResolver settingOverrideResolver;

    /**
     * 按请求参数重放指定区间，只读不写，重复执行结果一致
     *
     * @param request 回测请求
     * @return 回测结果
     */
    @Override
    public BacktestResultVO run(EstimateBacktestRequest request) {
        EstimateBacktestRequest query = request == null ? new EstimateBacktestRequest() : request;
        LocalDate end = parseDate(query.getEndDate(), LocalDate.now());
        LocalDate start = parseDate(query.getStartDate(), end.minusDays(DEFAULT_RANGE_DAYS));
        ThrowUtils.throwIf(start.isAfter(end), HttpsCodeEnum.PARAMS_ERROR, "起始日不能晚于截止日");
        ThrowUtils.throwIf(ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS, HttpsCodeEnum.PARAMS_ERROR,
                "单次回测最多 " + MAX_RANGE_DAYS + " 天，请拆成多段");
        EstimateThresholds effective = effectiveThresholds(query);
        List<VarietyVO> varieties = resolveVarieties(query.getVarietyKeys());
        ThrowUtils.throwIf(varieties.isEmpty(), HttpsCodeEnum.NOT_FOUND_ERROR,
                "没有可回测的基金，请先录入持仓或显式指定品种主键");
        List<BacktestItemVO> items = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (VarietyVO variety : varieties) {
            items.addAll(replay(variety, start, end, effective, notes));
        }
        items.sort(Comparator.comparing(BacktestItemVO::getTradeDate)
                .thenComparing(BacktestItemVO::getVarietyCode,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        BacktestResultVO result = new BacktestResultVO();
        result.setStartDate(start);
        result.setEndDate(end);
        result.setVarietyCount(varieties.size());
        result.setTradeDateCount(items.stream().map(BacktestItemVO::getTradeDate).distinct().toList().size());
        result.setSampleCount(items.size());
        result.setSummaries(summarize(items));
        result.setParams(paramOf(effective));
        result.setItems(items);
        result.setNotes(notes);
        fillOverall(result, items, notes, effective);
        if (items.isEmpty()) {
            notes.add("区间内没有可重放的数据：请确认该基金净值已入库、代理标的日线可取");
        }
        log.info("因子回测完成 区间={}~{} 基金 {} 只 记录 {} 条", start, end, varieties.size(), items.size());
        return result;
    }

    /**
     * 重放单只基金在整个区间，每日只用该日之前的净值与日线
     *
     * @param variety   品种
     * @param start     起始日
     * @param end       截止日
     * @param effective 本次生效阈值
     * @param notes     缺口说明，直接追加
     * @return 逐日结果
     */
    private List<BacktestItemVO> replay(VarietyVO variety, LocalDate start, LocalDate end,
                                        EstimateThresholds effective, List<String> notes) {
        FundCategory category = variety.getCategory();
        if (category == null || category == FundCategory.UNKNOWN) {
            notes.add(variety.getVarietyCode() + " 类型待确认，未参与回测");
            return List.of();
        }
        if (category == FundCategory.MIXED) {
            // 混合型不给点估计，回测没有可对照的数字，方向与区间依赖留存，历史留存不在库里
            notes.add(variety.getVarietyCode() + " 为混合型，不承诺点估计，未参与回测");
            return List.of();
        }
        String proxySymbol = benchmarkResolver.resolve(variety, null);
        if (proxySymbol == null) {
            notes.add(variety.getVarietyCode() + " 未解析到代理标的，未参与回测");
            return List.of();
        }
        List<FundNetWorthPoint> netWorths = fundNetWorthService.listRange(variety.getVarietyCode(), null, end);
        if (netWorths.isEmpty()) {
            notes.add(variety.getVarietyCode() + " 库内没有净值序列，未参与回测");
            return List.of();
        }
        MarketHistory history = marketQuoteAdapter.history(proxySymbol, PROXY_HISTORY_LIMIT);
        if (history.getBars() == null || history.getBars().isEmpty()) {
            notes.add(variety.getVarietyCode() + " 代理标的 " + proxySymbol + " 日线取不到，未参与回测");
            return List.of();
        }
        EstimateBranch branch = branchOf(variety, category);
        // 债券多代理：把每个代理的日线一次取齐，逐日合成时按日期取
        Map<String, List<MarketBar>> bondBars = branch == EstimateBranch.BOND_PROXY
                ? fetchBondProxyBars(effective) : Map.of();
        Map<LocalDate, BigDecimal> proxyReturns = proxyReturns(history);
        List<BacktestItemVO> items = new ArrayList<>();
        for (FundNetWorthPoint point : netWorths) {
            LocalDate day = point.getNetWorthDate();
            if (day.isBefore(start) || day.isAfter(end) || point.getEquityReturn() == null) {
                continue;
            }
            BigDecimal proxyChange = proxyReturns.get(day);
            if (proxyChange == null) {
                continue;
            }
            // 只喂当日之前的样本，回归与统计都不许看到未来
            List<FundNetWorthPoint> pastWorths = netWorths.stream()
                    .filter(item -> item.getNetWorthDate().isBefore(day)).toList();
            BigDecimal predicted = branch == EstimateBranch.BOND_PROXY
                    ? predictBondMulti(pastWorths, bondBars, day, proxyChange, effective)
                    : predict(branch, variety, proxyChange, pastWorths, history, effective);
            if (predicted == null) {
                continue;
            }
            items.add(itemOf(variety, branch, day, predicted, point.getEquityReturn(), effective));
        }
        return items;
    }

    /**
     * 取债券多代理的日线，缺任何一个都会让回归退化，直接返回空表
     *
     * @param effective 生效阈值
     * @return 代理代码到日线的映射
     */
    private Map<String, List<MarketBar>> fetchBondProxyBars(EstimateThresholds effective) {
        Map<String, List<MarketBar>> bars = new LinkedHashMap<>();
        for (String code : effective.bondProxyCodeList()) {
            String symbol = BenchmarkResolver.symbolOf(code);
            if (symbol == null) {
                continue;
            }
            MarketHistory history = marketQuoteAdapter.history(symbol, PROXY_HISTORY_LIMIT);
            if (history.getBars() == null || history.getBars().isEmpty()) {
                continue;
            }
            bars.put(symbol, history.getBars());
        }
        return bars.size() >= 2 ? bars : Map.of();
    }

    /**
     * 债券多代理重放：用当日之前的样本拟合，再用当日各代理涨跌合成
     *
     * @param pastWorths  当日之前的净值
     * @param bondBars    各代理日线
     * @param day         当日
     * @param fallback    单代理涨跌，多代理不可用时兜底
     * @param effective   生效阈值
     * @return 预估涨跌，算不出返回 null
     */
    private BigDecimal predictBondMulti(List<FundNetWorthPoint> pastWorths, Map<String, List<MarketBar>> bondBars,
                                        LocalDate day, BigDecimal fallback, EstimateThresholds effective) {
        if (bondBars.isEmpty() || bondMultiProxyEstimator == null) {
            return fallback;
        }
        BondMultiProxyEstimator.Model model = bondMultiProxyEstimator.estimate(pastWorths, bondBars,
                effective.getBondWindow(), effective.getBondMinPairs());
        if (model == null) {
            return fallback;
        }
        Map<String, BigDecimal> changes = new LinkedHashMap<>();
        for (Map.Entry<String, List<MarketBar>> entry : bondBars.entrySet()) {
            BigDecimal change = changeAt(entry.getValue(), day);
            if (change != null) {
                changes.put(entry.getKey(), change);
            }
        }
        BigDecimal predicted = bondMultiProxyEstimator.predict(model, changes);
        return predicted == null ? fallback : predicted;
    }

    /**
     * 从日线里取某个交易日的涨跌幅
     *
     * @param bars 日线
     * @param day  交易日
     * @return 涨跌幅，百分点，取不到返回 null
     */
    private BigDecimal changeAt(List<MarketBar> bars, LocalDate day) {
        for (int i = 1; i < bars.size(); i++) {
            if (!day.equals(bars.get(i).getDate())) {
                continue;
            }
            BigDecimal previous = bars.get(i - 1).getClose();
            BigDecimal current = bars.get(i).getClose();
            if (previous == null || current == null || previous.signum() == 0) {
                return null;
            }
            return current.subtract(previous).divide(previous, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return null;
    }

    /**
     * 按分支算出当日的重放预估值
     *
     * @param branch      分支
     * @param variety     品种
     * @param proxyChange 代理标的当日涨跌
     * @param pastWorths  当日之前的净值
     * @param history     代理日线
     * @param effective   生效阈值
     * @return 预估涨跌，算不出返回 null
     */
    private BigDecimal predict(EstimateBranch branch, VarietyVO variety, BigDecimal proxyChange,
                               List<FundNetWorthPoint> pastWorths, MarketHistory history,
                               EstimateThresholds effective) {
        if (branch == EstimateBranch.BOND_PROXY) {
            BondBaselineEstimator.Model model = bondBaselineEstimator.estimate(pastWorths, history.getBars(),
                    effective.getBondWindow(), effective.getBondMinPairs());
            return model == null ? proxyChange : bondBaselineEstimator.predict(model, proxyChange);
        }
        if (branch == EstimateBranch.GOLD_PROXY) {
            GoldBetaModel model = goldBetaEstimator.estimate(pastWorths, history.getBars(),
                    effective.getGoldWindow(), effective.getGoldMinPairs());
            return model.predict(proxyChange);
        }
        if (branch == EstimateBranch.INDEX_ONLY && variety.getVarietyType() != VarietyType.ETF
                && effective.getIndexBetaWindow() > 0) {
            BetaModel model = betaRegressor.estimate(pastWorths, history.getBars(),
                    effective.getIndexBetaWindow(), effective.getIndexBetaMinPairs());
            return model == null ? proxyChange : model.predict(proxyChange);
        }
        return proxyChange;
    }

    /**
     * 判定分支，与预估引擎保持同一套规则；回测只重放三条代理分支，持仓加权另行说明
     *
     * @param variety  品种
     * @param category 基金类型
     * @return 分支
     */
    private EstimateBranch branchOf(VarietyVO variety, FundCategory category) {
        if (category == FundCategory.GOLD) {
            return EstimateBranch.GOLD_PROXY;
        }
        if (category == FundCategory.BOND) {
            return EstimateBranch.BOND_PROXY;
        }
        return EstimateBranch.INDEX_ONLY;
    }

    /**
     * 代理日线转「日期到日收益」的映射，单位百分点
     *
     * @param history 代理日线
     * @return 日收益映射
     */
    private Map<LocalDate, BigDecimal> proxyReturns(MarketHistory history) {
        Map<LocalDate, BigDecimal> returns = new LinkedHashMap<>();
        List<com.lilac.adapter.model.MarketBar> bars = history.getBars();
        for (int i = 1; i < bars.size(); i++) {
            BigDecimal previous = bars.get(i - 1).getClose();
            BigDecimal current = bars.get(i).getClose();
            if (previous == null || current == null || previous.signum() == 0) {
                continue;
            }
            returns.put(bars.get(i).getDate(), current.subtract(previous)
                    .divide(previous, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
        }
        return returns;
    }

    /**
     * 拼一条重放结果并当场判定超阈值与方向命中
     *
     * @param variety     品种
     * @param branch      分支
     * @param day         交易日
     * @param predicted   重放预估值
     * @param actual      实际涨跌幅
     * @param effective   生效阈值
     * @return 逐条结果
     */
    private BacktestItemVO itemOf(VarietyVO variety, EstimateBranch branch, LocalDate day, BigDecimal predicted,
                                  BigDecimal actual, EstimateThresholds effective) {
        BacktestItemVO item = new BacktestItemVO();
        item.setVarietyKey(variety.getVarietyKey());
        item.setVarietyCode(variety.getVarietyCode());
        item.setVarietyName(variety.getVarietyName());
        item.setCategoryLabel(variety.getCategory() == null ? null : variety.getCategory().getLabel());
        item.setTradeDate(day);
        item.setBranchLabel(branch == null ? null : branch.getLabel());
        item.setOutputTier("A");
        item.setEstimatedChange(scale(predicted, RESULT_SCALE));
        item.setActualChange(scale(actual, RESULT_SCALE));
        item.setErrorValue(scale(predicted.subtract(actual), RESULT_SCALE));
        BigDecimal threshold = variety.getCategory() == FundCategory.BOND
                ? effective.getBondErrorThreshold() : effective.getStockErrorThreshold();
        item.setThresholdValue(threshold);
        item.setOverThreshold(predicted.subtract(actual).abs().compareTo(threshold) > 0);
        item.setDirectionHit(directionHit(predicted, actual, effective.getDirectionFloor()));
        return item;
    }

    /**
     * 判定方向是否命中，两侧都靠近零时不参与判定
     *
     * @param predicted 预估涨跌
     * @param actual    实际涨跌
     * @param floor     横盘下限
     * @return 命中返回 true，无法判定返回 null
     */
    private Boolean directionHit(BigDecimal predicted, BigDecimal actual, BigDecimal floor) {
        if (predicted.abs().compareTo(floor) <= 0 || actual.abs().compareTo(floor) <= 0) {
            return null;
        }
        return predicted.signum() == actual.signum();
    }

    /**
     * 逐只汇总 MAE、中位误差、P90、偏差与超阈值次数
     *
     * @param items 逐条结果
     * @return 逐只汇总
     */
    private List<BacktestSummaryVO> summarize(List<BacktestItemVO> items) {
        Map<String, List<BacktestItemVO>> grouped = new LinkedHashMap<>();
        for (BacktestItemVO item : items) {
            grouped.computeIfAbsent(item.getVarietyKey(), key -> new ArrayList<>()).add(item);
        }
        List<BacktestSummaryVO> summaries = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<BacktestItemVO>> entry : grouped.entrySet()) {
            List<BacktestItemVO> rows = entry.getValue();
            BacktestItemVO head = rows.get(0);
            BacktestSummaryVO summary = new BacktestSummaryVO();
            summary.setVarietyKey(entry.getKey());
            summary.setVarietyCode(head.getVarietyCode());
            summary.setVarietyName(head.getVarietyName());
            summary.setCategoryLabel(head.getCategoryLabel());
            summary.setSampleCount(rows.size());
            List<BigDecimal> absErrors = rows.stream().map(row -> row.getErrorValue().abs()).toList();
            summary.setMae(mean(absErrors));
            summary.setMedianAbsError(percentile(absErrors, 50));
            summary.setP90AbsError(percentile(absErrors, PERCENTILE_P90));
            summary.setBias(mean(rows.stream().map(BacktestItemVO::getErrorValue).toList()));
            summary.setOverThresholdCount((int) rows.stream()
                    .filter(row -> Boolean.TRUE.equals(row.getOverThreshold())).count());
            summary.setDirectionAccuracy(ratio(rows.stream().map(BacktestItemVO::getDirectionHit).toList()));
            summary.setIntervalCoverage(null);
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * 填整体统计，口径与逐只汇总一致
     *
     * @param result 回测结果
     * @param items  逐条结果
     * @param notes  说明，直接追加
     */
    private void fillOverall(BacktestResultVO result, List<BacktestItemVO> items, List<String> notes,
                             EstimateThresholds effective) {
        if (items.isEmpty()) {
            return;
        }
        List<BigDecimal> absErrors = items.stream().map(item -> item.getErrorValue().abs()).toList();
        result.setMae(mean(absErrors));
        result.setMedianAbsError(percentile(absErrors, 50));
        result.setP90AbsError(percentile(absErrors, PERCENTILE_P90));
        result.setOverThresholdRatio(ratio(items.stream().map(BacktestItemVO::getOverThreshold).toList()));
        result.setDirectionAccuracy(ratio(items.stream().map(BacktestItemVO::getDirectionHit).toList()));
        List<BacktestItemVO> counted = items.stream()
                .filter(item -> item.getDirectionHit() != null).toList();
        if (counted.isEmpty()) {
            // 全被方向下限挡掉时不能只说「未计入」，否则使用者会以为方向命中率是 0
            notes.add("全部 " + items.size() + " 条记录的涨跌都低于方向下限 "
                    + plain(effective.getDirectionFloor()) + " 个百分点，方向命中率无从计算");
            return;
        }
        long skipped = items.size() - counted.size();
        if (skipped > 0) {
            // 债券日波动常常小于方向下限，不计入方向统计，但必须说清分母剩多少
            notes.add("有 " + skipped + " 条涨跌低于方向下限 " + plain(effective.getDirectionFloor())
                    + " 个百分点未计入方向判定，方向命中率实际基于 " + counted.size() + " 条");
        }
    }

    /**
     * 去掉多余尾零，便于写进说明文案
     *
     * @param value 数值
     * @return 文本，入参为 null 时给破折号
     */
    private String plain(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * 求均值并保留四位小数
     *
     * @param values 数值列表
     * @return 均值，空列表返回 null
     */
    private BigDecimal mean(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), RESULT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 用最近秩法求分位数，样本少时不插值
     *
     * @param values   数值列表
     * @param percent  分位，如 90
     * @return 分位数，空列表返回 null
     */
    private BigDecimal percentile(List<BigDecimal> values, int percent) {
        if (values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(sorted.size() * percent / 100.0) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1))).setScale(RESULT_SCALE,
                RoundingMode.HALF_UP);
    }

    /**
     * 求比例，空列表返回 null
     *
     * @param values 布尔列表
     * @return 比例，0~1
     */
    private BigDecimal ratio(List<Boolean> values) {
        List<Boolean> usable = values.stream().filter(Objects::nonNull).toList();
        if (usable.isEmpty()) {
            return null;
        }
        long hits = usable.stream().filter(Boolean.TRUE::equals).count();
        return BigDecimal.valueOf(hits).divide(BigDecimal.valueOf(usable.size()), RESULT_SCALE,
                RoundingMode.HALF_UP);
    }

    /**
     * 合并请求覆盖值与当前生效配置，得到本次回测真正用的阈值
     *
     * @param request 回测请求
     * @return 生效阈值，为独立副本，不污染单例
     */
    private EstimateThresholds effectiveThresholds(EstimateBacktestRequest request) {
        EstimateThresholds copy = new EstimateThresholds();
        BeanUtil.copyProperties(settingOverrideResolver.effective(thresholds), copy);
        if (request.getAMaeLimit() != null) {
            copy.setAMaeLimit(request.getAMaeLimit());
        }
        if (request.getIndexBetaWindow() != null) {
            copy.setIndexBetaWindow(request.getIndexBetaWindow());
        }
        if (request.getGoldWindow() != null) {
            copy.setGoldWindow(request.getGoldWindow());
        }
        if (request.getBondWindow() != null) {
            copy.setBondWindow(request.getBondWindow());
        }
        if (request.getSigmaFactor() != null) {
            copy.setSigmaFactor(request.getSigmaFactor());
        }
        if (request.getStockErrorThreshold() != null) {
            copy.setStockErrorThreshold(request.getStockErrorThreshold());
        }
        if (request.getBondErrorThreshold() != null) {
            copy.setBondErrorThreshold(request.getBondErrorThreshold());
        }
        return copy;
    }

    /**
     * 回显本次生效参数，保证结果可复现
     *
     * @param effective 生效阈值
     * @return 参数回显
     */
    private BacktestParamVO paramOf(EstimateThresholds effective) {
        BacktestParamVO param = new BacktestParamVO();
        param.setAMaeLimit(effective.getAMaeLimit());
        param.setIndexBetaWindow(effective.getIndexBetaWindow());
        param.setGoldWindow(effective.getGoldWindow());
        param.setBondWindow(effective.getBondWindow());
        param.setSigmaFactor(effective.getSigmaFactor());
        param.setStockErrorThreshold(effective.getStockErrorThreshold());
        param.setBondErrorThreshold(effective.getBondErrorThreshold());
        return param;
    }

    /**
     * 解析本次要回测的品种，未指定时取全部品种
     *
     * @param varietyKeys 品种业务主键
     * @return 品种列表
     */
    private List<VarietyVO> resolveVarieties(List<String> varietyKeys) {
        VarietyQueryRequest query = new VarietyQueryRequest();
        List<VarietyVO> all = varietyService.listVariety(query);
        if (varietyKeys == null || varietyKeys.isEmpty()) {
            return all == null ? List.of() : all;
        }
        Map<String, VarietyVO> byKey = new LinkedHashMap<>();
        for (VarietyVO variety : all) {
            byKey.put(variety.getVarietyKey(), variety);
        }
        List<VarietyVO> picked = new ArrayList<>(varietyKeys.size());
        for (String key : varietyKeys) {
            VarietyVO variety = byKey.get(key);
            if (variety != null) {
                picked.add(variety);
            }
        }
        return picked;
    }

    /**
     * 解析日期字符串，空或格式非法时用兜底值
     *
     * @param raw      原始日期
     * @param fallback 兜底日期
     * @return 日期
     */
    private LocalDate parseDate(String raw, LocalDate fallback) {
        return raw == null || raw.isBlank() ? fallback : LocalDate.parse(raw.trim());
    }

    /**
     * 统一保留小数位
     *
     * @param value 原始值
     * @param scale 小数位
     * @return 保留后的值，入参为 null 时返回 null
     */
    private BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }
}

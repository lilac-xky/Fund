package com.lilac.service.impl;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.domain.dto.estimate.CalibrationRequest;
import com.lilac.domain.dto.estimate.EstimateErrorQueryRequest;
import com.lilac.domain.dto.estimate.EstimateErrorSaveRequest;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.CalibrationGapVO;
import com.lilac.domain.vo.CalibrationItemVO;
import com.lilac.domain.vo.CalibrationResultVO;
import com.lilac.domain.vo.EstimateErrorTrendVO;
import com.lilac.domain.vo.EstimateErrorVO;
import com.lilac.domain.vo.EstimateSnapshotVO;
import com.lilac.domain.vo.GoldBetaRecordVO;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.engine.CalibrationJudge;
import com.lilac.engine.ErrorHistoryCalculator;
import com.lilac.engine.EstimateThresholds;
import com.lilac.engine.TierResolver;
import com.lilac.engine.model.EstimateCalibrationVerdict;
import com.lilac.engine.model.EstimateHistoryStats;
import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingStatus;
import com.lilac.service.EstimateCalibrationService;
import com.lilac.service.EstimateErrorService;
import com.lilac.service.EstimateGoldBetaService;
import com.lilac.service.EstimateSnapshotService;
import com.lilac.service.FundNetWorthService;
import com.lilac.service.HoldingService;
import com.lilac.service.VarietyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 晚间校准业务实现，用当日实际净值反向检验白天预估，写误差历史并滚动判定档位
 */
@Slf4j
@Service
public class EstimateCalibrationServiceImpl implements EstimateCalibrationService {

    private static final int HISTORY_LOOKBACK_DAYS = 150;
    private static final int DEFAULT_TREND_DAYS = 30;
    private static final String SOURCE_SNAPSHOT = "SNAPSHOT";

    @Resource
    private EstimateSnapshotService estimateSnapshotService;

    @Resource
    private EstimateErrorService estimateErrorService;

    @Resource
    private EstimateGoldBetaService estimateGoldBetaService;

    @Resource
    private FundNetWorthService fundNetWorthService;

    @Resource
    private HoldingService holdingService;

    @Resource
    private VarietyService varietyService;

    @Resource
    private CalibrationJudge calibrationJudge;

    @Resource
    private TierResolver tierResolver;

    @Resource
    private ErrorHistoryCalculator errorHistoryCalculator;

    @Resource
    private EstimateThresholds thresholds;

    /**
     * 执行某一交易日的晚间校准，同一交易日重复执行只覆盖不重复累计
     *
     * @param request 校准请求
     * @return 校准汇总
     */
    @Override
    public CalibrationResultVO calibrate(CalibrationRequest request) {
        CalibrationRequest query = request == null ? new CalibrationRequest() : request;
        LocalDate tradeDate = query.getTradeDate() == null ? LocalDate.now() : query.getTradeDate();
        Map<String, EstimateSnapshotVO> snapshots = snapshotMap(tradeDate);
        List<VarietyVO> varieties = resolveVarieties(query, snapshots);
        List<CalibrationItemVO> items = new ArrayList<>();
        List<CalibrationGapVO> gaps = new ArrayList<>();
        int written = 0;
        for (VarietyVO variety : varieties) {
            EstimateSnapshotVO snapshot = snapshots.get(variety.getVarietyKey());
            BigDecimal actual = actualChange(variety.getVarietyCode(), tradeDate);
            if (actual == null) {
                gaps.add(gap(variety, "当日净值未公布，上游最新净值为 "
                        + latestNetWorthDate(variety.getVarietyCode()) + "，待净值更新后重跑校准"));
                continue;
            }
            if (snapshot == null) {
                gaps.add(gap(variety, "当日预估留存缺失，无法核对，请确认白天已跑过预估"));
                continue;
            }
            CalibrationItemVO item = calibrateOne(variety, snapshot, actual);
            if (item == null) {
                gaps.add(gap(variety, "当时未给出任何可对照输出，不参与误差统计"));
                continue;
            }
            // 先把滚动档位定下来再落库，误差表里的档位才是校准后的结论
            fillRolling(item, variety.getVarietyKey(), tradeDate);
            if (estimateErrorService.saveEstimateError(errorRequest(item))) {
                written++;
            }
            recordGoldBeta(variety, snapshot, tradeDate);
            items.add(item);
        }
        items.sort((left, right) -> Boolean.compare(Boolean.TRUE.equals(right.getOverThreshold()),
                Boolean.TRUE.equals(left.getOverThreshold())));
        CalibrationResultVO result = new CalibrationResultVO();
        result.setTradeDate(tradeDate);
        result.setItems(items);
        result.setOverThresholdItems(items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getOverThreshold())).toList());
        result.setSuspensionHints(items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getSuggestSuspend())).toList());
        result.setGaps(gaps);
        result.setCalibratedCount(items.size());
        result.setOverThresholdCount(result.getOverThresholdItems().size());
        result.setGapCount(gaps.size());
        result.setWrittenCount(written);
        if (!gaps.isEmpty()) {
            result.setNotice("有 " + gaps.size() + " 只未能校准，已逐只说明原因，未参与误差统计");
        } else if (items.isEmpty()) {
            // 既无持仓也无当日留存时必须说清，否则页面会显示成「校准成功但什么也没有」
            result.setNotice("本次没有可校准的基金：未指定基金、无持有中持仓、当日也无预估留存");
        }
        log.info("晚间校准完成 日期={} 已校准 {} 只 超阈值 {} 只 未校准 {} 只",
                tradeDate, items.size(), result.getOverThresholdCount(), gaps.size());
        return result;
    }

    /**
     * 查询单只基金的误差趋势，供复盘页与停用判断
     *
     * @param varietyKey 品种业务主键
     * @param days       回溯自然日数
     * @return 误差趋势
     */
    @Override
    public EstimateErrorTrendVO trend(String varietyKey, int days) {
        int span = days <= 0 ? DEFAULT_TREND_DAYS : days;
        LocalDate end = LocalDate.now();
        EstimateErrorQueryRequest query = new EstimateErrorQueryRequest();
        query.setVarietyKey(varietyKey);
        query.setStartDate(end.minusDays(span));
        query.setEndDate(end);
        List<EstimateErrorVO> series = estimateErrorService.listEstimateError(query);
        VarietyVO variety = findVariety(varietyKey);
        FundCategory category = variety == null ? FundCategory.UNKNOWN : variety.getCategory();
        EstimateHistoryStats stats = summarize(series);
        EstimateErrorTrendVO trend = new EstimateErrorTrendVO();
        trend.setVarietyKey(varietyKey);
        trend.setVarietyCode(variety == null ? null : variety.getVarietyCode());
        trend.setVarietyName(variety == null ? null : variety.getVarietyName());
        trend.setCategory(category);
        trend.setCategoryLabel(category == null ? null : category.getLabel());
        trend.setSampleCount(stats.getSampleCount());
        trend.setMedianAbsError(stats.getMedianAbsError());
        trend.setMae(stats.getMae());
        trend.setDirectionAccuracy(stats.getDirectionAccuracy());
        trend.setIntervalCoverage(stats.getIntervalCoverage());
        trend.setRollingTier(tierResolver.resolve(category, stats, stats, previousTier(series), thresholds));
        trend.setLatestTradeDate(series.isEmpty() ? null : series.get(series.size() - 1).getTradeDate());
        trend.setConsecutiveOverCount(consecutiveOver(series));
        trend.setSuggestSuspend(trend.getConsecutiveOverCount() >= thresholds.getSuspendConsecutiveDays());
        trend.setSeries(series);
        return trend;
    }

    /**
     * 列出需要提示「建议停用该基金预估」的基金
     *
     * @param tradeDate 判定日
     * @return 建议停用清单
     */
    @Override
    public List<EstimateErrorTrendVO> suspensionHints(LocalDate tradeDate) {
        LocalDate end = tradeDate == null ? LocalDate.now() : tradeDate;
        EstimateErrorQueryRequest query = new EstimateErrorQueryRequest();
        query.setStartDate(end.minusDays(DEFAULT_TREND_DAYS));
        query.setEndDate(end);
        Map<String, Integer> consecutive = new LinkedHashMap<>();
        for (EstimateErrorVO row : estimateErrorService.listEstimateError(query)) {
            consecutive.merge(row.getVarietyKey(), Boolean.TRUE.equals(row.getOverThreshold()) ? 1 : 0,
                    (left, right) -> right == 0 ? 0 : left + 1);
        }
        List<EstimateErrorTrendVO> hints = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : consecutive.entrySet()) {
            if (entry.getValue() >= thresholds.getSuspendConsecutiveDays()) {
                hints.add(trend(entry.getKey(), DEFAULT_TREND_DAYS));
            }
        }
        return hints;
    }

    /**
     * 校准单只基金，返回 null 表示这一只没有可对照的输出
     *
     * @param variety  品种
     * @param snapshot 当日预估留存
     * @param actual   当日实际涨跌幅
     * @return 校准条目
     */
    private CalibrationItemVO calibrateOne(VarietyVO variety, EstimateSnapshotVO snapshot, BigDecimal actual) {
        FundCategory category = snapshot.getCategory() == null ? variety.getCategory() : snapshot.getCategory();
        if (snapshot.getEstimatedChange() == null && snapshot.getDirection() == null) {
            return null;
        }
        EstimateCalibrationVerdict verdict = calibrationJudge.judge(category, snapshot.getDirection(),
                snapshot.getEstimatedChange(), actual, snapshot.getIntervalLow(), snapshot.getIntervalHigh(),
                thresholds);
        CalibrationItemVO item = new CalibrationItemVO();
        item.setVarietyKey(variety.getVarietyKey());
        item.setVarietyCode(variety.getVarietyCode());
        item.setVarietyName(variety.getVarietyName());
        item.setCategory(category);
        item.setCategoryLabel(category == null ? null : category.getLabel());
        item.setTradeDate(snapshot.getTradeDate());
        item.setOutputTier(snapshot.getOutputTier());
        item.setEstimatedChange(verdict.getEstimatedChange());
        item.setActualChange(verdict.getActualChange());
        item.setErrorValue(verdict.getErrorValue());
        item.setThresholdValue(verdict.getThresholdValue());
        item.setOverThreshold(verdict.getOverThreshold());
        item.setDirectionHit(verdict.getDirectionHit());
        item.setInInterval(verdict.getInInterval());
        item.setIntervalLow(snapshot.getIntervalLow());
        item.setIntervalHigh(snapshot.getIntervalHigh());
        item.setIntervalWidth(verdict.getIntervalWidth());
        item.setHoldingCutoffDate(snapshot.getHoldingCutoffDate());
        item.setHoldingDisclosureDate(snapshot.getHoldingDisclosureDate());
        item.setEstimateSource(SOURCE_SNAPSHOT);
        item.setBeta(snapshot.getBeta());
        item.setRho(snapshot.getRho());
        item.setResidualP90(snapshot.getResidualP90());
        item.setRemarks(remarksOf(item, category));
        return item;
    }

    /**
     * 拼装本条的说明文字，混合型与超阈值两种情形必须同屏说清
     *
     * @param item     校准条目
     * @param category 基金类型
     * @return 说明列表
     */
    private List<String> remarksOf(CalibrationItemVO item, FundCategory category) {
        List<String> remarks = new ArrayList<>();
        if (category == FundCategory.MIXED) {
            remarks.add("混合型不参与点估计阈值判定，改为对照方向命中与区间覆盖");
        }
        if (category == FundCategory.BOND) {
            remarks.add("债券型使用独立阈值 " + plain(item.getThresholdValue()) + "，不套用 0.3");
        }
        if (Boolean.TRUE.equals(item.getOverThreshold())) {
            remarks.add("本期误差超过阈值，该基金预估值后续是否采信需人工判断");
        }
        return remarks;
    }

    /**
     * 把今天这条记录并入滚动窗口，判定档位与连续超阈值期数
     *
     * @param item       校准条目
     * @param varietyKey 品种业务主键
     * @param tradeDate  校准交易日
     */
    private void fillRolling(CalibrationItemVO item, String varietyKey, LocalDate tradeDate) {
        List<EstimateErrorVO> history = loadHistory(varietyKey, tradeDate);
        history.add(currentRow(item));
        EstimateHistoryStats stats = summarize(history);
        EstimateTier tier = tierResolver.resolve(item.getCategory(), stats, stats, previousTier(history),
                thresholds);
        item.setRollingTier(tier);
        item.setMedianAbsError(stats.getMedianAbsError());
        item.setDirectionAccuracy(stats.getDirectionAccuracy());
        item.setIntervalCoverage(stats.getIntervalCoverage());
        item.setConsecutiveOverCount(consecutiveOver(history));
        item.setSuggestSuspend(item.getConsecutiveOverCount() >= thresholds.getSuspendConsecutiveDays());
        if (Boolean.TRUE.equals(item.getSuggestSuspend())) {
            item.getRemarks().add("连续 " + item.getConsecutiveOverCount() + " 期超阈值，建议停用该基金的预估");
        }
        if (tier == EstimateTier.C && item.getCategory() == FundCategory.MIXED) {
            // PRD 第 9 节：混合型连续多期方向准确率不达标时降级为无法预估
            item.getRemarks().add("滚动窗口方向准确率或区间覆盖率不达标，已降级为无法预估");
        }
    }

    /**
     * 把当前条目拼成一条误差记录，供滚动窗口在落库前即可参与统计
     *
     * @param item 校准条目
     * @return 误差记录
     */
    private EstimateErrorVO currentRow(CalibrationItemVO item) {
        EstimateErrorVO row = new EstimateErrorVO();
        row.setVarietyKey(item.getVarietyKey());
        row.setTradeDate(item.getTradeDate());
        row.setEstimatedChange(item.getEstimatedChange());
        row.setActualChange(item.getActualChange());
        row.setOverThreshold(item.getOverThreshold());
        row.setDirectionHit(item.getDirectionHit());
        row.setInInterval(item.getInInterval());
        row.setIntervalWidth(item.getIntervalWidth());
        return row;
    }

    /**
     * 记录黄金 β，供 ISSUE-007 的 β+AR(1) 估计复用与交叉校验
     *
     * @param variety   品种
     * @param snapshot  当日预估留存
     * @param tradeDate 校准交易日
     */
    private void recordGoldBeta(VarietyVO variety, EstimateSnapshotVO snapshot, LocalDate tradeDate) {
        if (variety.getCategory() != FundCategory.GOLD || snapshot.getBeta() == null) {
            return;
        }
        GoldBetaRecordVO record = new GoldBetaRecordVO();
        record.setVarietyKey(variety.getVarietyKey());
        record.setTradeDate(tradeDate);
        record.setBeta(snapshot.getBeta());
        record.setRho(snapshot.getRho());
        record.setResidualP90(snapshot.getResidualP90());
        record.setFallback(snapshot.getBeta().compareTo(BigDecimal.ONE) == 0);
        estimateGoldBetaService.save(record);
    }

    /**
     * 取当日实际净值涨跌幅，取不到返回 null
     *
     * @param fundCode  6 位基金代码
     * @param tradeDate 交易日
     * @return 实际涨跌幅，百分点
     */
    private BigDecimal actualChange(String fundCode, LocalDate tradeDate) {
        List<FundNetWorthPoint> points = fundNetWorthService.listRange(fundCode,
                tradeDate.minusDays(thresholds.getActualLookbackDays()), tradeDate);
        return points.stream()
                .filter(point -> tradeDate.equals(point.getNetWorthDate()))
                .map(FundNetWorthPoint::getEquityReturn)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * 取库内最新净值日期，用于说明净值为何还没公布
     *
     * @param fundCode 6 位基金代码
     * @return 最新净值日期，无数据返回 null
     */
    private LocalDate latestNetWorthDate(String fundCode) {
        return fundNetWorthService.latestDate(fundCode);
    }

    /**
     * 取该交易日全部预估留存，按品种业务主键索引
     *
     * @param tradeDate 交易日
     * @return 留存映射
     */
    private Map<String, EstimateSnapshotVO> snapshotMap(LocalDate tradeDate) {
        Map<String, EstimateSnapshotVO> map = new LinkedHashMap<>();
        for (EstimateSnapshotVO snapshot : estimateSnapshotService.listByDate(tradeDate)) {
            map.put(snapshot.getVarietyKey(), snapshot);
        }
        return map;
    }

    /**
     * 解析本次要校准的基金，指定了列表就按列表，否则按全部持有中的持仓
     *
     * @param request   校准请求
     * @param snapshots 当日留存
     * @return 品种列表
     */
    private List<VarietyVO> resolveVarieties(CalibrationRequest request, Map<String, EstimateSnapshotVO> snapshots) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (request.getVarietyKeys() != null && !request.getVarietyKeys().isEmpty()) {
            request.getVarietyKeys().stream()
                    .filter(key -> key != null && !key.isBlank())
                    .map(String::trim)
                    .forEach(keys::add);
        } else {
            HoldingQueryRequest holdingQuery = new HoldingQueryRequest();
            holdingQuery.setStatus(HoldingStatus.HOLDING);
            for (HoldingVO holding : holdingService.listHolding(holdingQuery)) {
                if (holding.getVarietyKey() != null && !holding.getVarietyKey().isBlank()) {
                    keys.add(holding.getVarietyKey());
                }
            }
        }
        if (keys.isEmpty()) {
            // 未录持仓时按当日留存回退，保证校准不会因为持仓为空而什么都不做
            keys.addAll(snapshots.keySet());
        }
        List<VarietyVO> varieties = new ArrayList<>(keys.size());
        for (String key : keys) {
            VarietyVO variety = findVariety(key);
            if (variety != null) {
                varieties.add(variety);
            }
        }
        return varieties;
    }

    /**
     * 按业务主键查品种
     *
     * @param varietyKey 品种业务主键
     * @return 品种，未找到返回 null
     */
    private VarietyVO findVariety(String varietyKey) {
        VarietyQueryRequest query = new VarietyQueryRequest();
        query.setVarietyKey(varietyKey);
        List<VarietyVO> found = varietyService.listVariety(query);
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * 取滚动窗口内的误差历史，只算校准日之前
     *
     * @param varietyKey 品种业务主键
     * @param tradeDate  校准交易日
     * @return 误差记录，按交易日升序
     */
    private List<EstimateErrorVO> loadHistory(String varietyKey, LocalDate tradeDate) {
        EstimateErrorQueryRequest query = new EstimateErrorQueryRequest();
        query.setVarietyKey(varietyKey);
        query.setStartDate(tradeDate.minusDays(HISTORY_LOOKBACK_DAYS));
        query.setEndDate(tradeDate.minusDays(1));
        return new ArrayList<>(estimateErrorService.listEstimateError(query));
    }

    /**
     * 按长窗口统计误差
     *
     * @param history 误差记录
     * @return 统计结果
     */
    private EstimateHistoryStats summarize(List<EstimateErrorVO> history) {
        return errorHistoryCalculator.summarize(history, thresholds.getHistoryWindow(),
                thresholds.getSigmaFactor());
    }

    /**
     * 取最近一次记录的档位
     *
     * @param history 误差记录
     * @return 档位，无记录返回 null
     */
    private EstimateTier previousTier(List<EstimateErrorVO> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        String tier = history.get(history.size() - 1).getOutputTier();
        if (tier == null || tier.isBlank()) {
            return null;
        }
        try {
            return EstimateTier.valueOf(tier.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 统计从最后一天往前连续超阈值的期数
     *
     * @param history 误差记录，按交易日升序
     * @return 连续期数
     */
    private int consecutiveOver(List<EstimateErrorVO> history) {
        int count = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (!Boolean.TRUE.equals(history.get(i).getOverThreshold())) {
                break;
            }
            count++;
        }
        return count;
    }

    /**
     * 把误差记录转保存请求
     *
     * @param item 校准条目
     * @return 保存请求
     */
    private EstimateErrorSaveRequest errorRequest(CalibrationItemVO item) {
        EstimateErrorSaveRequest request = new EstimateErrorSaveRequest();
        request.setVarietyKey(item.getVarietyKey());
        request.setTradeDate(item.getTradeDate());
        request.setEstimatedChange(item.getEstimatedChange());
        request.setActualChange(item.getActualChange());
        request.setErrorValue(item.getErrorValue());
        request.setOverThreshold(item.getOverThreshold());
        request.setThresholdValue(item.getThresholdValue());
        request.setDirectionHit(item.getDirectionHit());
        request.setInInterval(item.getInInterval());
        request.setIntervalWidth(item.getIntervalWidth());
        request.setHoldingCutoffDate(item.getHoldingCutoffDate());
        request.setHoldingDisclosureDate(item.getHoldingDisclosureDate());
        request.setOutputTier(item.getRollingTier() == null ? null : item.getRollingTier().name());
        request.setEstimateSource(item.getEstimateSource());
        return request;
    }

    /**
     * 拼装未校准条目
     *
     * @param variety 品种
     * @param reason  原因
     * @return 未校准条目
     */
    private CalibrationGapVO gap(VarietyVO variety, String reason) {
        CalibrationGapVO gap = new CalibrationGapVO();
        gap.setVarietyKey(variety.getVarietyKey());
        gap.setVarietyCode(variety.getVarietyCode());
        gap.setVarietyName(variety.getVarietyName());
        gap.setReason(reason);
        return gap;
    }

    /**
     * 去掉多余尾零便于文案展示
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }
}

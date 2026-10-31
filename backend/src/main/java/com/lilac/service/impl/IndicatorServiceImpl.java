package com.lilac.service.impl;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.MarketBar;
import com.lilac.adapter.model.MarketHistory;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.indicator.IndicatorQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.IndicatorAdvisoryVO;
import com.lilac.domain.vo.IndicatorVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.engine.BenchmarkResolver;
import com.lilac.engine.IndicatorCache;
import com.lilac.enums.IndicatorBasis;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.IndicatorSignal;
import com.lilac.enums.VarietyType;
import com.lilac.service.FundNetWorthService;
import com.lilac.service.HoldingService;
import com.lilac.service.IndicatorService;
import com.lilac.service.VarietyService;
import com.lilac.strategy.IndicatorCalculator;
import com.lilac.strategy.IndicatorPlanResolver;
import com.lilac.strategy.model.IndicatorPlan;
import com.lilac.strategy.model.IndicatorResult;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * 技术指标业务实现，取前复权日线交给计算器，结果按参数指纹缓存
 */
@Slf4j
@Service
public class IndicatorServiceImpl implements IndicatorService {

    private static final int MIN_LIMIT = 60;

    /**
     * 场外基金取多少天的净值，约一年交易日，够算全部指标
     */
    private static final int NET_WORTH_DAYS = 400;
    private static final String NO_HOLDING = "尚无持有中的标的，指标先录入持仓才会开始工作";

    @Resource
    private IndicatorPlanResolver indicatorPlanResolver;

    @Resource
    private IndicatorCalculator indicatorCalculator;

    @Resource
    private IndicatorCache indicatorCache;

    @Resource
    private MarketQuoteAdapter marketQuoteAdapter;

    @Resource
    private HoldingService holdingService;

    @Resource
    private VarietyService varietyService;

    @Resource
    private FundNetWorthService fundNetWorthService;

    /**
     * 对单只标的算指标，代码与品种主键二选一
     *
     * @param request 查询请求
     * @return 指标视图
     */
    @Override
    public IndicatorVO computeOne(IndicatorQueryRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR, "指标查询请求不能为空");
        boolean hasCode = request.getCode() != null && !request.getCode().isBlank();
        boolean hasKey = request.getVarietyKey() != null && !request.getVarietyKey().isBlank();
        ThrowUtils.throwIf(!hasCode && !hasKey, HttpsCodeEnum.PARAMS_ERROR,
                "必须指定行情代码或品种业务主键");
        IndicatorPlan plan = indicatorPlanResolver.resolve();
        if (hasKey) {
            VarietyVO variety = findVariety(request.getVarietyKey().trim());
            ThrowUtils.throwIf(variety == null, HttpsCodeEnum.NOT_FOUND_ERROR,
                    "未找到品种 " + request.getVarietyKey());
            // 场外基金没有日线行情，改用净值序列算指标，否则永远取不到数据
            if (variety.getVarietyType() != VarietyType.ETF) {
                IndicatorVO vo = computeByNetWorth(variety, plan);
                vo.setVarietyKey(variety.getVarietyKey());
                vo.setVarietyCode(variety.getVarietyCode());
                vo.setVarietyName(variety.getVarietyName());
                return vo;
            }
            String symbol = BenchmarkResolver.symbolOf(variety.getVarietyCode());
            ThrowUtils.throwIf(symbol == null, HttpsCodeEnum.PARAMS_ERROR,
                    "该品种的代码推不出行情市场，暂不能计算指标");
            IndicatorVO vo = compute(symbol, plan, request.getLimit());
            vo.setVarietyKey(variety.getVarietyKey());
            vo.setVarietyCode(variety.getVarietyCode());
            vo.setVarietyName(variety.getVarietyName());
            return vo;
        }
        return compute(request.getCode().trim(), plan, request.getLimit());
    }

    /**
     * 用历史净值算场外基金的指标，净值当收盘价，缺高低价故 KDJ 不可算
     *
     * @param variety 品种
     * @param plan    指标参数
     * @return 指标视图
     */
    private IndicatorVO computeByNetWorth(VarietyVO variety, IndicatorPlan plan) {
        IndicatorVO vo = new IndicatorVO();
        vo.setSymbol(variety.getVarietyCode());
        vo.setParamSummary(paramSummary(plan));
        vo.setBasis(IndicatorBasis.NET_WORTH);
        vo.setBasisLabel(IndicatorBasis.NET_WORTH.getLabel());
        if (!plan.complete()) {
            vo.setComplete(Boolean.FALSE);
            vo.setCached(Boolean.FALSE);
            vo.setUnavailableReason("指标参数不合法，暂不计算：" + String.join("；", plan.getIssues()));
            vo.setRemarks(List.of(vo.getUnavailableReason()));
            vo.setSignals(List.of());
            vo.setSignalLabels(List.of());
            return vo;
        }
        List<FundNetWorthPoint> points = fundNetWorthService.listRange(variety.getVarietyCode(),
                LocalDate.now().minusDays(NET_WORTH_DAYS), null);
        List<MarketBar> bars = toBars(points);
        IndicatorResult result = indicatorCalculator.compute(bars, plan);
        result.setSymbol(variety.getVarietyCode());
        result.setAdjusted(Boolean.FALSE);
        result.setSource("netWorth");
        fill(vo, result, Boolean.FALSE);
        // 净值没有最高最低价，KDJ 必然算不出，用一句面向使用者的说明替换计算器的技术措辞
        if (result.getK() == null) {
            vo.getRemarks().removeIf(text -> text.contains("最高价或最低价"));
            vo.getRemarks().add("场外基金只有单位净值，没有日线最高最低价，KDJ 无法计算；"
                    + "RSI 与 MACD 按净值序列计算");
        }
        if (result.complete()) {
            indicatorCache.save(variety.getVarietyCode(), signature(plan), result);
        }
        return vo;
    }

    /**
     * 净值点转成日线形状，最高最低价取净值本身，故 KDJ 判为不可算
     *
     * @param points 净值序列
     * @return 日线形状
     */
    private List<MarketBar> toBars(List<FundNetWorthPoint> points) {
        List<MarketBar> bars = new ArrayList<>(points.size());
        for (FundNetWorthPoint point : points) {
            if (point.getNetWorthDate() == null || point.getUnitNetWorth() == null) {
                continue;
            }
            bars.add(new MarketBar(point.getNetWorthDate(), point.getUnitNetWorth()));
        }
        return bars;
    }

    /**
     * 对全部持有中的标的算指标，逐只失败不影响其他标的
     *
     * @return 指标总览
     */
    @Override
    public IndicatorAdvisoryVO computeAll() {
        IndicatorPlan plan = indicatorPlanResolver.resolve();
        List<VarietyVO> varieties = holdingVarieties();
        List<IndicatorVO> items = new ArrayList<>();
        List<IndicatorVO> unavailable = new ArrayList<>();
        int signalCount = 0;
        for (VarietyVO variety : varieties) {
            IndicatorVO vo;
            try {
                if (variety.getVarietyType() != VarietyType.ETF) {
                    vo = computeByNetWorth(variety, plan);
                } else {
                    String symbol = BenchmarkResolver.symbolOf(variety.getVarietyCode());
                    if (symbol == null) {
                        vo = blank(variety.getVarietyCode(), "该品种的代码推不出行情市场，暂不能计算指标");
                    } else {
                        vo = compute(symbol, plan, 0);
                    }
                }
            } catch (RuntimeException e) {
                // 单只取数失败只是这一只没指标，不能让整批指标都出不来
                log.warn("指标计算失败 code={}：{}", variety.getVarietyCode(), e.getMessage());
                vo = blank(variety.getVarietyCode(), "指标计算失败：" + e.getMessage());
            }
            vo.setVarietyKey(variety.getVarietyKey());
            vo.setVarietyCode(variety.getVarietyCode());
            vo.setVarietyName(variety.getVarietyName());
            if (Boolean.TRUE.equals(vo.getComplete())) {
                items.add(vo);
                signalCount += vo.getSignals() == null ? 0 : vo.getSignals().size();
            } else {
                unavailable.add(vo);
            }
        }
        return assemble(plan, varieties.size(), items, unavailable, signalCount);
    }

    /**
     * 取日线算指标，先查缓存再取数
     *
     * @param symbol 标的完整代码
     * @param plan   指标参数
     * @param limit  日线根数，小于等于 0 时按参数需要自动取
     * @return 指标视图
     */
    private IndicatorVO compute(String symbol, IndicatorPlan plan, Integer limit) {
        IndicatorVO vo = new IndicatorVO();
        vo.setSymbol(symbol);
        vo.setParamSummary(paramSummary(plan));
        vo.setBasis(IndicatorBasis.DAILY_BAR);
        vo.setBasisLabel(IndicatorBasis.DAILY_BAR.getLabel());
        if (!plan.complete()) {
            vo.setComplete(Boolean.FALSE);
            vo.setCached(Boolean.FALSE);
            vo.setUnavailableReason("指标参数不合法，暂不计算：" + String.join("；", plan.getIssues()));
            vo.setRemarks(List.of(vo.getUnavailableReason()));
            vo.setSignals(List.of());
            vo.setSignalLabels(List.of());
            return vo;
        }
        String signature = signature(plan);
        Optional<IndicatorResult> cached = indicatorCache.read(symbol, signature);
        if (cached.isPresent()) {
            fill(vo, cached.get(), Boolean.TRUE);
            return vo;
        }
        int rows = limit != null && limit > 0 ? Math.max(limit, indicatorCalculator.requiredBars(plan))
                : Math.max(MIN_LIMIT, indicatorCalculator.requiredBars(plan));
        MarketHistory history = marketQuoteAdapter.history(symbol, rows);
        List<MarketBar> bars = history.getBars();
        IndicatorResult result = indicatorCalculator.compute(bars, plan);
        result.setSymbol(symbol);
        result.setAdjusted(history.getAdjusted());
        result.setSource(history.getSource());
        fill(vo, result, Boolean.FALSE);
        if (result.complete()) {
            indicatorCache.save(symbol, signature, result);
        }
        return vo;
    }

    /**
     * 把计算结果写进视图
     *
     * @param vo     视图
     * @param result 计算结果
     * @param cached 是否来自缓存
     */
    private void fill(IndicatorVO vo, IndicatorResult result, Boolean cached) {
        vo.setRsi(result.getRsi());
        vo.setK(result.getK());
        vo.setD(result.getD());
        vo.setJ(result.getJ());
        vo.setDif(result.getDif());
        vo.setDea(result.getDea());
        vo.setMacdBar(result.getMacdBar());
        vo.setBarDate(result.getBarDate());
        vo.setSamples(result.getSamples());
        vo.setAdjusted(result.getAdjusted());
        vo.setSource(result.getSource());
        vo.setComplete(result.complete());
        vo.setCached(cached);
        vo.setSignals(result.getSignals());
        vo.setSignalLabels(result.getSignals().stream().map(IndicatorSignal::getLabel).toList());
        // 复制一份，调用方可能改写说明文字，不能连带改到计算器返回值与缓存
        vo.setRemarks(new ArrayList<>(result.getRemarks()));
        vo.setUnavailableReason(result.getUnavailableReason());
    }

    /**
     * 汇总成总览，逐条摘要供邮件直接取用
     *
     * @param plan         指标参数
     * @param requested    纳入的标的数
     * @param items        算出指标的标的
     * @param unavailable  无法计算的标的
     * @param signalCount  触发信号条数
     * @return 指标总览
     */
    private IndicatorAdvisoryVO assemble(IndicatorPlan plan, int requested, List<IndicatorVO> items,
                                         List<IndicatorVO> unavailable, int signalCount) {
        IndicatorAdvisoryVO vo = new IndicatorAdvisoryVO();
        vo.setItems(items);
        vo.setUnavailableItems(unavailable);
        vo.setRequested(requested);
        vo.setCompleteCount(items.size());
        vo.setUnavailableCount(unavailable.size());
        vo.setSignalCount(signalCount);
        vo.setParamUsable(plan.complete());
        vo.setParamIssues(plan.getIssues());
        vo.setParamSummary(paramSummary(plan));
        vo.setParticipateInAlert(plan.alertEnabled());
        vo.setSummary(summaryOf(items, unavailable));
        if (requested == 0) {
            vo.setNotice(NO_HOLDING);
        }
        log.info("指标计算完成 标的 {} 只 算出 {} 只 无法计算 {} 只 信号 {} 条",
                requested, items.size(), unavailable.size(), signalCount);
        return vo;
    }

    /**
     * 拼逐条摘要，只把有信号的写进邮件，免得正文被十几个正常值淹没
     *
     * @param items       算出指标的标的
     * @param unavailable 无法计算的标的
     * @return 摘要列表
     */
    private List<String> summaryOf(List<IndicatorVO> items, List<IndicatorVO> unavailable) {
        List<String> summary = new ArrayList<>();
        for (IndicatorVO item : items) {
            if (item.getSignals() == null || item.getSignals().isEmpty()) {
                continue;
            }
            List<String> labels = new ArrayList<>();
            for (IndicatorSignal signal : item.getSignals()) {
                labels.add(signal.getLabel() + "（" + signal.getNote() + "）");
            }
            summary.add(item.getVarietyCode() + " " + item.getVarietyName() + "：" + String.join("、", labels)
                    + "，RSI " + plain(item.getRsi()) + "，K " + plain(item.getK()) + "，D " + plain(item.getD())
                    + "，DIF " + plain(item.getDif()) + "，DEA " + plain(item.getDea()));
        }
        for (IndicatorVO item : unavailable) {
            summary.add(item.getVarietyCode() + " " + item.getVarietyName() + "：无法计算指标，"
                    + (item.getUnavailableReason() == null ? "原因见页面" : item.getUnavailableReason()));
        }
        return summary;
    }

    /**
     * 生成参数摘要，页面必须与计算层用同一套参数
     *
     * @param plan 指标参数
     * @return 摘要文字
     */
    private String paramSummary(IndicatorPlan plan) {
        if (plan == null || !plan.complete()) {
            return "参数不完整";
        }
        return "RSI(" + plan.getRsiPeriod() + ") 超买 " + plain(plan.getRsiUpper()) + " 超卖 "
                + plain(plan.getRsiLower()) + "；KDJ(" + plan.getKdjPeriod() + "," + plan.getKdjKSmooth()
                + "," + plan.getKdjDSmooth() + ")；MACD(" + plan.getMacdFast() + "," + plan.getMacdSlow()
                + "," + plan.getMacdSignal() + ")";
    }

    /**
     * 生成参数指纹，参数改了就不该命中缓存
     *
     * @param plan 指标参数
     * @return 指纹
     */
    private String signature(IndicatorPlan plan) {
        return plan.getRsiPeriod() + "_" + plan.getKdjPeriod() + "_" + plan.getKdjKSmooth() + "_"
                + plan.getKdjDSmooth() + "_" + plan.getMacdFast() + "_" + plan.getMacdSlow() + "_"
                + plan.getMacdSignal();
    }

    /**
     * 造一个不可计算的视图
     *
     * @param code   6 位代码
     * @param reason 原因
     * @return 视图
     */
    private IndicatorVO blank(String code, String reason) {
        IndicatorVO vo = new IndicatorVO();
        vo.setVarietyCode(code);
        vo.setComplete(Boolean.FALSE);
        vo.setCached(Boolean.FALSE);
        vo.setUnavailableReason(reason);
        vo.setRemarks(List.of(reason));
        vo.setSignals(List.of());
        vo.setSignalLabels(List.of());
        return vo;
    }

    /**
     * 取全部持有中的品种，同一品种只出一次
     *
     * @return 品种列表
     */
    private List<VarietyVO> holdingVarieties() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        HoldingQueryRequest holdingQuery = new HoldingQueryRequest();
        holdingQuery.setStatus(HoldingStatus.HOLDING);
        for (HoldingVO holding : holdingService.listHolding(holdingQuery)) {
            if (holding.getVarietyKey() != null && !holding.getVarietyKey().isBlank()) {
                keys.add(holding.getVarietyKey());
            }
        }
        if (keys.isEmpty()) {
            return List.of();
        }
        List<VarietyVO> varieties = new ArrayList<>();
        for (VarietyVO variety : varietyService.listVariety(new VarietyQueryRequest())) {
            if (keys.contains(variety.getVarietyKey())) {
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
     * 去掉多余尾零便于文案展示
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        BigDecimal trimmed = value.stripTrailingZeros();
        return trimmed.scale() < 0 ? trimmed.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
                : trimmed.toPlainString();
    }
}

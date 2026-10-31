package com.lilac.service.impl;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.domain.dto.bond.BondMonitorRequest;
import com.lilac.domain.dto.bond.BondParamSaveRequest;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.BondAdvisoryVO;
import com.lilac.domain.vo.BondMonitorVO;
import com.lilac.domain.vo.BondPlanVO;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.BondTrendSignal;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.service.BondMonitorService;
import com.lilac.service.FundNetWorthService;
import com.lilac.service.HoldingService;
import com.lilac.service.VarietyService;
import com.lilac.strategy.BondPlanResolver;
import com.lilac.strategy.BondTrendCalculator;
import com.lilac.strategy.model.BondPlan;
import com.lilac.strategy.model.BondTrendResult;
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

/**
 * 债券监控业务实现，取历史净值交给趋势计算器，并装配成带具体数值的提醒
 */
@Slf4j
@Service
public class BondMonitorServiceImpl implements BondMonitorService {

    private static final int NET_WORTH_LOOKBACK_DAYS = 400;
    private static final int MONEY_SCALE = 2;
    private static final String NO_HOLDING =
            "尚无持有中的债券基金，债券监控先录入持仓才会开始工作";

    @Resource
    private BondPlanResolver bondPlanResolver;

    @Resource
    private BondTrendCalculator bondTrendCalculator;

    @Resource
    private FundNetWorthService fundNetWorthService;

    @Resource
    private HoldingService holdingService;

    @Resource
    private VarietyService varietyService;

    /**
     * 读取债券监控参数
     *
     * @return 参数视图
     */
    @Override
    public BondPlanVO getPlan() {
        return toPlanVO(bondPlanResolver.resolve());
    }

    /**
     * 保存债券监控参数，窗口与横盘天数矛盾时拒绝保存
     *
     * @param request 参数保存请求
     * @return 保存后的参数视图
     */
    @Override
    public BondPlanVO savePlan(BondParamSaveRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR, "债券监控参数不能为空");
        BondPlan plan = new BondPlan();
        plan.setWindow(request.getWindow());
        plan.setSidewaysBand(request.getSidewaysBand());
        plan.setSidewaysDays(request.getSidewaysDays());
        plan.setSidewaysReturn(request.getSidewaysReturn());
        plan.setWeakSlope(request.getWeakSlope());
        plan.setWeakVolatility(request.getWeakVolatility());
        return toPlanVO(bondPlanResolver.save(plan));
    }

    /**
     * 对全部持有中的债券基金逐只判定，触发的与未触发的分组返回
     *
     * @return 监控总览
     */
    @Override
    public BondAdvisoryVO adviseAll() {
        BondPlan plan = bondPlanResolver.resolve();
        List<VarietyVO> varieties = bondVarieties();
        List<BondMonitorVO> items = new ArrayList<>();
        List<BondMonitorVO> idle = new ArrayList<>();
        int unavailable = 0;
        for (VarietyVO variety : varieties) {
            BondMonitorVO vo = monitor(variety, plan);
            if (vo.getSignal() == BondTrendSignal.UNAVAILABLE) {
                unavailable++;
                idle.add(vo);
            } else if (Boolean.TRUE.equals(vo.getAlerting())) {
                items.add(vo);
            } else {
                idle.add(vo);
            }
        }
        return assemble(plan, varieties.size(), items, idle, unavailable);
    }

    /**
     * 对指定品种判定，非债券类型直接拒绝
     *
     * @param request 试算请求
     * @return 单只监控结果
     */
    @Override
    public BondMonitorVO adviseOne(BondMonitorRequest request) {
        ThrowUtils.throwIf(request == null || request.getVarietyKey() == null
                || request.getVarietyKey().isBlank(), HttpsCodeEnum.PARAMS_ERROR, "品种业务主键不能为空");
        VarietyVO variety = findVariety(request.getVarietyKey().trim());
        ThrowUtils.throwIf(variety == null, HttpsCodeEnum.NOT_FOUND_ERROR,
                "未找到品种 " + request.getVarietyKey());
        ThrowUtils.throwIf(variety.getCategory() != FundCategory.BOND, HttpsCodeEnum.PARAMS_ERROR,
                "该品种类型为 " + label(variety) + "，债券监控只适用于债券型基金");
        return monitor(variety, bondPlanResolver.resolve());
    }

    /**
     * 单只基金出监控结果，净值序列与持有金额都在这里装配
     *
     * @param variety 品种
     * @param plan    监控参数
     * @return 监控结果
     */
    private BondMonitorVO monitor(VarietyVO variety, BondPlan plan) {
        BondMonitorVO vo = new BondMonitorVO();
        vo.setVarietyKey(variety.getVarietyKey());
        vo.setVarietyCode(variety.getVarietyCode());
        vo.setVarietyName(variety.getVarietyName());
        vo.setCategoryLabel(variety.getCategory() == null ? "待确认" : variety.getCategory().getLabel());
        List<FundNetWorthPoint> points = fundNetWorthService.listRange(variety.getVarietyCode(),
                LocalDate.now().minusDays(NET_WORTH_LOOKBACK_DAYS), null);
        BondTrendResult result = bondTrendCalculator.evaluate(points, plan);
        vo.setSignal(result.getSignal());
        vo.setSignalLabel(result.getSignal().getLabel());
        vo.setAlerting(result.getSignal().alerting());
        vo.setSidewaysDays(result.getSidewaysDays());
        vo.setSidewaysStart(result.getSidewaysStart());
        vo.setSidewaysEnd(result.getSidewaysEnd());
        vo.setSidewaysCalendarDays(
                bondTrendCalculator.naturalDaysBetween(result.getSidewaysStart(), result.getSidewaysEnd()));
        vo.setSidewaysReturn(result.getSidewaysReturn());
        vo.setWindow(result.getWindow());
        vo.setWindowStart(result.getWindowStart());
        vo.setWindowEnd(result.getWindowEnd());
        vo.setWindowReturn(result.getWindowReturn());
        vo.setSlope(result.getSlope());
        vo.setAnnualizedSlope(result.getAnnualizedSlope());
        vo.setVolatility(result.getVolatility());
        vo.setSamples(result.getSamples());
        vo.setLatestNetWorthDate(result.getWindowEnd());
        vo.setUnavailableReason(result.getUnavailableReason());
        vo.setRemarks(result.getRemarks());
        AmountSource amount = amountOf(variety);
        vo.setCurrentAmount(amount.amount);
        vo.setAmountSource(amount.source);
        if (!points.isEmpty() && points.get(points.size() - 1).getNetWorthDate() != null) {
            // 净值过期时提醒必须说清楚，否则使用者会拿半年前的结论做当天的决定
            vo.getRemarks().add("净值数据截止 " + points.get(points.size() - 1).getNetWorthDate()
                    + "，未更新时结论可能滞后");
        }
        return vo;
    }

    /**
     * 汇总成总览，逐条摘要供邮件直接取用
     *
     * @param plan        监控参数
     * @param requested   纳入的基金数
     * @param items       触发提醒的基金
     * @param idle        未触发的基金
     * @param unavailable 数据不足的数量
     * @return 监控总览
     */
    private BondAdvisoryVO assemble(BondPlan plan, int requested, List<BondMonitorVO> items,
                                    List<BondMonitorVO> idle, int unavailable) {
        BondAdvisoryVO vo = new BondAdvisoryVO();
        vo.setItems(items);
        vo.setIdleItems(idle);
        vo.setRequested(requested);
        vo.setAlertCount(items.size());
        vo.setIdleCount(idle.size() - unavailable);
        vo.setUnavailableCount(unavailable);
        vo.setParamUsable(plan.complete());
        vo.setParamIssues(plan.getIssues());
        vo.setSidewaysDaysThreshold(plan.getSidewaysDays());
        vo.setWeakSlopeThreshold(plan.getWeakSlope());
        vo.setSummary(summaryOf(items, idle));
        if (requested == 0) {
            vo.setNotice(NO_HOLDING);
        }
        log.info("债券监控完成 基金 {} 只 触发 {} 只 未触发 {} 只 数据不足 {} 只",
                requested, items.size(), idle.size() - unavailable, unavailable);
        return vo;
    }

    /**
     * 拼逐条摘要，触发项写清依据数值，未触发项只写状态与天数
     *
     * @param items 触发提醒的基金
     * @param idle  未触发的基金
     * @return 摘要列表
     */
    private List<String> summaryOf(List<BondMonitorVO> items, List<BondMonitorVO> idle) {
        List<String> summary = new ArrayList<>();
        for (BondMonitorVO item : items) {
            summary.add(item.getVarietyCode() + " " + item.getVarietyName() + "：" + item.getSignalLabel()
                    + "，考虑换仓。横盘 " + text(item.getSidewaysDays()) + " 个交易日，区间累计涨幅 "
                    + percent(item.getSidewaysReturn()) + "，年化斜率 " + percent(item.getAnnualizedSlope())
                    + "，日波动率 " + percent(item.getVolatility())
                    + (item.getSidewaysStart() == null ? "" : "（" + item.getSidewaysStart() + " 至 "
                    + item.getSidewaysEnd() + "）"));
        }
        for (BondMonitorVO item : idle) {
            if (item.getSignal() == BondTrendSignal.UNAVAILABLE) {
                summary.add(item.getVarietyCode() + " " + item.getVarietyName() + "：无法判断，"
                        + text(item.getUnavailableReason()));
                continue;
            }
            summary.add(item.getVarietyCode() + " " + item.getVarietyName() + "：" + item.getSignalLabel()
                    + "，横盘 " + text(item.getSidewaysDays()) + " 个交易日，年化斜率 "
                    + percent(item.getAnnualizedSlope()) + "，未达换仓阈值");
        }
        return summary;
    }

    /**
     * 取当前持有金额，按份额乘最新净值推算
     *
     * @param variety 品种
     * @return 金额与来源说明
     */
    private AmountSource amountOf(VarietyVO variety) {
        HoldingQueryRequest query = new HoldingQueryRequest();
        query.setVarietyKey(variety.getVarietyKey());
        query.setStatus(HoldingStatus.HOLDING);
        BigDecimal shares = null;
        for (HoldingVO holding : holdingService.listHolding(query)) {
            if (holding.getShares() == null) {
                continue;
            }
            shares = shares == null ? holding.getShares() : shares.add(holding.getShares());
        }
        if (shares == null || shares.signum() <= 0) {
            return new AmountSource(null, "库内无该品种的持有中持仓，当前金额取不到");
        }
        List<FundNetWorthPoint> points = fundNetWorthService.listRange(variety.getVarietyCode(),
                LocalDate.now().minusDays(NET_WORTH_LOOKBACK_DAYS), null);
        for (int i = points.size() - 1; i >= 0; i--) {
            FundNetWorthPoint point = points.get(i);
            if (point.getUnitNetWorth() == null) {
                continue;
            }
            BigDecimal amount = shares.multiply(point.getUnitNetWorth()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            return new AmountSource(amount, "按持有 " + plain(shares) + " 份 × 净值 "
                    + plain(point.getUnitNetWorth()) + " 推算，净值日期 " + point.getNetWorthDate());
        }
        return new AmountSource(null, "无历史净值，无法用份额推算当前金额");
    }

    /**
     * 取全部持有中的债券品种，同一品种只出一次
     *
     * @return 品种列表
     */
    private List<VarietyVO> bondVarieties() {
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
        VarietyQueryRequest varietyQuery = new VarietyQueryRequest();
        varietyQuery.setCategory(FundCategory.BOND);
        List<VarietyVO> varieties = new ArrayList<>();
        for (VarietyVO variety : varietyService.listVariety(varietyQuery)) {
            // 有持仓的债券基金才需要监控，未持仓的只影响备注
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
     * 参数转视图
     *
     * @param plan 参数
     * @return 参数视图
     */
    private BondPlanVO toPlanVO(BondPlan plan) {
        BondPlanVO vo = new BondPlanVO();
        vo.setWindow(plan.getWindow());
        vo.setSidewaysBand(plan.getSidewaysBand());
        vo.setSidewaysDays(plan.getSidewaysDays());
        vo.setSidewaysReturn(plan.getSidewaysReturn());
        vo.setWeakSlope(plan.getWeakSlope());
        vo.setWeakVolatility(plan.getWeakVolatility());
        vo.setUsable(plan.complete());
        vo.setIssues(plan.getIssues());
        return vo;
    }

    /**
     * 取基金类型中文名，未确认时给出提示
     *
     * @param variety 品种
     * @return 中文名
     */
    private String label(VarietyVO variety) {
        return variety.getCategory() == null ? "待确认" : variety.getCategory().getLabel();
    }

    /**
     * 空值替换为破折号，避免文案出现 null
     *
     * @param value 原值
     * @return 文本
     */
    private String text(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }

    /**
     * 百分比格式化，带符号并保留两位
     *
     * @param value 数值
     * @return 文本
     */
    private String percent(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        return (scaled.signum() > 0 ? "+" : "") + scaled.toPlainString() + "%";
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

    /**
     * 当前金额及其来源说明
     */
    private static class AmountSource {

        private final BigDecimal amount;

        private final String source;

        /**
         * 记录金额与来源
         *
         * @param amount 金额，取不到为 null
         * @param source 来源说明
         */
        private AmountSource(BigDecimal amount, String source) {
            this.amount = amount;
            this.source = source;
        }
    }
}

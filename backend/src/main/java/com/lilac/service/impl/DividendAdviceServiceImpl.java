package com.lilac.service.impl;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.domain.dto.dividend.DividendAdviceRequest;
import com.lilac.domain.dto.dividend.DividendParamSaveRequest;
import com.lilac.domain.dto.dividend.DividendQueryRequest;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.DividendAdviceVO;
import com.lilac.domain.vo.DividendAdvisoryVO;
import com.lilac.domain.vo.DividendPlanVO;
import com.lilac.domain.vo.DividendWatchVO;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.IndexDividendOverviewVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.DividendAction;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.service.DividendAdviceService;
import com.lilac.service.FundNetWorthService;
import com.lilac.service.HoldingService;
import com.lilac.service.IndexDividendService;
import com.lilac.service.VarietyService;
import com.lilac.strategy.DividendAdvisor;
import com.lilac.strategy.DividendPlanResolver;
import com.lilac.strategy.model.DividendDecision;
import com.lilac.strategy.model.DividendPlan;
import com.lilac.strategy.model.DividendSignal;
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
 * 红利加减仓建议业务实现，取股息率与持仓金额后交给规则计算器出结论
 */
@Slf4j
@Service
public class DividendAdviceServiceImpl implements DividendAdviceService {

    private static final int MONEY_SCALE = 2;
    private static final int NET_WORTH_LOOKBACK_DAYS = 30;
    private static final String GRID_NOTE =
            "同一标的的红利建议优先于网格提醒：红利建议给出加仓或减仓时，网格提醒不再重复给出反向动作";

    @Resource
    private DividendPlanResolver dividendPlanResolver;

    @Resource
    private DividendAdvisor dividendAdvisor;

    @Resource
    private IndexDividendService indexDividendService;

    @Resource
    private HoldingService holdingService;

    @Resource
    private VarietyService varietyService;

    @Resource
    private FundNetWorthService fundNetWorthService;

    /**
     * 读取当前红利参数，空库时返回缺项清单而不是静默给默认值
     *
     * @return 参数视图
     */
    @Override
    public DividendPlanVO getPlan() {
        return toPlanVO(dividendPlanResolver.resolve());
    }

    /**
     * 保存红利参数，保底高于基准或阈值顺序颠倒时拒绝保存
     *
     * @param request 参数保存请求
     * @return 保存后的参数视图
     */
    @Override
    public DividendPlanVO savePlan(DividendParamSaveRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR, "红利参数不能为空");
        DividendPlan plan = new DividendPlan();
        plan.setBaseAmount(request.getBaseAmount());
        plan.setFloorAmount(request.getFloorAmount());
        plan.setAddYield(request.getAddYield());
        plan.setReduceYield(request.getReduceYield());
        return toPlanVO(dividendPlanResolver.save(plan));
    }

    /**
     * 对全部持有中的红利持仓逐只给建议
     *
     * @return 建议总览
     */
    @Override
    public DividendAdvisoryVO adviseAll() {
        DividendPlan plan = dividendPlanResolver.resolve();
        List<VarietyVO> varieties = dividendVarieties();
        List<DividendAdviceVO> items = new ArrayList<>();
        List<DividendAdviceVO> skipped = new ArrayList<>();
        for (VarietyVO variety : varieties) {
            DividendAdviceVO advice = advise(variety, plan, null);
            if (advice.getAction() == DividendAction.UNDECIDABLE) {
                skipped.add(advice);
            } else {
                items.add(advice);
            }
        }
        return assemble(plan, varieties.size(), items, skipped);
    }

    /**
     * 对指定品种给建议，可手工指定当前金额以核对规则
     *
     * @param request 建议请求
     * @return 单只建议
     */
    @Override
    public DividendAdviceVO adviseOne(DividendAdviceRequest request) {
        ThrowUtils.throwIf(request == null || request.getVarietyKey() == null
                || request.getVarietyKey().isBlank(), HttpsCodeEnum.PARAMS_ERROR, "品种业务主键不能为空");
        VarietyVO variety = findVariety(request.getVarietyKey().trim());
        ThrowUtils.throwIf(variety == null, HttpsCodeEnum.NOT_FOUND_ERROR,
                "未找到品种 " + request.getVarietyKey());
        ThrowUtils.throwIf(variety.getCategory() != FundCategory.DIVIDEND, HttpsCodeEnum.PARAMS_ERROR,
                "该品种类型为 " + label(variety) + "，红利建议只适用于红利基金");
        ThrowUtils.throwIf(request.getCurrentAmount() != null && request.getCurrentAmount().signum() < 0,
                HttpsCodeEnum.PARAMS_ERROR, "当前持有金额不能为负数");
        return advise(variety, dividendPlanResolver.resolve(), request.getCurrentAmount());
    }

    /**
     * 列出红利策略的监控对象，说明这套参数管的是哪几只基金与它们的股息率状态
     *
     * @return 监控对象列表
     */
    @Override
    public List<DividendWatchVO> watchList() {
        List<VarietyVO> varieties = dividendVarieties();
        List<DividendWatchVO> result = new ArrayList<>(varieties.size());
        for (VarietyVO variety : varieties) {
            DividendWatchVO vo = new DividendWatchVO();
            vo.setVarietyKey(variety.getVarietyKey());
            vo.setVarietyCode(variety.getVarietyCode());
            vo.setVarietyName(variety.getVarietyName());
            vo.setIndexCode(variety.getTrackIndexCode());
            List<String> reasons = new ArrayList<>();
            boolean hasIndex = variety.getTrackIndexCode() != null && !variety.getTrackIndexCode().isBlank();
            if (!hasIndex) {
                reasons.add("未登记跟踪指数，取不到股息率，无法给加减仓建议");
            }
            DividendSignal signal = hasIndex ? signalOf(variety) : null;
            if (hasIndex && signal == null) {
                reasons.add("该指数尚无股息率数据，请先刷新股息率或手工录入");
            } else if (signal != null) {
                vo.setDividendYield(signal.getDividendYield());
                vo.setDataDate(signal.getDataDate());
                vo.setAgeDays(signal.getAgeDays() == null ? null : signal.getAgeDays().intValue());
                vo.setStale(signal.getStale());
                if (Boolean.TRUE.equals(signal.getStale())) {
                    reasons.add(signal.getStaleReason());
                }
            }
            vo.setIndexName(indexNameOf(variety.getTrackIndexCode()));
            AmountSource amount = amountOf(variety, null);
            vo.setCurrentAmount(amount.amount);
            if (amount.amount == null) {
                reasons.add(amount.source);
            }
            vo.setUsable(reasons.isEmpty());
            vo.setReasons(reasons);
            result.add(vo);
        }
        return result;
    }

    /**
     * 单只品种出建议，金额来源与股息率新鲜度都在这里装配
     *
     * @param variety       品种
     * @param plan          红利参数
     * @param manualAmount  手工指定的当前金额，为空时按持仓与净值推算
     * @return 建议
     */
    private DividendAdviceVO advise(VarietyVO variety, DividendPlan plan, BigDecimal manualAmount) {
        DividendAdviceVO vo = new DividendAdviceVO();
        vo.setVarietyKey(variety.getVarietyKey());
        vo.setVarietyCode(variety.getVarietyCode());
        vo.setVarietyName(variety.getVarietyName());
        vo.setIndexCode(variety.getTrackIndexCode());
        vo.setBaseAmount(plan.getBaseAmount());
        vo.setFloorAmount(plan.getFloorAmount());
        vo.setGridNote(GRID_NOTE);
        DividendSignal signal = signalOf(variety);
        fillSignal(vo, signal);
        AmountSource amount = amountOf(variety, manualAmount);
        vo.setAmountSource(amount.source);
        vo.setCurrentAmount(amount.amount);
        DividendDecision decision = dividendAdvisor.decide(plan, signal, amount.amount);
        vo.setAction(decision.getAction());
        vo.setActionLabel(decision.getAction().getLabel());
        vo.setSuggestAmount(decision.getSuggestAmount());
        vo.setTargetAmount(decision.getTargetAmount());
        vo.setRemainingAmount(decision.getRemainingAmount());
        vo.setFloorProtected(decision.getFloorProtected());
        vo.setGridSuppressed(decision.getGridSuppressed());
        vo.setRemarks(decision.getRemarks());
        return vo;
    }

    /**
     * 取该品种跟踪指数的股息率，未登记跟踪指数时返回 null 由规则层说明
     *
     * @param variety 品种
     * @return 股息率信号，取不到返回 null
     */
    private DividendSignal signalOf(VarietyVO variety) {
        if (variety.getTrackIndexCode() == null || variety.getTrackIndexCode().isBlank()) {
            return null;
        }
        DividendQueryRequest query = new DividendQueryRequest();
        query.setIndexCode(variety.getTrackIndexCode());
        List<IndexDividendOverviewVO> rows = indexDividendService.listOverview(query);
        if (rows.isEmpty()) {
            return null;
        }
        IndexDividendOverviewVO latest = rows.get(0);
        DividendSignal signal = new DividendSignal();
        signal.setDividendYield(latest.getDividendYield());
        signal.setDataDate(latest.getDataDate());
        signal.setAgeDays(latest.getAgeDays());
        signal.setStale(latest.getStale());
        signal.setStaleReason(latest.getStaleReason());
        return signal;
    }

    /**
     * 把股息率信号写进建议，数据日期必须落到出参上供页面与邮件显示
     *
     * @param vo     建议
     * @param signal 股息率信号
     */
    private void fillSignal(DividendAdviceVO vo, DividendSignal signal) {
        if (signal == null) {
            return;
        }
        vo.setDividendYield(signal.getDividendYield());
        vo.setDataDate(signal.getDataDate());
        vo.setAgeDays(signal.getAgeDays());
        vo.setStale(signal.getStale());
    }

    /**
     * 取跟踪指数的中文全称，供页面显示「这套参数管的是哪只指数」
     *
     * @param indexCode 指数代码，可为空
     * @return 指数全称，取不到返回 null
     */
    private String indexNameOf(String indexCode) {
        if (indexCode == null || indexCode.isBlank()) {
            return null;
        }
        DividendQueryRequest query = new DividendQueryRequest();
        query.setIndexCode(indexCode);
        List<IndexDividendOverviewVO> rows = indexDividendService.listOverview(query);
        return rows.isEmpty() ? null : rows.get(0).getIndexName();
    }

    /**
     * 取当前持有金额，手工值优先，否则按持有份额乘最新净值
     *
     * @param variety      品种
     * @param manualAmount 手工指定金额
     * @return 金额与来源说明
     */
    private AmountSource amountOf(VarietyVO variety, BigDecimal manualAmount) {
        if (manualAmount != null) {
            return new AmountSource(manualAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP), "手工指定当前持有金额");
        }
        BigDecimal shares = sharesOf(variety.getVarietyKey());
        if (shares == null || shares.signum() <= 0) {
            return new AmountSource(null, "库内无该品种的持有中持仓，当前金额取不到");
        }
        BigDecimal netWorth = latestNetWorth(variety.getVarietyCode());
        if (netWorth == null) {
            return new AmountSource(null, "无历史净值，无法用份额推算当前金额");
        }
        BigDecimal amount = shares.multiply(netWorth).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new AmountSource(amount, "按持有 " + plain(shares) + " 份 × 最新净值 " + plain(netWorth)
                + " 推算，净值日期 " + latestNetWorthDate(variety.getVarietyCode()));
    }

    /**
     * 汇总持有中份额，同一品种多次建仓合并计算
     *
     * @param varietyKey 品种业务主键
     * @return 合计份额，无持仓返回 null
     */
    private BigDecimal sharesOf(String varietyKey) {
        HoldingQueryRequest query = new HoldingQueryRequest();
        query.setVarietyKey(varietyKey);
        query.setStatus(HoldingStatus.HOLDING);
        BigDecimal shares = null;
        for (HoldingVO holding : holdingService.listHolding(query)) {
            if (holding.getShares() == null) {
                continue;
            }
            shares = shares == null ? holding.getShares() : shares.add(holding.getShares());
        }
        return shares;
    }

    /**
     * 取库内最新单位净值
     *
     * @param fundCode 6 位基金代码
     * @return 最新净值，无数据返回 null
     */
    private BigDecimal latestNetWorth(String fundCode) {
        List<FundNetWorthPoint> points = fundNetWorthService.listRange(fundCode,
                LocalDate.now().minusDays(NET_WORTH_LOOKBACK_DAYS), null);
        for (int i = points.size() - 1; i >= 0; i--) {
            if (points.get(i).getUnitNetWorth() != null) {
                return points.get(i).getUnitNetWorth();
            }
        }
        return null;
    }

    /**
     * 取库内最新净值日期，供金额来源说明
     *
     * @param fundCode 6 位基金代码
     * @return 净值日期，无数据返回 null
     */
    private LocalDate latestNetWorthDate(String fundCode) {
        return fundNetWorthService.latestDate(fundCode);
    }

    /**
     * 取全部持有中的红利品种，同一品种只出一次
     *
     * @return 品种列表
     */
    private List<VarietyVO> dividendVarieties() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        HoldingQueryRequest holdingQuery = new HoldingQueryRequest();
        holdingQuery.setStatus(HoldingStatus.HOLDING);
        for (HoldingVO holding : holdingService.listHolding(holdingQuery)) {
            if (holding.getVarietyKey() != null && !holding.getVarietyKey().isBlank()) {
                keys.add(holding.getVarietyKey());
            }
        }
        VarietyQueryRequest varietyQuery = new VarietyQueryRequest();
        varietyQuery.setCategory(FundCategory.DIVIDEND);
        List<VarietyVO> varieties = new ArrayList<>();
        for (VarietyVO variety : varietyService.listVariety(varietyQuery)) {
            // 有持仓的红利基金才需要给加减仓建议，未持仓的只影响股息率刷新目标
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
     * 汇总成总览，金额合计只算给出动作的那部分
     *
     * @param plan      红利参数
     * @param requested 纳入的品种数
     * @param items     有动作的建议
     * @param skipped   不给动作的建议
     * @return 总览
     */
    private DividendAdvisoryVO assemble(DividendPlan plan, int requested, List<DividendAdviceVO> items,
                                        List<DividendAdviceVO> skipped) {
        items.sort((left, right) -> compareAmount(right.getSuggestAmount(), left.getSuggestAmount()));
        DividendAdvisoryVO vo = new DividendAdvisoryVO();
        vo.setItems(items);
        vo.setSkippedItems(skipped);
        vo.setRequested(requested);
        vo.setParamUsable(plan.complete());
        vo.setParamIssues(plan.getIssues());
        vo.setBaseAmount(plan.getBaseAmount());
        vo.setFloorAmount(plan.getFloorAmount());
        vo.setAddYield(plan.getAddYield());
        vo.setReduceYield(plan.getReduceYield());
        vo.setGridNote(GRID_NOTE);
        fillTotals(vo, items, skipped);
        fillFreshness(vo, items, skipped);
        fillSummary(vo, items, skipped);
        if (requested == 0) {
            vo.setNotice("尚无持有中的红利基金，请先在品种里把红利的跟踪指数确认好，并录入持仓");
        }
        log.info("红利建议完成 品种 {} 只 给出动作 {} 只 不给动作 {} 只",
                requested, vo.getActionCount(), vo.getUndecidableCount());
        return vo;
    }

    /**
     * 计算买卖合计与当前持仓合计
     *
     * @param vo      总览
     * @param items   有动作的建议
     * @param skipped 不给动作的建议
     */
    private void fillTotals(DividendAdvisoryVO vo, List<DividendAdviceVO> items, List<DividendAdviceVO> skipped) {
        BigDecimal addSum = BigDecimal.ZERO;
        BigDecimal reduceSum = BigDecimal.ZERO;
        BigDecimal currentSum = BigDecimal.ZERO;
        int actions = 0;
        int holds = 0;
        for (DividendAdviceVO item : concat(items, skipped)) {
            if (item.getCurrentAmount() != null) {
                currentSum = currentSum.add(item.getCurrentAmount());
            }
            if (item.getAction() == DividendAction.ADD) {
                addSum = addSum.add(item.getSuggestAmount());
                actions++;
            } else if (item.getAction() == DividendAction.REDUCE) {
                reduceSum = reduceSum.add(item.getSuggestAmount());
                actions++;
            } else if (item.getAction() == DividendAction.HOLD) {
                holds++;
            }
        }
        vo.setTotalAddAmount(addSum);
        vo.setTotalReduceAmount(reduceSum);
        vo.setTotalCurrentAmount(currentSum);
        vo.setActionCount(actions);
        vo.setHoldCount(holds);
        vo.setUndecidableCount(skipped.size());
    }

    /**
     * 取全部建议的最旧数据日期与过期标记
     *
     * @param vo      总览
     * @param items   有动作的建议
     * @param skipped 不给动作的建议
     */
    private void fillFreshness(DividendAdvisoryVO vo, List<DividendAdviceVO> items,
                               List<DividendAdviceVO> skipped) {
        LocalDate oldest = null;
        Long age = null;
        boolean stale = false;
        for (DividendAdviceVO item : concat(items, skipped)) {
            if (item.getDataDate() != null && (oldest == null || item.getDataDate().isBefore(oldest))) {
                oldest = item.getDataDate();
            }
            if (item.getAgeDays() != null && (age == null || item.getAgeDays() > age)) {
                age = item.getAgeDays();
            }
            stale = stale || Boolean.TRUE.equals(item.getStale());
        }
        vo.setDataDate(oldest);
        vo.setAgeDays(age);
        vo.setHasStale(stale);
    }

    /**
     * 拼装逐条汇总文字，供页面与邮件直接取用
     *
     * @param vo      总览
     * @param items   有动作的建议
     * @param skipped 不给动作的建议
     */
    private void fillSummary(DividendAdvisoryVO vo, List<DividendAdviceVO> items,
                             List<DividendAdviceVO> skipped) {
        List<String> summary = new ArrayList<>();
        for (DividendAdviceVO item : items) {
            summary.add(item.getVarietyCode() + " " + item.getVarietyName() + "：" + item.getActionLabel()
                    + (item.getSuggestAmount() == null ? "" : " " + plain(item.getSuggestAmount()) + " 元")
                    + (item.getStale() == null ? "" : "（数据日期 " + item.getDataDate() + "）"));
        }
        for (DividendAdviceVO item : skipped) {
            summary.add(item.getVarietyCode() + " " + item.getVarietyName() + "：不给动作，"
                    + (item.getRemarks().isEmpty() ? "原因见明细" : item.getRemarks().get(item.getRemarks().size() - 1)));
        }
        vo.setSummary(summary);
    }

    /**
     * 参数转视图
     *
     * @param plan 参数
     * @return 参数视图
     */
    private DividendPlanVO toPlanVO(DividendPlan plan) {
        DividendPlanVO vo = new DividendPlanVO();
        vo.setBaseAmount(plan.getBaseAmount());
        vo.setFloorAmount(plan.getFloorAmount());
        vo.setAddYield(plan.getAddYield());
        vo.setReduceYield(plan.getReduceYield());
        vo.setUsable(plan.complete());
        vo.setIssues(plan.getIssues());
        return vo;
    }

    /**
     * 判断两笔金额的排序，空值排最后
     *
     * @param left  左值
     * @param right 右值
     * @return 比较结果
     */
    private int compareAmount(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    /**
     * 合并两组建议，便于统一统计
     *
     * @param items   有动作的建议
     * @param skipped 不给动作的建议
     * @return 合并结果
     */
    private List<DividendAdviceVO> concat(List<DividendAdviceVO> items, List<DividendAdviceVO> skipped) {
        List<DividendAdviceVO> all = new ArrayList<>(items.size() + skipped.size());
        all.addAll(items);
        all.addAll(skipped);
        return all;
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

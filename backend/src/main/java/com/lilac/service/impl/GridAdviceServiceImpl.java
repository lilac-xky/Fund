package com.lilac.service.impl;

import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.domain.dto.dividend.DividendAdviceRequest;
import com.lilac.domain.dto.grid.GridAdviceRequest;
import com.lilac.domain.dto.grid.GridParamSaveRequest;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.DividendAdviceVO;
import com.lilac.domain.vo.GridAdviceVO;
import com.lilac.domain.vo.GridAdvisoryVO;
import com.lilac.domain.vo.GridPlanVO;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.DividendAction;
import com.lilac.enums.FundCategory;
import com.lilac.enums.GridAction;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.VarietyType;
import com.lilac.service.DividendAdviceService;
import com.lilac.service.GridAdviceService;
import com.lilac.service.HoldingService;
import com.lilac.service.VarietyService;
import com.lilac.strategy.GridCalculator;
import com.lilac.strategy.GridPlanResolver;
import com.lilac.strategy.model.GridDecision;
import com.lilac.strategy.model.GridPlan;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 网格到价提醒业务实现，取行情与红利建议后交给规则计算器判定是否穿越网格线
 */
@Slf4j
@Service
public class GridAdviceServiceImpl implements GridAdviceService {

    private static final int PRICE_SCALE = 4;
    private static final String DIVIDEND_NOTE =
            "同一标的的红利建议优先于网格提醒：红利侧给出加仓或减仓时，网格提醒让位，不重复给出反向动作";

    @Resource
    private GridPlanResolver gridPlanResolver;

    @Resource
    private GridCalculator gridCalculator;

    @Resource
    private MarketQuoteAdapter marketQuoteAdapter;

    @Resource
    private HoldingService holdingService;

    @Resource
    private VarietyService varietyService;

    @Resource
    private DividendAdviceService dividendAdviceService;

    /**
     * 读取当前网格参数，连带给出划格结果
     *
     * @return 参数视图
     */
    @Override
    public GridPlanVO getPlan() {
        return toPlanVO(gridPlanResolver.resolve());
    }

    /**
     * 保存网格参数，上下界颠倒或划不出两格时拒绝
     *
     * @param request 保存请求
     * @return 保存后的参数视图
     */
    @Override
    public GridPlanVO savePlan(GridParamSaveRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR, "网格参数不能为空");
        GridPlan plan = new GridPlan();
        plan.setLowerBound(request.getLowerBound());
        plan.setUpperBound(request.getUpperBound());
        plan.setStep(request.getStep());
        plan.setAmountPerGrid(request.getAmountPerGrid());
        return toPlanVO(gridPlanResolver.save(plan));
    }

    /**
     * 对全部持有中的场内 ETF 逐只判断网格触发
     *
     * @return 提醒总览
     */
    @Override
    public GridAdvisoryVO adviseAll() {
        GridPlan plan = gridPlanResolver.resolve();
        List<VarietyVO> varieties = gridVarieties();
        List<GridAdviceVO> items = new ArrayList<>();
        List<GridAdviceVO> idle = new ArrayList<>();
        for (VarietyVO variety : varieties) {
            GridAdviceVO advice = advise(variety, plan, null, null);
            if (advice.getAction() == GridAction.BUY || advice.getAction() == GridAction.SELL) {
                items.add(advice);
            } else {
                idle.add(advice);
            }
        }
        return assemble(plan, varieties.size(), items, idle);
    }

    /**
     * 对指定标的试算，可手工指定当前价与参考价以核对规则
     *
     * @param request 试算请求
     * @return 单只提醒
     */
    @Override
    public GridAdviceVO adviseOne(GridAdviceRequest request) {
        ThrowUtils.throwIf(request == null || request.getVarietyKey() == null
                || request.getVarietyKey().isBlank(), HttpsCodeEnum.PARAMS_ERROR, "品种业务主键不能为空");
        VarietyVO variety = findVariety(request.getVarietyKey().trim());
        ThrowUtils.throwIf(variety == null, HttpsCodeEnum.NOT_FOUND_ERROR,
                "未找到品种 " + request.getVarietyKey());
        ThrowUtils.throwIf(variety.getVarietyType() != VarietyType.ETF, HttpsCodeEnum.PARAMS_ERROR,
                "该品种为 " + variety.getVarietyType() + "，网格提醒只适用于场内 ETF");
        ThrowUtils.throwIf(request.getCurrentPrice() != null && request.getCurrentPrice().signum() <= 0,
                HttpsCodeEnum.PARAMS_ERROR, "当前价必须大于 0");
        ThrowUtils.throwIf(request.getReferencePrice() != null && request.getReferencePrice().signum() <= 0,
                HttpsCodeEnum.PARAMS_ERROR, "参考价必须大于 0");
        return advise(variety, gridPlanResolver.resolve(), request.getCurrentPrice(), request.getReferencePrice());
    }

    /**
     * 列出当前参数下的网格线
     *
     * @return 网格线价格，按升序
     */
    @Override
    public List<BigDecimal> lines() {
        return gridCalculator.lines(gridPlanResolver.resolve());
    }

    /**
     * 单只标的出提醒，行情取数与红利互斥都在这里装配
     *
     * @param variety        品种
     * @param plan           网格参数
     * @param manualPrice    手工当前价，为空时取行情
     * @param manualReference 手工参考价，为空时取上一交易日收盘
     * @return 提醒
     */
    private GridAdviceVO advise(VarietyVO variety, GridPlan plan, BigDecimal manualPrice,
                                BigDecimal manualReference) {
        GridAdviceVO vo = new GridAdviceVO();
        vo.setVarietyKey(variety.getVarietyKey());
        vo.setVarietyCode(variety.getVarietyCode());
        vo.setVarietyName(variety.getVarietyName());
        vo.setCategoryLabel(variety.getCategory() == null ? "待确认" : variety.getCategory().getLabel());
        vo.setLowerBound(plan.getLowerBound());
        vo.setUpperBound(plan.getUpperBound());
        vo.setAmountPerGrid(plan.getAmountPerGrid());
        Price reference = priceOf(variety, manualPrice, manualReference);
        vo.setCurrentPrice(reference.current);
        vo.setPriceDate(reference.date);
        vo.setPriceSource(reference.source);
        vo.setReferencePrice(reference.previous);
        vo.setReferenceDate(reference.previousDate);
        GridDecision decision = gridCalculator.decide(plan, reference.current, reference.previous,
                dividendActionOf(variety));
        vo.setAction(decision.getAction());
        vo.setActionLabel(decision.getAction().getLabel());
        vo.setCurrentGrid(decision.getCurrentGrid());
        vo.setCrossedLine(decision.getCrossedLine());
        vo.setSuggestAmount(decision.getSuggestAmount());
        vo.setLineCount(decision.getLineCount());
        vo.setSuppressed(decision.getSuppressed());
        vo.setRemarks(decision.getRemarks());
        return vo;
    }

    /**
     * 取该标的的红利动作，非红利品种返回 null；取不到时按无建议处理，不阻断网格提醒
     *
     * @param variety 品种
     * @return 红利动作，非红利或取不到返回 null
     */
    private DividendAction dividendActionOf(VarietyVO variety) {
        if (variety.getCategory() != FundCategory.DIVIDEND) {
            return null;
        }
        try {
            DividendAdviceRequest request = new DividendAdviceRequest();
            request.setVarietyKey(variety.getVarietyKey());
            DividendAdviceVO advice = dividendAdviceService.adviseOne(request);
            return advice == null ? null : advice.getAction();
        } catch (RuntimeException e) {
            // 红利侧取数失败只是失去互斥依据，网格仍可独立判断
            log.warn("红利建议取数失败 varietyKey={}：{}", variety.getVarietyKey(), e.getMessage());
            return null;
        }
    }

    /**
     * 取当前价与参考价，手工值优先，否则取行情的最新价与昨收
     *
     * @param variety         品种
     * @param manualPrice     手工当前价
     * @param manualReference 手工参考价
     * @return 价格组合
     */
    private Price priceOf(VarietyVO variety, BigDecimal manualPrice, BigDecimal manualReference) {
        if (manualPrice != null) {
            return new Price(manualPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP), null,
                    "手工指定当前价", manualReference, null);
        }
        if (variety.getVarietyCode() == null) {
            return new Price(null, null, "无代码，取不到行情", manualReference, null);
        }
        try {
            MarketQuote quote = marketQuoteAdapter.quote(variety.getVarietyCode());
            if (quote == null || quote.getPrice() == null) {
                return new Price(null, null, "行情取不到", manualReference, null);
            }
            String source = Boolean.TRUE.equals(quote.getRealtime()) ? "行情价（实时）" : "行情价（上一交易日）";
            return new Price(quote.getPrice(), quote.getTradeDate(), source,
                    manualReference == null ? quote.getPreviousClose() : manualReference, quote.getTradeDate());
        } catch (RuntimeException e) {
            log.warn("网格行情取数失败 code={}：{}", variety.getVarietyCode(), e.getMessage());
            return new Price(null, null, "行情取数失败：" + e.getMessage(), manualReference, null);
        }
    }

    /**
     * 取全部持有中的场内 ETF，网格只对场内可交易标的有意义
     *
     * @return 品种列表
     */
    private List<VarietyVO> gridVarieties() {
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
        List<VarietyVO> varieties = new ArrayList<>();
        for (VarietyVO variety : varietyService.listVariety(varietyQuery)) {
            // 场外基金按净值成交，没有盘中价格可以划格
            if (keys.contains(variety.getVarietyKey()) && variety.getVarietyType() == VarietyType.ETF) {
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
     * 汇总成总览，买卖合计只算给出动作的那部分
     *
     * @param plan      网格参数
     * @param requested 纳入的标的数
     * @param items     触发的提醒
     * @param idle      未触发的提醒
     * @return 总览
     */
    private GridAdvisoryVO assemble(GridPlan plan, int requested, List<GridAdviceVO> items,
                                    List<GridAdviceVO> idle) {
        items.sort((left, right) -> compareAmount(right.getSuggestAmount(), left.getSuggestAmount()));
        GridAdvisoryVO vo = new GridAdvisoryVO();
        vo.setItems(items);
        vo.setIdleItems(idle);
        vo.setRequested(requested);
        vo.setParamUsable(plan.complete());
        vo.setParamIssues(plan.getIssues());
        vo.setDividendNote(DIVIDEND_NOTE);
        fillTotals(vo, items, idle);
        fillSummary(vo, items, idle);
        if (requested == 0) {
            vo.setNotice("尚无持有中的场内 ETF，网格提醒只对场内可交易标的有效");
        }
        log.info("网格提醒完成 标的 {} 只 触发 {} 只 让位 {} 只 未触发 {} 只",
                requested, vo.getTriggeredCount(), vo.getSuppressedCount(), vo.getIdleCount());
        return vo;
    }

    /**
     * 计算买卖合计与各状态计数
     *
     * @param vo    总览
     * @param items 触发的提醒
     * @param idle  未触发的提醒
     */
    private void fillTotals(GridAdvisoryVO vo, List<GridAdviceVO> items, List<GridAdviceVO> idle) {
        BigDecimal buySum = BigDecimal.ZERO;
        BigDecimal sellSum = BigDecimal.ZERO;
        int suppressed = 0;
        for (GridAdviceVO item : idle) {
            if (item.getAction() == GridAction.SUPPRESSED) {
                suppressed++;
            }
        }
        for (GridAdviceVO item : items) {
            if (item.getAction() == GridAction.BUY) {
                buySum = buySum.add(item.getSuggestAmount());
            } else if (item.getAction() == GridAction.SELL) {
                sellSum = sellSum.add(item.getSuggestAmount());
            }
        }
        vo.setTotalBuyAmount(buySum);
        vo.setTotalSellAmount(sellSum);
        vo.setTriggeredCount(items.size());
        vo.setSuppressedCount(suppressed);
        vo.setIdleCount(idle.size());
    }

    /**
     * 拼装逐条摘要，供邮件直接取用
     *
     * @param vo    总览
     * @param items 触发的提醒
     * @param idle  未触发的提醒
     */
    private void fillSummary(GridAdvisoryVO vo, List<GridAdviceVO> items, List<GridAdviceVO> idle) {
        List<String> summary = new ArrayList<>();
        for (GridAdviceVO item : items) {
            summary.add(item.getVarietyCode() + " " + item.getVarietyName() + "："
                    + item.getActionLabel() + " " + plain(item.getSuggestAmount()) + " 元，"
                    + "触发网格线 " + plain(item.getCrossedLine()) + " 元（当前价 "
                    + plain(item.getCurrentPrice()) + " 元）");
        }
        for (GridAdviceVO item : idle) {
            if (item.getAction() == GridAction.SUPPRESSED) {
                summary.add(item.getVarietyCode() + " " + item.getVarietyName()
                        + "：网格让位于红利建议，本次不重复给动作");
            }
        }
        vo.setSummary(summary);
    }

    /**
     * 参数转视图，可用时连带给出网格线
     *
     * @param plan 参数
     * @return 参数视图
     */
    private GridPlanVO toPlanVO(GridPlan plan) {
        GridPlanVO vo = new GridPlanVO();
        vo.setLowerBound(plan.getLowerBound());
        vo.setUpperBound(plan.getUpperBound());
        vo.setStep(plan.getStep());
        vo.setAmountPerGrid(plan.getAmountPerGrid());
        vo.setUsable(plan.complete());
        vo.setIssues(plan.getIssues());
        vo.setLines(plan.complete() ? gridCalculator.lines(plan) : List.of());
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
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
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
     * 当前价、参考价与来源说明
     */
    private static class Price {

        private final BigDecimal current;

        private final java.time.LocalDate date;

        private final String source;

        private final BigDecimal previous;

        private final java.time.LocalDate previousDate;

        /**
         * 记录价格与来源
         *
         * @param current      当前价
         * @param date         当前价日期
         * @param source       来源说明
         * @param previous     参考价
         * @param previousDate 参考价日期
         */
        private Price(BigDecimal current, java.time.LocalDate date, String source,
                      BigDecimal previous, java.time.LocalDate previousDate) {
            this.current = current;
            this.date = date;
            this.source = source;
            this.previous = previous;
            this.previousDate = previousDate;
        }
    }
}

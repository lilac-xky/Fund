package com.lilac.service.impl;

import com.lilac.adapter.model.MarketBar;
import com.lilac.adapter.model.MarketHistory;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.domain.dto.bond.BondMonitorRequest;
import com.lilac.domain.dto.holding.HoldingChangeQueryRequest;
import com.lilac.domain.dto.lesson.LessonQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.BondMonitorVO;
import com.lilac.domain.vo.HoldingChangeVO;
import com.lilac.domain.vo.LessonAdvisoryVO;
import com.lilac.domain.vo.LessonAlertVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingChangeType;
import com.lilac.enums.LessonPattern;
import com.lilac.enums.VarietyType;
import com.lilac.service.BondMonitorService;
import com.lilac.service.HoldingService;
import com.lilac.service.LessonAdviceService;
import com.lilac.service.VarietyService;
import com.lilac.strategy.LessonCalculator;
import com.lilac.strategy.LessonPlanResolver;
import com.lilac.strategy.model.LessonAlert;
import com.lilac.strategy.model.LessonPlan;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史教训警示业务实现，取历史变动与日线交给规则计算器，再装配成带教训文案的警示
 */
@Slf4j
@Service
public class LessonAdviceServiceImpl implements LessonAdviceService {

    private static final int DEFAULT_DAYS = 180;
    private static final int HISTORY_BARS = 120;

    @Resource
    private LessonPlanResolver lessonPlanResolver;

    @Resource
    private LessonCalculator lessonCalculator;

    @Resource
    private HoldingService holdingService;

    @Resource
    private VarietyService varietyService;

    @Resource
    private MarketQuoteAdapter marketQuoteAdapter;

    @Resource
    private BondMonitorService bondMonitorService;

    /**
     * 检查历史变动并汇总警示
     *
     * @param request 查询请求
     * @return 警示总览
     */
    @Override
    public LessonAdvisoryVO advise(LessonQueryRequest request) {
        LessonPlan plan = lessonPlanResolver.resolve();
        LessonQueryRequest query = request == null ? new LessonQueryRequest() : request;
        List<HoldingChangeVO> changes = changesOf(query);
        List<LessonAlertVO> items = new ArrayList<>();
        if (plan.complete()) {
            for (HoldingChangeVO change : changes) {
                items.addAll(checkOne(change, changes, plan));
            }
        }
        return assemble(plan, changes.size(), items);
    }

    /**
     * 对一笔变动跑三类判定，单笔取数失败只跳过该笔
     *
     * @param change  变动记录
     * @param all     全部变动，供换仓配对
     * @param plan    警示参数
     * @return 命中的警示
     */
    private List<LessonAlertVO> checkOne(HoldingChangeVO change, List<HoldingChangeVO> all, LessonPlan plan) {
        List<LessonAlertVO> found = new ArrayList<>();
        if (change.getVarietyKey() == null) {
            return found;
        }
        VarietyVO variety = findVariety(change.getVarietyKey());
        if (variety == null) {
            return found;
        }
        List<MarketBar> bars = barsOf(variety);
        if (change.getChangeType() == HoldingChangeType.BUY) {
            addIfHit(found, lessonCalculator.chaseHigh(bars, change, plan), variety);
            addIfHit(found, blindSwitchOf(change, all, plan), findVariety(change.getVarietyKey()));
            return found;
        }
        if (change.getChangeType() == HoldingChangeType.SELL) {
            BigDecimal costPrice = change.getUnitPrice();
            addIfHit(found, lessonCalculator.panicSell(bars, change, costPrice, plan), variety);
        }
        return found;
    }

    /**
     * 判定这次买入是否属于盲目换仓：同日有卖出记录且转出的债券已达横盘阈值
     *
     * @param buy  买入记录
     * @param all  全部变动
     * @param plan 警示参数
     * @return 命中的警示，未命中返回 null
     */
    private LessonAlert blindSwitchOf(HoldingChangeVO buy, List<HoldingChangeVO> all, LessonPlan plan) {
        HoldingChangeVO sell = sameDaySell(buy, all);
        if (sell == null) {
            return null;
        }
        VarietyVO sold = findVariety(sell.getVarietyKey());
        if (sold == null || sold.getCategory() != FundCategory.BOND) {
            // 只有「从债券换出」才对应使用者那次教训，从别的类型换出不算
            return null;
        }
        if (!bondSideways(sold)) {
            return null;
        }
        VarietyVO bought = findVariety(buy.getVarietyKey());
        Long familiarDays = bought == null || bought.getCreateTime() == null ? null
                : lessonCalculator.daysBetween(bought.getCreateTime().toLocalDate(), LocalDate.now());
        return lessonCalculator.blindSwitch(sell, buy.getVarietyKey(), true, familiarDays, plan);
    }

    /**
     * 找出与买入同日的卖出记录，供换仓配对
     *
     * @param buy 买入记录
     * @param all 全部变动
     * @return 同日卖出，没有返回 null
     */
    private HoldingChangeVO sameDaySell(HoldingChangeVO buy, List<HoldingChangeVO> all) {
        for (HoldingChangeVO other : all) {
            if (other.getChangeType() == HoldingChangeType.SELL
                    && other.getOpenDate() != null && other.getOpenDate().equals(buy.getOpenDate())
                    && !other.getVarietyKey().equals(buy.getVarietyKey())) {
                return other;
            }
        }
        return null;
    }

    /**
     * 判断该债券基金是否已达横盘阈值，取不到结论时按未达处理
     *
     * @param variety 债券品种
     * @return 已达返回 true
     */
    private boolean bondSideways(VarietyVO variety) {
        try {
            BondMonitorRequest request = new BondMonitorRequest();
            request.setVarietyKey(variety.getVarietyKey());
            BondMonitorVO monitor = bondMonitorService.adviseOne(request);
            return monitor != null && Boolean.TRUE.equals(monitor.getAlerting());
        } catch (RuntimeException e) {
            // 债券侧取数失败只是少一条依据，不能让整批警示失败
            log.warn("债券横盘判定失败 varietyKey={}：{}", variety.getVarietyKey(), e.getMessage());
            return false;
        }
    }

    /**
     * 取该品种的日线，场内按自身代码，场外没有日线返回空
     *
     * @param variety 品种
     * @return 日线
     */
    private List<MarketBar> barsOf(VarietyVO variety) {
        if (variety.getVarietyType() != VarietyType.ETF || variety.getVarietyCode() == null) {
            return List.of();
        }
        try {
            MarketHistory history = marketQuoteAdapter.history(variety.getVarietyCode(), HISTORY_BARS);
            return history.getBars() == null ? List.of() : history.getBars();
        } catch (RuntimeException e) {
            log.warn("教训警示取日线失败 code={}：{}", variety.getVarietyCode(), e.getMessage());
            return List.of();
        }
    }

    /**
     * 把命中的警示转成视图
     *
     * @param target  收集列表
     * @param alert   命中结果，可为 null
     * @param variety 品种
     */
    private void addIfHit(List<LessonAlertVO> target, LessonAlert alert, VarietyVO variety) {
        if (alert == null) {
            return;
        }
        LessonAlertVO vo = new LessonAlertVO();
        vo.setPattern(alert.getPattern() == null ? null : alert.getPattern().name());
        vo.setPatternLabel(alert.getPatternLabel());
        vo.setVarietyKey(alert.getVarietyKey());
        vo.setVarietyCode(variety == null ? null : variety.getVarietyCode());
        vo.setVarietyName(variety == null ? alert.getVarietyName() : variety.getVarietyName());
        vo.setLesson(alert.getLesson());
        vo.setRemarks(alert.getRemarks());
        vo.setTriggerValue(alert.getTriggerValue());
        vo.setReferenceValue(alert.getReferenceValue());
        vo.setTriggerDate(alert.getTriggerDate());
        target.add(vo);
    }

    /**
     * 取历史变动，按回看天数过滤
     *
     * @param query 查询请求
     * @return 变动列表
     */
    private List<HoldingChangeVO> changesOf(LessonQueryRequest query) {
        HoldingChangeQueryRequest holdingQuery = new HoldingChangeQueryRequest();
        holdingQuery.setVarietyKey(query.getVarietyKey());
        holdingQuery.setLimit(500);
        int days = query.getDays() == null || query.getDays() <= 0 ? DEFAULT_DAYS : query.getDays();
        LocalDate from = LocalDate.now().minusDays(days);
        List<HoldingChangeVO> matched = new ArrayList<>();
        for (HoldingChangeVO change : holdingService.listChanges(holdingQuery)) {
            if (change.getOpenDate() == null || !change.getOpenDate().isBefore(from)) {
                matched.add(change);
            }
        }
        return matched;
    }

    /**
     * 汇总成总览，逐条摘要供邮件直接取用
     *
     * @param plan    警示参数
     * @param checked 检查的变动笔数
     * @param items   命中的警示
     * @return 总览
     */
    private LessonAdvisoryVO assemble(LessonPlan plan, int checked, List<LessonAlertVO> items) {
        LessonAdvisoryVO vo = new LessonAdvisoryVO();
        vo.setItems(items);
        vo.setCheckedCount(checked);
        vo.setAlertCount(items.size());
        vo.setParamUsable(plan.complete());
        vo.setParamIssues(plan.getIssues());
        vo.setChaseTolerance(plan.getChaseTolerance());
        vo.setPanicLossDays(plan.getPanicLossDays());
        vo.setParticipateInAlert(plan.alertEnabled());
        vo.setSummary(summaryOf(items));
        if (checked == 0) {
            vo.setNotice("尚无历史买卖记录，教训警示先录入持仓才会开始工作");
        }
        log.info("历史教训警示完成 检查 {} 笔 命中 {} 条", checked, items.size());
        return vo;
    }

    /**
     * 拼逐条摘要，每条必须带教训说明
     *
     * @param items 命中的警示
     * @return 摘要列表
     */
    private List<String> summaryOf(List<LessonAlertVO> items) {
        List<String> summary = new ArrayList<>();
        for (LessonAlertVO item : items) {
            String reason = item.getRemarks() == null || item.getRemarks().isEmpty() ? ""
                    : item.getRemarks().get(0);
            summary.add(item.getPatternLabel() + "｜" + item.getVarietyCode() + " " + item.getVarietyName()
                    + "：" + reason + "。教训：" + item.getLesson());
        }
        return summary;
    }

    /**
     * 按业务主键查品种
     *
     * @param varietyKey 品种业务主键
     * @return 品种，未找到返回 null
     */
    private VarietyVO findVariety(String varietyKey) {
        if (varietyKey == null || varietyKey.isBlank()) {
            return null;
        }
        VarietyQueryRequest query = new VarietyQueryRequest();
        query.setVarietyKey(varietyKey);
        List<VarietyVO> found = varietyService.listVariety(query);
        return found.isEmpty() ? null : found.get(0);
    }
}

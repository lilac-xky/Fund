package com.lilac.mail;

import com.lilac.domain.dto.dividend.DividendQueryRequest;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.BondAdvisoryVO;
import com.lilac.domain.vo.DividendAdviceVO;
import com.lilac.domain.vo.DividendAdvisoryVO;
import com.lilac.domain.vo.EstimateBatchVO;
import com.lilac.domain.vo.EstimateVO;
import com.lilac.domain.vo.GridAdvisoryVO;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.IndicatorAdvisoryVO;
import com.lilac.domain.vo.IndexDividendOverviewVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.HoldingStatus;
import com.lilac.service.BondMonitorService;
import com.lilac.service.DividendAdviceService;
import com.lilac.service.GridAdviceService;
import com.lilac.service.IndicatorService;
import com.lilac.service.IndexDividendService;
import com.lilac.service.HoldingService;
import com.lilac.service.VarietyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 邮件数据装配，把持仓、预估、红利建议与股息率日期凑成一份可渲染的邮件模型
 */
@Slf4j
@Component
public class MailContentBuilder {

    private final HoldingService holdingService;

    private final VarietyService varietyService;

    private final DividendAdviceService dividendAdviceService;

    private final IndexDividendService indexDividendService;

    private final GridAdviceService gridAdviceService;

    private final BondMonitorService bondMonitorService;

    private final IndicatorService indicatorService;

    /**
     * 注入邮件需要的七类业务
     *
     * @param holdingService        持仓业务
     * @param varietyService        品种业务
     * @param dividendAdviceService 红利建议业务
     * @param indexDividendService  股息率业务
     * @param gridAdviceService     网格提醒业务
     * @param bondMonitorService    债券监控业务
     * @param indicatorService      技术指标业务
     */
    public MailContentBuilder(HoldingService holdingService, VarietyService varietyService,
                              DividendAdviceService dividendAdviceService,
                              IndexDividendService indexDividendService,
                              GridAdviceService gridAdviceService,
                              BondMonitorService bondMonitorService,
                              IndicatorService indicatorService) {
        this.holdingService = holdingService;
        this.varietyService = varietyService;
        this.dividendAdviceService = dividendAdviceService;
        this.indexDividendService = indexDividendService;
        this.gridAdviceService = gridAdviceService;
        this.bondMonitorService = bondMonitorService;
        this.indicatorService = indicatorService;
    }

    /**
     * 装配邮件模型，尚无持仓时返回 holdings 为空的模型，由调用方改发提示邮件
     *
     * @param tradeDate 交易日
     * @param estimate  当日预估总览，可为空
     * @return 邮件模型
     */
    public MailContent build(LocalDate tradeDate, EstimateBatchVO estimate) {
        MailContent content = new MailContent();
        content.setTradeDate(tradeDate);
        List<VarietyVO> varieties = holdingVarieties();
        content.setVarietyCount(varieties.size());
        if (varieties.isEmpty()) {
            return content;
        }
        Map<String, VarietyVO> varietyMap = new LinkedHashMap<>();
        varieties.forEach(variety -> varietyMap.put(variety.getVarietyKey(), variety));
        fillEstimates(content, estimate, varietyMap);
        fillDividends(content, varieties);
        fillGrid(content, varieties);
        fillBonds(content, varieties);
        fillIndicators(content, varieties);
        fillDataCutoff(content, estimate, varieties);
        return content;
    }

    /**
     * 取全部持有中的品种，同一品种只出现一次
     *
     * @return 品种列表
     */
    private List<VarietyVO> holdingVarieties() {
        HoldingQueryRequest holdingQuery = new HoldingQueryRequest();
        holdingQuery.setStatus(HoldingStatus.HOLDING);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
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
            if (keys.contains(variety.getVarietyKey())) {
                varieties.add(variety);
            }
        }
        return varieties;
    }

    /**
     * 把预估结果拆成可预估与不可预估两段，不可预估的单独成段并带原因
     *
     * @param content    邮件模型
     * @param estimate   预估总览
     * @param varietyMap 品种表
     */
    private void fillEstimates(MailContent content, EstimateBatchVO estimate, Map<String, VarietyVO> varietyMap) {
        if (estimate == null) {
            content.setEstimateUnavailableReason("今日预估未产出，无法给出涨跌推算");
            content.getUnavailableItems().addAll(unavailableFromHoldings(varietyMap, "今日预估未产出"));
            return;
        }
        content.setOverallChange(estimate.getOverallChange());
        content.setOverallNote(estimate.getOverallNote());
        content.setTradingHours(estimate.getTradingHours());
        fillRows(content.getItems(), estimate.getItems(), varietyMap);
        fillRows(content.getUnavailableItems(), estimate.getUnavailableItems(), varietyMap);
    }

    /**
     * 把预估行转成邮件行，空列表也安全
     *
     * @param target     目标行列表
     * @param source     预估行
     * @param varietyMap 品种表
     */
    private void fillRows(List<MailContent.Item> target, List<EstimateVO> source,
                          Map<String, VarietyVO> varietyMap) {
        if (source == null) {
            return;
        }
        for (EstimateVO vo : source) {
            MailContent.Item item = new MailContent.Item();
            item.setVarietyKey(vo.getVarietyKey());
            item.setCode(vo.getVarietyCode());
            item.setName(vo.getVarietyName());
            item.setCategory(vo.getCategoryLabel());
            item.setBranch(vo.getBranchLabel());
            item.setTier(vo.getTierLabel());
            item.setConfidence(vo.getConfidenceLabel());
            item.setEstimatedChange(vo.getEstimatedChange());
            item.setDirection(vo.getDirectionLabel());
            item.setIntervalLow(vo.getIntervalLow());
            item.setIntervalHigh(vo.getIntervalHigh());
            item.setCoverage(vo.getCoverage());
            item.setUnavailableReason(vo.getUnavailableReason());
            item.setEstimable(Boolean.TRUE.equals(vo.getEstimable()));
            VarietyVO variety = varietyMap.get(vo.getVarietyKey());
            if (variety != null) {
                item.setCategory(variety.getCategory() == null ? item.getCategory()
                        : variety.getCategory().getLabel());
            }
            target.add(item);
        }
    }

    /**
     * 预估缺失时把全部持仓列为不可预估，附统一原因，绝不显示数字
     *
     * @param varietyMap 品种表
     * @param reason     原因
     * @return 不可预估行
     */
    private List<MailContent.Item> unavailableFromHoldings(Map<String, VarietyVO> varietyMap, String reason) {
        List<MailContent.Item> items = new ArrayList<>();
        for (VarietyVO variety : varietyMap.values()) {
            MailContent.Item item = new MailContent.Item();
            item.setVarietyKey(variety.getVarietyKey());
            item.setCode(variety.getVarietyCode());
            item.setName(variety.getVarietyName());
            item.setCategory(variety.getCategory() == null ? "待确认" : variety.getCategory().getLabel());
            item.setEstimable(false);
            item.setUnavailableReason(reason);
            items.add(item);
        }
        return items;
    }

    /**
     * 取红利建议汇总，失败时只记日志，不让一段建议拖垮整封邮件
     *
     * @param content   邮件模型
     * @param varieties 品种列表
     */
    private void fillDividends(MailContent content, List<VarietyVO> varieties) {
        boolean hasDividend = varieties.stream()
                .anyMatch(variety -> variety.getCategory() == com.lilac.enums.FundCategory.DIVIDEND);
        if (!hasDividend) {
            return;
        }
        try {
            DividendAdvisoryVO advisory = dividendAdviceService.adviseAll();
            content.setDividendAdvisory(advisory);
            content.setDividendAvailable(Boolean.TRUE.equals(advisory.getParamUsable()));
            List<String> summaries = new ArrayList<>();
            if (!content.isDividendAvailable()) {
                summaries.add("红利参数未配置完整，本次不给加减仓动作：" + join(advisory.getParamIssues()));
            }
            for (DividendAdviceVO advice : safe(advisory.getItems())) {
                summaries.add(summaryOf(advice));
            }
            for (DividendAdviceVO advice : safe(advisory.getSkippedItems())) {
                summaries.add(summaryOf(advice));
            }
            content.setDividendSummary(summaries);
        } catch (RuntimeException e) {
            log.warn("红利建议装配失败，邮件里标注为不可用：{}", e.getMessage());
            content.setDividendAvailable(false);
            content.setDividendSummary(List.of("红利建议本次取数失败，请到页面查看：" + e.getMessage()));
        }
    }

    /**
     * 拼单条红利建议的一句话，金额取整到元后原样带出
     *
     * @param advice 建议
     * @return 摘要
     */
    private String summaryOf(DividendAdviceVO advice) {
        StringBuilder builder = new StringBuilder();
        builder.append(advice.getVarietyCode()).append(" ").append(advice.getVarietyName()).append("：")
                .append(advice.getActionLabel());
        if (advice.getSuggestAmount() != null) {
            builder.append(" ").append(advice.getSuggestAmount().stripTrailingZeros().toPlainString()).append(" 元");
        }
        if (advice.getRemainingAmount() != null) {
            builder.append("，减仓后剩余 ").append(advice.getRemainingAmount().stripTrailingZeros().toPlainString())
                    .append(" 元");
        }
        if (Boolean.TRUE.equals(advice.getFloorProtected())) {
            builder.append("（保底分红金额已触及，改为持有）");
        }
        if (Boolean.TRUE.equals(advice.getStale()) && advice.getDataDate() != null) {
            builder.append("（股息率数据日期 ").append(advice.getDataDate()).append("，已过期）");
        }
        if (advice.getRemarks() != null && !advice.getRemarks().isEmpty()) {
            builder.append("；依据：").append(advice.getRemarks().get(0));
        }
        return builder.toString();
    }

    /**
     * 取网格到价提醒，失败时只记日志，不让一段提醒拖垮整封邮件
     *
     * @param content   邮件模型
     * @param varieties 品种列表
     */
    private void fillGrid(MailContent content, List<VarietyVO> varieties) {
        boolean hasEtf = varieties.stream()
                .anyMatch(variety -> variety.getVarietyType() == com.lilac.enums.VarietyType.ETF);
        if (!hasEtf) {
            return;
        }
        try {
            GridAdvisoryVO advisory = gridAdviceService.adviseAll();
            content.setGridAdvisory(advisory);
            content.setGridAvailable(Boolean.TRUE.equals(advisory.getParamUsable()));
            List<String> summaries = new ArrayList<>();
            if (!content.isGridAvailable()) {
                summaries.add("网格参数未配置完整，本次不给网格提醒：" + join(advisory.getParamIssues()));
            }
            summaries.addAll(safe(advisory.getSummary()));
            if (summaries.isEmpty()) {
                summaries.add("场内 ETF 当前价未越过任何网格线，本次无网格提醒");
            }
            content.setGridSummary(summaries);
        } catch (RuntimeException e) {
            log.warn("网格提醒装配失败，邮件里标注为不可用：{}", e.getMessage());
            content.setGridAvailable(false);
            content.setGridSummary(List.of("网格提醒本次取数失败，请到页面查看：" + e.getMessage()));
        }
    }

    /**
     * 取债券监控结果，失败时只记日志，不让一段提醒拖垮整封邮件
     *
     * @param content   邮件模型
     * @param varieties 品种列表
     */
    private void fillBonds(MailContent content, List<VarietyVO> varieties) {
        boolean hasBond = varieties.stream()
                .anyMatch(variety -> variety.getCategory() == com.lilac.enums.FundCategory.BOND);
        if (!hasBond) {
            return;
        }
        try {
            BondAdvisoryVO advisory = bondMonitorService.adviseAll();
            if (advisory == null) {
                content.setBondAvailable(false);
                content.setBondSummary(List.of("债券监控本次没有返回结果，请到页面查看"));
                return;
            }
            content.setBondAdvisory(advisory);
            content.setBondAvailable(Boolean.TRUE.equals(advisory.getParamUsable()));
            List<String> summaries = new ArrayList<>();
            if (!content.isBondAvailable()) {
                summaries.add("债券监控参数不合法，本次不给换仓提醒：" + join(advisory.getParamIssues()));
            }
            summaries.addAll(safe(advisory.getSummary()));
            if (summaries.isEmpty()) {
                summaries.add("债券基金净值未横盘也未转弱，本次无换仓提醒");
            }
            content.setBondSummary(summaries);
        } catch (RuntimeException e) {
            log.warn("债券监控装配失败，邮件里标注为不可用：{}", e.getMessage());
            content.setBondAvailable(false);
            content.setBondSummary(List.of("债券监控本次取数失败，请到页面查看：" + e.getMessage()));
        }
    }

    /**
     * 取技术指标结果，只在参数开启参与提醒时有内容，失败同样只记日志
     *
     * @param content   邮件模型
     * @param varieties 品种列表
     */
    private void fillIndicators(MailContent content, List<VarietyVO> varieties) {
        if (varieties.isEmpty()) {
            return;
        }
        try {
            IndicatorAdvisoryVO advisory = indicatorService.computeAll();
            if (advisory == null) {
                content.setIndicatorAvailable(false);
                content.setIndicatorSummary(List.of("技术指标本次没有返回结果，请到页面查看"));
                return;
            }
            content.setIndicatorAdvisory(advisory);
            // 开关关闭时指标只在页面展示，不准进邮件，这是参数页对使用者的承诺
            if (Boolean.FALSE.equals(advisory.getParticipateInAlert())) {
                content.setIndicatorAvailable(false);
                content.setIndicatorSummary(List.of("指标参数已关闭「参与提醒」，本次不在邮件里给指标信号"));
                return;
            }
            content.setIndicatorAvailable(Boolean.TRUE.equals(advisory.getParamUsable()));
            List<String> summaries = new ArrayList<>();
            if (!content.isIndicatorAvailable()) {
                summaries.add("指标参数不合法，本次不给指标信号：" + join(advisory.getParamIssues()));
                content.setIndicatorSummary(summaries);
                return;
            }
            summaries.addAll(safe(advisory.getSummary()));
            content.setIndicatorSummary(summaries);
        } catch (RuntimeException e) {
            log.warn("技术指标装配失败，邮件里标注为不可用：{}", e.getMessage());
            content.setIndicatorAvailable(false);
            content.setIndicatorSummary(List.of("技术指标本次计算失败，请到页面查看：" + e.getMessage()));
        }
    }

    /**
     * 汇总数据截止时间，含行情日期与股息率最旧日期，两者都在正文与页面出现
     *
     * @param content   邮件模型
     * @param estimate  预估总览
     * @param varieties 品种列表
     */
    private void fillDataCutoff(MailContent content, EstimateBatchVO estimate, List<VarietyVO> varieties) {
        LocalDate quoteDate = estimate == null ? null : estimate.getDataDate();
        content.setQuoteDate(quoteDate);
        LocalDate dividendDate = oldestDividendDate(varieties);
        content.setDividendDataDate(dividendDate);
        LocalDate cutoff = quoteDate;
        if (dividendDate != null && (cutoff == null || dividendDate.isBefore(cutoff))) {
            cutoff = dividendDate;
        }
        content.setDataCutoffDate(cutoff == null ? content.getTradeDate() : cutoff);
    }

    /**
     * 取相关红利基金跟踪指数里最旧的一期股息率日期
     *
     * @param varieties 品种列表
     * @return 最旧日期，无数据返回 null
     */
    private LocalDate oldestDividendDate(List<VarietyVO> varieties) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        varieties.stream()
                .filter(variety -> variety.getCategory() == com.lilac.enums.FundCategory.DIVIDEND)
                .map(VarietyVO::getTrackIndexCode)
                .filter(code -> code != null && !code.isBlank())
                .forEach(codes::add);
        if (codes.isEmpty()) {
            return null;
        }
        LocalDate oldest = null;
        for (String code : codes) {
            DividendQueryRequest query = new DividendQueryRequest();
            query.setIndexCode(code);
            try {
                for (IndexDividendOverviewVO row : safe(indexDividendService.listOverview(query))) {
                    if (row.getDataDate() != null && (oldest == null || row.getDataDate().isBefore(oldest))) {
                        oldest = row.getDataDate();
                    }
                }
            } catch (RuntimeException e) {
                log.warn("股息率日期读取失败 code={}：{}", code, e.getMessage());
            }
        }
        return oldest;
    }

    /**
     * 空列表兜底，避免上游给 null 时遍历炸掉
     *
     * @param list 原列表
     * @return 非空列表
     * @param <T>  元素类型
     */
    private <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    /**
     * 把问题清单拼成一句话
     *
     * @param issues 问题清单
     * @return 一句话
     */
    private String join(List<String> issues) {
        return issues == null || issues.isEmpty() ? "无" : String.join("；", issues);
    }
}

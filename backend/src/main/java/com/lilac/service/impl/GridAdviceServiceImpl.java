package com.lilac.service.impl;

import com.lilac.adapter.fund.IndexEtfRegistry;
import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.config.GridProperties;
import com.lilac.domain.dto.dividend.DividendAdviceRequest;
import com.lilac.domain.dto.grid.GridAdviceRequest;
import com.lilac.domain.dto.grid.GridParamSaveRequest;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.DividendAdviceVO;
import com.lilac.domain.vo.GridAdviceVO;
import com.lilac.domain.vo.GridAdvisoryVO;
import com.lilac.domain.vo.GridPlanVO;
import com.lilac.domain.vo.GridWatchVO;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.engine.BenchmarkResolver;
import com.lilac.enums.DividendAction;
import com.lilac.enums.FundCategory;
import com.lilac.enums.GridAction;
import com.lilac.enums.GridBasis;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.VarietyType;
import com.lilac.service.DividendAdviceService;
import com.lilac.service.FundNetWorthService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 网格到价提醒业务实现，场内用实时价、场外按跟踪 ETF 在尾盘估净值，每只按自己的格子判定穿越
 */
@Slf4j
@Service
public class GridAdviceServiceImpl implements GridAdviceService {

    private static final int PRICE_SCALE = 4;
    private static final String DIVIDEND_NOTE =
            "同一标的的红利建议优先于网格提醒：红利侧给出加仓或减仓时，网格提醒让位，不重复给出反向动作";
    private static final String OUT_OF_WINDOW =
            "场外基金按跟踪 ETF 的当日涨跌在尾盘估净值，盘中的价格还会变，故只在 "
                    + "14:00 之后给提醒。回到这个时间点再看一次即可";
    private static final String NO_BENCHMARK =
            "该基金没有可用的跟踪 ETF 或对标指数，取不到可估的价格，无法参与场外网格";
    private static final String NOT_CONFIGURED = "这只标的还没划格子，网格对它不生效，请先配置上下界与格子大小";
    private static final String POSITION_INSIDE = "INSIDE";
    private static final String POSITION_ABOVE = "ABOVE";
    private static final String POSITION_BELOW = "BELOW";

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

    @Resource
    private FundNetWorthService fundNetWorthService;

    @Resource
    private BenchmarkResolver benchmarkResolver;

    @Resource
    private GridProperties gridProperties;

    /**
     * 读取指定标的的网格参数，没配过时由问题清单说明
     *
     * @param varietyKey 品种业务主键
     * @return 参数视图
     */
    @Override
    public GridPlanVO getPlanOf(String varietyKey) {
        ThrowUtils.throwIf(varietyKey == null || varietyKey.isBlank(), HttpsCodeEnum.PARAMS_ERROR,
                "品种业务主键不能为空");
        VarietyVO variety = findVariety(varietyKey.trim());
        ThrowUtils.throwIf(variety == null, HttpsCodeEnum.NOT_FOUND_ERROR,
                "未找到品种 " + varietyKey);
        GridPlan plan = gridPlanResolver.resolveFor(variety.getVarietyKey(), variety.getVarietyName());
        GridPlanVO vo = toPlanVO(plan);
        vo.setVarietyKey(plan.getVarietyKey());
        vo.setVarietyName(plan.getVarietyName());
        return vo;
    }

    /**
     * 保存指定标的的网格参数，不合法时拒绝
     *
     * @param request 保存请求
     * @return 保存后的参数视图
     */
    @Override
    public GridPlanVO savePlan(GridParamSaveRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR, "网格参数不能为空");
        ThrowUtils.throwIf(request.getVarietyKey() == null || request.getVarietyKey().isBlank(),
                HttpsCodeEnum.PARAMS_ERROR, "保存网格参数必须指定标的");
        VarietyVO variety = findVariety(request.getVarietyKey().trim());
        ThrowUtils.throwIf(variety == null, HttpsCodeEnum.NOT_FOUND_ERROR,
                "未找到品种 " + request.getVarietyKey());
        GridPlan plan = toPlan(request, variety.getVarietyKey());
        GridPlan saved = gridPlanResolver.save(plan);
        saved.setVarietyName(variety.getVarietyName());
        return toPlanVO(saved);
    }

    /**
     * 清除指定标的的网格参数，回到未配置状态
     *
     * @param varietyKey 品种业务主键
     * @return 是否清掉了至少一条
     */
    @Override
    public boolean removePlan(String varietyKey) {
        ThrowUtils.throwIf(varietyKey == null || varietyKey.isBlank(), HttpsCodeEnum.PARAMS_ERROR,
                "必须指定要清除的标的");
        return gridPlanResolver.remove(varietyKey.trim()) > 0;
    }

    /**
     * 把保存请求转成参数对象
     *
     * @param request    保存请求
     * @param varietyKey 品种业务主键
     * @return 参数
     */
    private GridPlan toPlan(GridParamSaveRequest request, String varietyKey) {
        GridPlan plan = new GridPlan();
        plan.setVarietyKey(varietyKey);
        plan.setLowerBound(request.getLowerBound());
        plan.setUpperBound(request.getUpperBound());
        plan.setStep(request.getStep());
        plan.setAmountPerGrid(request.getAmountPerGrid());
        return plan;
    }

    /**
     * 对全部持有中的标的判断网格触发，每只用它自己的格子
     *
     * @return 提醒总览
     */
    @Override
    public GridAdvisoryVO adviseAll() {
        List<VarietyVO> varieties = gridVarieties();
        List<GridAdviceVO> items = new ArrayList<>();
        List<GridAdviceVO> idle = new ArrayList<>();
        List<String> unconfigured = new ArrayList<>();
        for (VarietyVO variety : varieties) {
            // 每只标的有自己的上下界，不能拿一套价格区间套在所有标的上
            GridPlan plan = gridPlanResolver.resolveFor(variety.getVarietyKey(), variety.getVarietyName());
            if (!plan.complete()) {
                unconfigured.add(variety.getVarietyCode() + " " + variety.getVarietyName()
                        + "：" + String.join("；", plan.getIssues()));
            }
            GridAdviceVO advice = advise(variety, plan, null, null);
            if (advice.getAction() == GridAction.BUY || advice.getAction() == GridAction.SELL) {
                items.add(advice);
            } else {
                idle.add(advice);
            }
        }
        return assemble(varieties.size(), items, idle, unconfigured);
    }

    /**
     * 列出网格的监控对象，带上当前价与区间状态，说明每只标的的格子还能不能用
     *
     * @return 监控对象列表
     */
    @Override
    public List<GridWatchVO> watchList() {
        List<VarietyVO> varieties = gridVarieties();
        // 一次取回已单独配置过的标的集合，避免逐只查询各自是否配置过
        Set<String> customized = gridPlanResolver.listAll().stream()
                .map(GridPlan::getVarietyKey)
                .filter(key -> key != null)
                .collect(Collectors.toSet());
        List<GridWatchVO> result = new ArrayList<>(varieties.size());
        for (VarietyVO variety : varieties) {
            result.add(watchOf(variety, customized.contains(variety.getVarietyKey())));
        }
        return result;
    }

    /**
     * 组装一只标的的监控对象，参数、价格与区间状态都在这里汇合
     *
     * @param variety    品种
     * @param customized 是否已单独配置过格子
     * @return 监控对象
     */
    private GridWatchVO watchOf(VarietyVO variety, boolean customized) {
        GridPlan plan = gridPlanResolver.resolveFor(variety.getVarietyKey(), variety.getVarietyName());
        GridWatchVO vo = new GridWatchVO();
        vo.setVarietyKey(variety.getVarietyKey());
        vo.setVarietyCode(variety.getVarietyCode());
        vo.setVarietyName(variety.getVarietyName());
        vo.setCategoryLabel(variety.getCategory() == null ? "待确认" : variety.getCategory().getLabel());
        vo.setVarietyType(variety.getVarietyType() == null ? null : variety.getVarietyType().name());
        boolean onExchange = variety.getVarietyType() == VarietyType.ETF;
        vo.setBasis(onExchange ? GridBasis.REALTIME_PRICE : GridBasis.ESTIMATED_NAV);
        vo.setBasisLabel(vo.getBasis().getLabel());
        vo.setProxySymbol(onExchange ? null : proxySymbolOf(variety));
        vo.setLowerBound(plan.getLowerBound());
        vo.setUpperBound(plan.getUpperBound());
        vo.setStep(plan.getStep());
        vo.setAmountPerGrid(plan.getAmountPerGrid());
        vo.setParamUsable(plan.complete());
        vo.setParamIssues(plan.getIssues());
        vo.setParamNotes(plan.getNotes());
        vo.setLineCount(plan.complete() ? gridCalculator.lines(plan).size() : 0);
        vo.setCustomized(customized);
        if (!customized) {
            // 没配过就明说网格对它不生效，不能让使用者以为服务已经在盯着
            vo.getParamNotes().add(NOT_CONFIGURED);
        }
        if (!onExchange && (vo.getProxySymbol() == null || vo.getProxySymbol().isBlank())) {
            // 场外没有跟踪 ETF 就推不出价格，必须在监控对象里明说
            vo.getParamIssues().add(NO_BENCHMARK);
            vo.setParamUsable(Boolean.FALSE);
        }
        Price reference = watchPrice(variety, onExchange);
        vo.setCurrentPrice(reference.current);
        vo.setPriceDate(reference.date);
        vo.setPriceSource(reference.source);
        if (reference.blocked != null) {
            vo.getParamNotes().add(reference.blocked);
            return vo;
        }
        fillPosition(vo, plan, reference.current);
        return vo;
    }

    /**
     * 标注当前价落在格子区间的哪个位置，出界时明确提示要重新划格
     *
     * @param vo    监控对象
     * @param plan  参数
     * @param price 当前价
     */
    private void fillPosition(GridWatchVO vo, GridPlan plan, BigDecimal price) {
        if (price == null || !plan.complete()) {
            return;
        }
        if (price.compareTo(plan.getUpperBound()) > 0) {
            vo.setPosition(POSITION_ABOVE);
            vo.setPositionNote("当前价 " + plain(price) + " 已高于上界 " + plain(plan.getUpperBound())
                    + "，超出后没有可卖的网格线，需重新划格");
            return;
        }
        if (price.compareTo(plan.getLowerBound()) < 0) {
            vo.setPosition(POSITION_BELOW);
            vo.setPositionNote("当前价 " + plain(price) + " 已低于下界 " + plain(plan.getLowerBound())
                    + "，超出后没有可买的网格线，需重新划格");
            return;
        }
        vo.setPosition(POSITION_INSIDE);
        vo.setPositionNote("当前价 " + plain(price) + " 落在格子区间内");
    }

    /**
     * 只取当前价，供监控对象展示；场外同样只在尾盘才估得出，盘中如实说明
     *
     * @param variety    品种
     * @param onExchange 是否场内 ETF
     * @return 价格组合，取不到时 blocked 写明原因
     */
    private Price watchPrice(VarietyVO variety, boolean onExchange) {
        if (variety.getVarietyCode() == null) {
            return new Price(null, null, "无代码，取不到价格", "该品种没有代码，取不到价格，无法定位网格");
        }
        if (onExchange) {
            try {
                MarketQuote quote = marketQuoteAdapter.quote(variety.getVarietyCode());
                if (quote == null || quote.getPrice() == null) {
                    return new Price(null, null, "行情取不到", "行情取不到，无法定位网格");
                }
                String source = Boolean.TRUE.equals(quote.getRealtime()) ? "行情价（实时）" : "行情价（上一交易日）";
                return new Price(quote.getPrice(), quote.getTradeDate(), source, null);
            } catch (RuntimeException e) {
                log.warn("网格监控取行情失败 code={}：{}", variety.getVarietyCode(), e.getMessage());
                return new Price(null, null, "行情取数失败", "行情取数失败，无法定位网格：" + e.getMessage());
            }
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.toLocalTime().isBefore(gridProperties.getTailWindowStart())) {
            return new Price(null, now.toLocalDate(), "未到到尾盘判断时点", OUT_OF_WINDOW);
        }
        Price estimated = offExchangePrice(variety, null);
        return new Price(estimated.current, estimated.date, estimated.source, estimated.blocked);
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
        ThrowUtils.throwIf(request.getCurrentPrice() != null && request.getCurrentPrice().signum() <= 0,
                HttpsCodeEnum.PARAMS_ERROR, "当前价必须大于 0");
        ThrowUtils.throwIf(request.getReferencePrice() != null && request.getReferencePrice().signum() <= 0,
                HttpsCodeEnum.PARAMS_ERROR, "参考价必须大于 0");
        // 试算也按该标的自己的格子，否则核对结果与实盘提醒对不上
        return advise(variety, gridPlanResolver.resolveFor(variety.getVarietyKey(), variety.getVarietyName()),
                request.getCurrentPrice(), request.getReferencePrice());
    }

    /**
     * 列出指定标的的网格线
     *
     * @param varietyKey 品种业务主键
     * @return 网格线价格，按升序
     */
    @Override
    public List<BigDecimal> lines(String varietyKey) {
        ThrowUtils.throwIf(varietyKey == null || varietyKey.isBlank(), HttpsCodeEnum.PARAMS_ERROR,
                "品种业务主键不能为空");
        VarietyVO variety = findVariety(varietyKey.trim());
        ThrowUtils.throwIf(variety == null, HttpsCodeEnum.NOT_FOUND_ERROR, "未找到品种 " + varietyKey);
        return gridCalculator.lines(gridPlanResolver.resolveFor(variety.getVarietyKey(), variety.getVarietyName()));
    }

    /**
     * 单只标的出提醒，价格口径与红利互斥都在这里装配
     *
     * @param variety        品种
     * @param plan           网格参数
     * @param manualPrice    手工当前价，为空时按场内行情或场外预估
     * @param manualReference 手工参考价，为空时取上一交易日基准
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
        boolean onExchange = variety.getVarietyType() == VarietyType.ETF;
        vo.setBasis(onExchange ? GridBasis.REALTIME_PRICE : GridBasis.ESTIMATED_NAV);
        vo.setBasisLabel(vo.getBasis().getLabel());
        Price reference = priceOf(variety, manualPrice, manualReference, onExchange);
        vo.setCurrentPrice(reference.current);
        vo.setPriceDate(reference.date);
        vo.setPriceSource(reference.source);
        vo.setReferencePrice(reference.previous);
        vo.setReferenceDate(reference.previousDate);
        if (!plan.complete()) {
            // 没划格子时直接说明，不拿空参数去算穿越
            vo.setAction(GridAction.NONE);
            vo.setActionLabel(GridAction.NONE.getLabel());
            vo.setLineCount(0);
            vo.setSuppressed(Boolean.FALSE);
            List<String> remarks = new ArrayList<>(plan.getIssues());
            remarks.add(NOT_CONFIGURED);
            vo.setRemarks(remarks);
            return vo;
        }
        if (reference.blocked != null) {
            // 拿不到可比价格时直接说明原因，不进入穿越判定，避免用错价给出动作
            vo.setAction(GridAction.NONE);
            vo.setActionLabel(GridAction.NONE.getLabel());
            vo.setLineCount(gridCalculator.lines(plan).size());
            vo.setSuppressed(Boolean.FALSE);
            vo.setRemarks(List.of(reference.blocked));
            return vo;
        }
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
        if (!onExchange) {
            vo.getRemarks().add("场外基金按跟踪 ETF 的当日涨跌估净值，尾盘判断，盘中价格还会变");
        }
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
     * 取当前价与参考价，场内用实时行情，场外用跟踪 ETF 在尾盘估出的净值
     *
     * @param variety         品种
     * @param manualPrice     手工当前价
     * @param manualReference 手工参考价
     * @param onExchange      是否场内 ETF
     * @return 价格组合，不可判断时 blocked 写明原因
     */
    private Price priceOf(VarietyVO variety, BigDecimal manualPrice, BigDecimal manualReference,
                          boolean onExchange) {
        if (manualPrice != null) {
            return new Price(manualPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP), null,
                    "手工指定当前价", manualReference, null, null);
        }
        if (variety.getVarietyCode() == null) {
            return new Price(null, null, "无代码，取不到价格", manualReference, null,
                    "该品种没有代码，取不到价格，无法定位网格");
        }
        return onExchange ? exchangePrice(variety, manualReference) : offExchangePrice(variety, manualReference);
    }

    /**
     * 场内：直接取行情最新价与昨收
     *
     * @param variety         品种
     * @param manualReference 手工参考价
     * @return 价格组合
     */
    private Price exchangePrice(VarietyVO variety, BigDecimal manualReference) {
        try {
            MarketQuote quote = marketQuoteAdapter.quote(variety.getVarietyCode());
            if (quote == null || quote.getPrice() == null) {
                return new Price(null, null, "行情取不到", manualReference, null,
                        "行情取不到，无法定位网格");
            }
            String source = Boolean.TRUE.equals(quote.getRealtime()) ? "行情价（实时）" : "行情价（上一交易日）";
            return new Price(quote.getPrice(), quote.getTradeDate(), source,
                    manualReference == null ? quote.getPreviousClose() : manualReference,
                    quote.getTradeDate(), null);
        } catch (RuntimeException e) {
            log.warn("网格行情取数失败 code={}：{}", variety.getVarietyCode(), e.getMessage());
            return new Price(null, null, "行情取数失败：" + e.getMessage(), manualReference, null,
                    "行情取数失败，无法定位网格：" + e.getMessage());
        }
    }

    /**
     * 场外：只在尾盘用跟踪 ETF 的当日涨跌估出当日净值，与昨日净值比较
     *
     * @param variety         品种
     * @param manualReference 手工参考价
     * @return 价格组合
     */
    private Price offExchangePrice(VarietyVO variety, BigDecimal manualReference) {
        String proxySymbol = proxySymbolOf(variety);
        if (proxySymbol == null || proxySymbol.isBlank()) {
            return new Price(null, null, NO_BENCHMARK, manualReference, null, NO_BENCHMARK);
        }
        LocalDateTime now = LocalDateTime.now();
        LocalTime start = gridProperties.getTailWindowStart();
        // 盘中价格随时会变，只有临近收盘时的估算才有决策价值
        if (now.toLocalTime().isBefore(start)) {
            return new Price(null, now.toLocalDate(), "未到到尾盘判断时点", manualReference, null, OUT_OF_WINDOW);
        }
        List<FundNetWorthPoint> points = fundNetWorthService.listRange(variety.getVarietyCode(),
                LocalDate.now().minusDays(30), null);
        BigDecimal previousNav = null;
        LocalDate previousDate = null;
        for (int i = points.size() - 1; i >= 0; i--) {
            FundNetWorthPoint point = points.get(i);
            if (point.getUnitNetWorth() == null || point.getNetWorthDate() == null) {
                continue;
            }
            if (point.getNetWorthDate().isBefore(now.toLocalDate())) {
                previousNav = point.getUnitNetWorth();
                previousDate = point.getNetWorthDate();
                break;
            }
        }
        if (previousNav == null) {
            return new Price(null, null, "无昨日净值，无法估算", manualReference, null,
                    "库内没有该基金的可用净值，请先把持仓总览打开一次以拉取净值");
        }
        MarketQuote quote;
        try {
            quote = marketQuoteAdapter.quote(proxySymbol);
        } catch (RuntimeException e) {
            log.warn("场外网格取跟踪 ETF 行情失败 symbol={}：{}", proxySymbol, e.getMessage());
            return new Price(null, null, "跟踪 ETF 行情取数失败", manualReference, null,
                    "跟踪 ETF " + proxySymbol + " 行情取数失败：" + e.getMessage());
        }
        if (quote == null || quote.getChangePercent() == null) {
            return new Price(null, null, "跟踪 ETF 无行情", manualReference, null,
                    "跟踪 ETF " + proxySymbol + " 无行情，今日无法估算场外净值");
        }
        BigDecimal estimated = previousNav.multiply(BigDecimal.ONE.add(
                        quote.getChangePercent().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)))
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        String source = "按跟踪 ETF " + proxySymbol + " 当日 " + quote.getChangePercent()
                .stripTrailingZeros().toPlainString() + "% 估净值";
        return new Price(estimated, now.toLocalDate(), source,
                manualReference == null ? previousNav : manualReference, previousDate, null);
    }

    /**
     * 取场外基金的对标指数或跟踪 ETF 的行情代码
     *
     * @param variety 品种
     * @return 带前缀完整代码，取不到返回 null
     */
    private String proxySymbolOf(VarietyVO variety) {
        if (variety.getVarietyType() == VarietyType.ETF) {
            return BenchmarkResolver.symbolOf(variety.getVarietyCode());
        }
        String trackIndex = variety.getTrackIndexCode();
        if (trackIndex != null && !trackIndex.isBlank()) {
            String symbol = BenchmarkResolver.symbolOf(trackIndex);
            if (symbol != null) {
                return symbol;
            }
            // 字母指数先换成跟踪它的 ETF，避免推不出市场
            String etf = IndexEtfRegistry.findByIndexCode(trackIndex);
            if (etf != null) {
                return BenchmarkResolver.symbolOf(etf);
            }
        }
        return benchmarkResolver.resolve(variety, null);
    }

    /**
     * 取全部持有中的标的，场内 ETF 与场外基金都在列
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
            // 场外基金同样可以做网格，只在尾盘按其跟踪 ETF 的涨跌估出净值后判断
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
     * 汇总成总览，买卖合计只算给出动作的那部分
     *
     * @param requested    纳入的标的数
     * @param items        触发的提醒
     * @param idle         未触发的提醒
     * @param unconfigured 还没划格子的标的，逐条带缺项
     * @return 总览
     */
    private GridAdvisoryVO assemble(int requested, List<GridAdviceVO> items, List<GridAdviceVO> idle,
                                    List<String> unconfigured) {
        items.sort((left, right) -> compareAmount(right.getSuggestAmount(), left.getSuggestAmount()));
        GridAdvisoryVO vo = new GridAdvisoryVO();
        vo.setItems(items);
        vo.setIdleItems(idle);
        vo.setRequested(requested);
        vo.setParamIssues(unconfigured);
        // 参数按标的各存一套，只有全部都没配时才算整批不可用
        vo.setParamUsable(unconfigured.size() < requested);
        vo.setDividendNote(DIVIDEND_NOTE);
        fillTotals(vo, items, idle);
        fillSummary(vo, items, idle, unconfigured);
        if (requested == 0) {
            vo.setNotice("尚无持有中的标的，网格提醒先录入持仓才会开始工作");
        }
        log.info("网格提醒完成 标的 {} 只 触发 {} 只 让位 {} 只 未触发 {} 只 未配置 {} 只",
                requested, vo.getTriggeredCount(), vo.getSuppressedCount(), vo.getIdleCount(), unconfigured.size());
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
     * @param vo           总览
     * @param items        触发的提醒
     * @param idle         未触发的提醒
     * @param unconfigured 还没划格子的标的
     */
    private void fillSummary(GridAdvisoryVO vo, List<GridAdviceVO> items, List<GridAdviceVO> idle,
                             List<String> unconfigured) {
        List<String> summary = new ArrayList<>();
        for (String issue : unconfigured) {
            summary.add(issue + "（还没划格子，网格对它不生效）");
        }
        for (GridAdviceVO item : items) {
            summary.add(item.getVarietyCode() + " " + item.getVarietyName() + "："
                    + item.getActionLabel() + " " + plain(item.getSuggestAmount()) + " 元，"
                    + "触发网格线 " + plain(item.getCrossedLine()) + " 元（" + item.getBasisLabel()
                    + " " + plain(item.getCurrentPrice()) + " 元）");
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
        vo.setVarietyKey(plan.getVarietyKey());
        vo.setVarietyName(plan.getVarietyName());
        vo.setLowerBound(plan.getLowerBound());
        vo.setUpperBound(plan.getUpperBound());
        vo.setStep(plan.getStep());
        vo.setAmountPerGrid(plan.getAmountPerGrid());
        vo.setUsable(plan.complete());
        vo.setIssues(plan.getIssues());
        vo.setNotes(plan.getNotes());
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
     * 当前价、参考价与来源说明，不可判断时 blocked 写明原因
     */
    private record Price(BigDecimal current, LocalDate date, String source, BigDecimal previous,
                         LocalDate previousDate, String blocked) {

        /**
         * 只需当前价的场景，参考价与 blocked 单独给出
         *
         * @param current 当前价
         * @param date    价格日期
         * @param source  来源说明
         * @param blocked 不可判断原因，可为 null
         */
        private Price(BigDecimal current, LocalDate date, String source, String blocked) {
            this(current, date, source, null, null, blocked);
        }
    }
}

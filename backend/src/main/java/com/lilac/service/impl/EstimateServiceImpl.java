package com.lilac.service.impl;

import com.lilac.adapter.model.ForexRate;
import com.lilac.adapter.model.MarketBar;
import com.lilac.adapter.model.MarketHistory;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.domain.dto.estimate.EstimateBatchRequest;
import com.lilac.domain.dto.estimate.EstimateErrorQueryRequest;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.entity.FundCatalog;
import com.lilac.domain.vo.EstimateBatchVO;
import com.lilac.domain.vo.EstimateErrorVO;
import com.lilac.domain.vo.EstimateTrackVO;
import com.lilac.domain.vo.EstimateVO;
import com.lilac.domain.vo.FundSnapshotVO;
import com.lilac.domain.vo.HoldingItemVO;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.engine.BenchmarkResolver;
import com.lilac.engine.BondBaselineEstimator;
import com.lilac.engine.EstimateCache;
import com.lilac.engine.EstimateConverter;
import com.lilac.engine.EstimateEngine;
import com.lilac.engine.EstimateThresholds;
import com.lilac.engine.EstimateTrackStore;
import com.lilac.engine.GoldBetaEstimator;
import com.lilac.engine.HoldingWeightCalculator;
import com.lilac.engine.model.EstimateContext;
import com.lilac.engine.model.EstimateOutcome;
import com.lilac.engine.model.EstimateTrack;
import com.lilac.enums.EstimateBranch;
import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import com.lilac.service.EstimateErrorService;
import com.lilac.service.EstimateService;
import com.lilac.service.EstimateSnapshotService;
import com.lilac.service.FundCatalogService;
import com.lilac.service.FundNetWorthService;
import com.lilac.service.FundSnapshotService;
import com.lilac.service.HoldingService;
import com.lilac.service.VarietyService;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 预估业务实现，负责装配取数、批量行情、汇率与历史误差，再交给引擎逐只计算
 */
@Slf4j
@Service
public class EstimateServiceImpl implements EstimateService {

    private static final int HISTORY_LOOKBACK_DAYS = 150;
    private static final int GOLD_HISTORY_LIMIT = 90;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int RESULT_SCALE = 4;

    @Resource
    private EstimateEngine estimateEngine;

    @Resource
    private EstimateConverter estimateConverter;

    @Resource
    private EstimateThresholds thresholds;

    @Resource
    private BenchmarkResolver benchmarkResolver;

    @Resource
    private GoldBetaEstimator goldBetaEstimator;

    @Resource
    private BondBaselineEstimator bondBaselineEstimator;

    @Resource
    private EstimateCache estimateCache;

    @Resource
    private EstimateTrackStore estimateTrackStore;

    @Resource
    private HoldingService holdingService;

    @Resource
    private VarietyService varietyService;

    @Resource
    private FundCatalogService fundCatalogService;

    @Resource
    private FundSnapshotService fundSnapshotService;

    @Resource
    private FundNetWorthService fundNetWorthService;

    @Resource
    private EstimateErrorService estimateErrorService;

    @Resource
    private EstimateSnapshotService estimateSnapshotService;

    @Resource
    private MarketQuoteAdapter marketQuoteAdapter;

    /**
     * 按持仓批量预估当日涨跌，可预估与不可预估分两组，不可预估的不参与整体统计
     *
     * @param request 预估请求
     * @return 预估总览
     */
    @Override
    public EstimateBatchVO estimateBatch(EstimateBatchRequest request) {
        EstimateBatchRequest query = request == null ? new EstimateBatchRequest() : request;
        LocalDate today = LocalDate.now();
        boolean refresh = Boolean.TRUE.equals(query.getRefresh());
        if (!refresh) {
            Optional<EstimateBatchVO> cached = estimateCache.read(today);
            if (cached.isPresent()) {
                EstimateBatchVO hit = cached.get();
                // 命中缓存也要记轨迹，否则一个交易时点内只有第一次请求留下点
                recordTrack(hit, today);
                return hit;
            }
        }
        List<String> varietyKeys = resolveVarietyKeys(query);
        ThrowUtils.throwIf(varietyKeys.isEmpty(), HttpsCodeEnum.NOT_FOUND_ERROR,
                "尚未录入持仓，请先录入品种与持仓");
        EstimateBatchVO result = estimateInChunks(varietyKeys, today);
        // 留存与缓存都按「行情所属交易日」记账，否则开盘前跑一次会拿昨日行情覆盖昨日留档
        LocalDate dataDate = result.getDataDate() == null ? today : result.getDataDate();
        retainSnapshots(result, dataDate);
        estimateCache.save(result, dataDate);
        recordTrack(result, dataDate);
        return result;
    }

    /**
     * 读取今日整体预估的盘中轨迹，供行情条画曲线
     *
     * @param tradeDate 交易日，为空时取今天
     * @return 轨迹
     */
    @Override
    public EstimateTrackVO track(LocalDate tradeDate) {
        LocalDate date = tradeDate == null ? LocalDate.now() : tradeDate;
        EstimateTrack track = estimateTrackStore.read(date);
        EstimateTrackVO vo = new EstimateTrackVO();
        vo.setTradeDate(date);
        vo.setPoints(track.getPoints());
        vo.setPointCount(track.getPoints() == null ? 0 : track.getPoints().size());
        if (track.getPoints() == null || track.getPoints().size() < 2) {
            vo.setNote("轨迹从今日首次预估开始记录，满 2 个采样点后才会画出走势");
        }
        return vo;
    }

    /**
     * 记录一个轨迹采样点，失败只告警不影响预估本身
     *
     * @param result    预估总览
     * @param tradeDate 交易日
     */
    private void recordTrack(EstimateBatchVO result, LocalDate tradeDate) {
        estimateTrackStore.record(tradeDate, result.getOverallChange(), result.getEstimableCount());
    }

    /**
     * 把本次输出的逐只结论留存，供晚间校准逐只对照
     *
     * @param result    预估总览
     * @param tradeDate 预估所属交易日
     */
    private void retainSnapshots(EstimateBatchVO result, LocalDate tradeDate) {
        List<EstimateVO> items = new ArrayList<>();
        if (result.getItems() != null) {
            items.addAll(result.getItems());
        }
        if (result.getUnavailableItems() != null) {
            items.addAll(result.getUnavailableItems());
        }
        try {
            estimateSnapshotService.saveBatch(items, tradeDate);
        } catch (RuntimeException e) {
            // 留存失败只是让晚间校准缺一条记录，不能让预估本身失败
            log.warn("当日预估留存失败 日期={}：{}", tradeDate, e.getMessage());
        }
    }

    /**
     * 预估单只基金，不做缓存，供详情页与排障直接调用
     *
     * @param varietyKey 品种业务主键
     * @return 单只预估结果
     */
    @Override
    public EstimateVO estimateOne(String varietyKey) {
        ThrowUtils.throwIf(varietyKey == null || varietyKey.isBlank(),
                HttpsCodeEnum.PARAMS_ERROR, "品种业务主键不能为空");
        VarietyVO variety = findVariety(varietyKey);
        ThrowUtils.throwIf(variety == null, HttpsCodeEnum.NOT_FOUND_ERROR, "未找到品种 " + varietyKey);
        List<EstimateOutcome> outcomes = estimateFunds(List.of(variety), LocalDate.now());
        return estimateConverter.toVO(outcomes.get(0));
    }

    /**
     * 分批预估并汇总，超过上限时按批处理，绝不静默丢弃任何一只
     *
     * @param varietyKeys 品种业务主键
     * @param today       取数当日
     * @return 预估总览
     */
    private EstimateBatchVO estimateInChunks(List<String> varietyKeys, LocalDate today) {
        int size = thresholds.getMaxItems() <= 0 ? 30 : thresholds.getMaxItems();
        List<EstimateOutcome> outcomes = new ArrayList<>(varietyKeys.size());
        for (int from = 0; from < varietyKeys.size(); from += size) {
            List<String> chunk = varietyKeys.subList(from, Math.min(from + size, varietyKeys.size()));
            outcomes.addAll(estimateKeys(chunk, today));
        }
        EstimateBatchVO vo = assemble(outcomes, varietyKeys.size());
        if (varietyKeys.size() > size) {
            vo.setNotice("持仓 " + varietyKeys.size() + " 只已超过单批上限 " + size
                    + " 只，已分 " + ((varietyKeys.size() + size - 1) / size) + " 批处理，未丢弃任何一只");
        }
        return vo;
    }

    /**
     * 按品种主键分批取品种信息并预估
     *
     * @param varietyKeys 品种业务主键
     * @param today       取数当日
     * @return 预估结果
     */
    private List<EstimateOutcome> estimateKeys(List<String> varietyKeys, LocalDate today) {
        List<VarietyVO> varieties = new ArrayList<>(varietyKeys.size());
        for (String key : varietyKeys) {
            VarietyVO variety = findVariety(key);
            if (variety != null) {
                varieties.add(variety);
            }
        }
        return estimateFunds(varieties, today);
    }

    /**
     * 逐只装配上下文并预估，第一步先把所有标的行情一次批量取回
     *
     * @param varieties 品种列表
     * @param today     取数当日
     * @return 预估结果
     */
    private List<EstimateOutcome> estimateFunds(List<VarietyVO> varieties, LocalDate today) {
        if (varieties.isEmpty()) {
            return List.of();
        }
        List<Prepared> prepared = varieties.stream().map(variety -> prepare(variety, today)).toList();
        Map<String, MarketQuote> quotes = fetchQuotes(prepared);
        Map<String, MarketHistory> proxyHistories = fetchProxyHistories(prepared);
        ForexRate forex = fetchForexIfNeeded(quotes, prepared);
        List<EstimateOutcome> outcomes = new ArrayList<>(prepared.size());
        for (Prepared item : prepared) {
            outcomes.add(estimateOne(item, quotes, proxyHistories, forex, today));
        }
        return outcomes;
    }

    /**
     * 装配单只基金的分支、代理标的与持仓，这一步只做本地与基金侧取数
     *
     * @param variety 品种
     * @param today   取数当日
     * @return 中间状态
     */
    private Prepared prepare(VarietyVO variety, LocalDate today) {
        Prepared prepared = new Prepared();
        prepared.variety = variety;
        FundCatalog catalog = fundCatalogService.findByCode(variety.getVarietyCode());
        prepared.rawFundType = catalog == null ? null : catalog.getFundType();
        prepared.proxySymbol = benchmarkResolver.resolve(variety, prepared.rawFundType);
        prepared.branch = preliminaryBranch(variety, prepared.proxySymbol, prepared.rawFundType);
        // 快照只为带出净值日期、持仓截止日与降级标记，取不到不影响指数类分支出数
        loadSnapshot(prepared, today);
        prepared.history = loadHistory(variety.getVarietyKey());
        prepared.previousTier = resolvePreviousTier(prepared.history);
        prepared.hkHoldings = hasHongKong(prepared.snapshot);
        // 黄金与债券都要拿基金自己的净值序列去配对代理日线，两条分支共用同一段取数
        if (prepared.branch == EstimateBranch.GOLD_PROXY || prepared.branch == EstimateBranch.BOND_PROXY) {
            prepared.netWorthPoints = fundNetWorthService.listRange(variety.getVarietyCode(),
                    today.minusDays(HISTORY_LOOKBACK_DAYS * 2), null);
        }
        return prepared;
    }

    /**
     * 预判分支，与引擎内部规则保持一致，用于决定要不要取持仓
     *
     * @param variety     品种
     * @param proxySymbol 代理标的
     * @param rawFundType 原始类型
     * @return 分支
     */
    private EstimateBranch preliminaryBranch(VarietyVO variety, String proxySymbol, String rawFundType) {
        FundCategory category = variety.getCategory();
        if (category == null || category == FundCategory.UNKNOWN) {
            return EstimateBranch.UNAVAILABLE;
        }
        if (category == FundCategory.GOLD) {
            return EstimateBranch.GOLD_PROXY;
        }
        if (category == FundCategory.BOND) {
            return EstimateBranch.BOND_PROXY;
        }
        if (variety.getVarietyType() == com.lilac.enums.VarietyType.ETF) {
            return EstimateBranch.INDEX_ONLY;
        }
        String rawType = rawFundType == null ? "" : rawFundType;
        // ETF 联接的季报只有那只 ETF 与现金，个股加权必判覆盖率不足，与引擎同样改走指数直取
        boolean linkFund = variety.getVarietyName() != null && variety.getVarietyName().contains("联接");
        boolean dividendByIndex = category == FundCategory.DIVIDEND && linkFund;
        if (rawType.startsWith("指数型") && (category != FundCategory.DIVIDEND || dividendByIndex)
                && proxySymbol != null) {
            return EstimateBranch.INDEX_ONLY;
        }
        return EstimateBranch.STOCK_WEIGHTED;
    }

    /**
     * 取净值与持仓，失败只记日志，由引擎判为不可预估
     *
     * @param prepared 中间状态
     * @param today    取数当日
     */
    private void loadSnapshot(Prepared prepared, LocalDate today) {
        try {
            FundSnapshotVO snapshot = fundSnapshotService.load(prepared.variety.getVarietyCode(), today);
            // 上游返回空对象等同于取不到，不能让后续判空逻辑漏判
            prepared.snapshot = snapshot;
            prepared.degraded = snapshot != null && Boolean.TRUE.equals(snapshot.getDegraded());
        } catch (BusinessException e) {
            // 单只基金取不到净值与持仓不能拖垮整批，交给引擎说出原因
            log.warn("净值与持仓取数失败 code={}：{}", prepared.variety.getVarietyCode(), e.getMessage());
        }
    }

    /**
     * 取历史误差，取不到时按无历史处理
     *
     * @param varietyKey 品种业务主键
     * @return 误差记录，按交易日升序
     */
    private List<EstimateErrorVO> loadHistory(String varietyKey) {
        EstimateErrorQueryRequest query = new EstimateErrorQueryRequest();
        query.setVarietyKey(varietyKey);
        query.setStartDate(LocalDate.now().minusDays(HISTORY_LOOKBACK_DAYS));
        query.setEndDate(LocalDate.now());
        try {
            return estimateErrorService.listEstimateError(query);
        } catch (BusinessException e) {
            log.warn("误差历史查询失败 varietyKey={}：{}", varietyKey, e.getMessage());
            return List.of();
        }
    }

    /**
     * 从最近一条误差记录取回当时档位，用于档位滞回
     *
     * @param history 误差记录
     * @return 档位，无记录返回 null
     */
    private EstimateTier resolvePreviousTier(List<EstimateErrorVO> history) {
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
     * 判断快照持仓里是否含港股，港股代码为 5 位
     *
     * @param snapshot 净值与持仓
     * @return 含港股返回 true
     */
    private boolean hasHongKong(FundSnapshotVO snapshot) {
        if (snapshot == null || snapshot.getStockHoldings() == null) {
            return false;
        }
        return snapshot.getStockHoldings().stream()
                .map(HoldingItemVO::getCode)
                .filter(code -> code != null && !code.isBlank())
                .anyMatch(code -> code.trim().length() == 5);
    }

    /**
     * 把本次需要的全部标的去重后一次批量取行情
     *
     * @param prepared 中间状态列表
     * @return 带前缀完整代码到行情的映射
     */
    private Map<String, MarketQuote> fetchQuotes(List<Prepared> prepared) {
        LinkedHashSet<String> symbols = new LinkedHashSet<>();
        for (Prepared item : prepared) {
            if (item.proxySymbol != null) {
                symbols.add(item.proxySymbol);
            }
            if (item.snapshot != null && item.snapshot.getStockHoldings() != null) {
                item.snapshot.getStockHoldings().stream()
                        .map(holding -> HoldingWeightCalculator.symbolOf(holding.getCode()))
                        .filter(symbol -> !symbol.isBlank())
                        .forEach(symbols::add);
            }
        }
        if (symbols.isEmpty()) {
            return Map.of();
        }
        try {
            return marketQuoteAdapter.quotes(new ArrayList<>(symbols));
        } catch (BusinessException e) {
            log.warn("批量行情取数失败，整批按行情取不到处理：{}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 取代理标的日线，供黄金 β 与债券传导系数的滚动估计
     *
     * @param prepared 中间状态列表
     * @return 代理代码到日线的映射
     */
    private Map<String, MarketHistory> fetchProxyHistories(List<Prepared> prepared) {
        Map<String, MarketHistory> histories = new LinkedHashMap<>();
        prepared.stream()
                .filter(item -> item.proxySymbol != null
                        && (item.branch == EstimateBranch.GOLD_PROXY
                        || item.branch == EstimateBranch.BOND_PROXY))
                .map(item -> item.proxySymbol)
                .distinct()
                .forEach(symbol -> histories.put(symbol, marketQuoteAdapter.history(symbol, GOLD_HISTORY_LIMIT)));
        return histories;
    }

    /**
     * 有港股持仓时才取汇率，汇率失败不阻断预估，只是折算按不变处理
     *
     * @param quotes   行情集合
     * @param prepared 中间状态列表
     * @return 汇率，无港股时为 null
     */
    private ForexRate fetchForexIfNeeded(Map<String, MarketQuote> quotes, List<Prepared> prepared) {
        boolean needForex = prepared.stream().anyMatch(item -> item.hkHoldings);
        if (!needForex) {
            return null;
        }
        try {
            return marketQuoteAdapter.hkdCny();
        } catch (BusinessException e) {
            log.warn("汇率取数失败，港股按汇率不变处理：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 组装单只基金的上下文交给引擎
     *
     * @param prepared       中间状态
     * @param quotes         全部行情
     * @param proxyHistories 代理标的日线
     * @param forex          汇率
     * @param today          取数当日
     * @return 预估结果
     */
    private EstimateOutcome estimateOne(Prepared prepared, Map<String, MarketQuote> quotes,
                                        Map<String, MarketHistory> proxyHistories, ForexRate forex,
                                        LocalDate today) {
        EstimateContext context = new EstimateContext();
        context.setVariety(prepared.variety);
        context.setRawFundType(prepared.rawFundType);
        context.setSnapshot(prepared.snapshot);
        context.setProxySymbol(prepared.proxySymbol);
        context.setHoldingQuotes(holdingQuotes(prepared.snapshot, quotes));
        context.setForex(forex);
        context.setNetWorthPoints(prepared.netWorthPoints);
        context.setHistory(prepared.history);
        context.setPreviousTier(prepared.previousTier);
        context.setHkHoldings(prepared.hkHoldings);
        context.setDegraded(prepared.degraded);
        fillProxy(prepared, context, quotes);
        fillProxyModel(prepared, context, proxyHistories);
        return estimateEngine.estimate(context);
    }

    /**
     * 填充代理标的行情与不可用原因
     *
     * @param prepared 中间状态
     * @param context  上下文
     * @param quotes   全部行情
     */
    private void fillProxy(Prepared prepared, EstimateContext context, Map<String, MarketQuote> quotes) {
        if (prepared.proxySymbol == null) {
            context.setProxyUnavailableReason("未解析到对标指数，请确认该基金的跟踪标的");
            return;
        }
        MarketQuote quote = findQuote(quotes, prepared.proxySymbol);
        context.setProxyQuote(quote);
        if (quote == null) {
            context.setProxyUnavailableReason("代理标的 " + prepared.proxySymbol + " 无行情返回");
        } else if (!quote.getSymbol().symbol().equals(prepared.proxySymbol)) {
            // 补查命中另一市场时，结果里带出实际命中的标的，便于核对
            context.setProxySymbol(quote.getSymbol().symbol());
        } else if (!quote.usable()) {
            context.setProxyUnavailableReason(quote.getUnavailableReason());
        }
    }

    /**
     * 先按带前缀代码精确取，取不到再按数字代码匹配，兜住 000300 这类补查另一市场的标的
     *
     * @param quotes 全部行情
     * @param symbol 代理标的完整代码
     * @return 行情，取不到返回 null
     */
    private MarketQuote findQuote(Map<String, MarketQuote> quotes, String symbol) {
        MarketQuote exact = quotes.get(symbol);
        if (exact != null) {
            return exact;
        }
        String code = symbol.length() > 2 ? symbol.substring(2) : symbol;
        return quotes.values().stream()
                .filter(quote -> quote.getSymbol() != null && code.equals(quote.getSymbol().getCode()))
                .findFirst().orElse(null);
    }

    /**
     * 黄金按滚动窗口估 β 与 ρ，债券估自身对代理的传导系数，样本不足都退化为直取代理
     *
     * @param prepared       中间状态
     * @param context        上下文
     * @param proxyHistories 代理日线
     */
    private void fillProxyModel(Prepared prepared, EstimateContext context,
                                Map<String, MarketHistory> proxyHistories) {
        if (prepared.proxySymbol == null) {
            return;
        }
        MarketHistory history = proxyHistories.get(prepared.proxySymbol);
        if (history == null || history.getBars() == null) {
            return;
        }
        List<MarketBar> bars = history.getBars();
        if (prepared.branch == EstimateBranch.GOLD_PROXY) {
            context.setGoldModel(goldBetaEstimator.estimate(prepared.netWorthPoints, bars,
                    thresholds.getGoldWindow(), thresholds.getGoldMinPairs()));
            return;
        }
        if (prepared.branch == EstimateBranch.BOND_PROXY) {
            context.setBondModel(bondBaselineEstimator.estimate(prepared.netWorthPoints, bars,
                    thresholds.getBondWindow(), thresholds.getBondMinPairs()));
        }
    }

    /**
     * 从全量行情里挑出该基金持仓对应的部分
     *
     * @param snapshot 净值与持仓
     * @param quotes   全部行情
     * @return 持仓标的行情
     */
    private Map<String, MarketQuote> holdingQuotes(FundSnapshotVO snapshot, Map<String, MarketQuote> quotes) {
        if (snapshot == null || snapshot.getStockHoldings() == null || quotes.isEmpty()) {
            return Map.of();
        }
        Map<String, MarketQuote> owned = new LinkedHashMap<>();
        for (HoldingItemVO holding : snapshot.getStockHoldings()) {
            String symbol = HoldingWeightCalculator.symbolOf(holding.getCode());
            if (symbol.isBlank()) {
                continue;
            }
            MarketQuote quote = quotes.get(symbol);
            if (quote != null) {
                owned.put(symbol, quote);
            }
        }
        return owned;
    }

    /**
     * 汇总成总览，整体涨跌只统计 A 档，B 档不给数字也就不参与平均
     *
     * @param outcomes  预估结果
     * @param requested 请求的基金数
     * @return 总览
     */
    private EstimateBatchVO assemble(List<EstimateOutcome> outcomes, int requested) {
        List<EstimateVO> items = new ArrayList<>();
        List<EstimateVO> unavailable = new ArrayList<>();
        BigDecimal sum = ZERO;
        int counted = 0;
        LocalDate dataDate = null;
        boolean degraded = false;
        boolean realtime = false;
        boolean tierBRest = false;
        for (EstimateOutcome outcome : outcomes) {
            EstimateVO vo = estimateConverter.toVO(outcome);
            if (Boolean.TRUE.equals(vo.getEstimable())) {
                items.add(vo);
            } else {
                unavailable.add(vo);
            }
            if (vo.getEstimatedChange() != null) {
                sum = sum.add(vo.getEstimatedChange());
                counted++;
            }
            if (vo.getTier() == EstimateTier.B && Boolean.TRUE.equals(vo.getEstimable())) {
                tierBRest = true;
            }
            if (vo.getQuoteDate() != null && (dataDate == null || vo.getQuoteDate().isAfter(dataDate))) {
                dataDate = vo.getQuoteDate();
            }
            degraded = degraded || Boolean.TRUE.equals(vo.getDegraded());
            realtime = realtime || Boolean.TRUE.equals(vo.getRealtime());
        }
        items.sort((left, right) -> compareChange(right.getEstimatedChange(), left.getEstimatedChange()));
        EstimateBatchVO vo = new EstimateBatchVO();
        vo.setItems(items);
        vo.setUnavailableItems(unavailable);
        vo.setRequested(requested);
        vo.setEstimableCount(items.size());
        vo.setUnavailableCount(unavailable.size());
        vo.setOverallCount(counted);
        vo.setOverallChange(counted == 0 ? null : sum.divide(BigDecimal.valueOf(counted), RESULT_SCALE, RoundingMode.HALF_UP));
        vo.setDataDate(dataDate);
        vo.setTradingHours(realtime);
        vo.setDegraded(degraded);
        vo.setMixedCount((int) items.stream()
                .filter(item -> item.getCategory() == FundCategory.MIXED).count());
        // 整体涨跌只含 A 档，有 B 档时要说明它们没有计入，避免数字被误读成全部持仓
        if (tierBRest) {
            vo.setOverallNote("整体涨跌只统计点估计（A 档）基金；方向区间（B 档）基金不参与平均");
        }
        log.info("预估完成 共 {} 只 可预估 {} 只 不可预估 {} 只 数据日期={}",
                requested, items.size(), unavailable.size(), dataDate);
        return vo;
    }

    /**
     * 按预估涨跌幅排序，无数字的排最后
     *
     * @param left  左值
     * @param right 右值
     * @return 比较结果
     */
    private int compareChange(BigDecimal left, BigDecimal right) {
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
     * 解析本次要预估的品种主键，未指定时取全部持有中的持仓
     *
     * @param request 预估请求
     * @return 去重后的品种主键
     */
    private List<String> resolveVarietyKeys(EstimateBatchRequest request) {
        if (request.getVarietyKeys() != null && !request.getVarietyKeys().isEmpty()) {
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            request.getVarietyKeys().stream()
                    .filter(key -> key != null && !key.isBlank())
                    .map(String::trim)
                    .forEach(keys::add);
            ThrowUtils.throwIf(keys.isEmpty(), HttpsCodeEnum.PARAMS_ERROR, "品种业务主键列表不能全为空");
            return new ArrayList<>(keys);
        }
        HoldingQueryRequest holdingQuery = new HoldingQueryRequest();
        holdingQuery.setStatus(HoldingStatus.HOLDING);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (HoldingVO holding : holdingService.listHolding(holdingQuery)) {
            if (holding.getVarietyKey() != null && !holding.getVarietyKey().isBlank()) {
                keys.add(holding.getVarietyKey());
            }
        }
        return new ArrayList<>(keys);
    }

    /**
     * 单只基金取数过程中的中间状态，避免在多处重复查询
     */
    private static class Prepared {

        private VarietyVO variety;
        private String rawFundType;
        private String proxySymbol;
        private EstimateBranch branch;
        private FundSnapshotVO snapshot;
        private List<EstimateErrorVO> history;
        private EstimateTier previousTier;
        private List<FundNetWorthPoint> netWorthPoints;
        private boolean hkHoldings;
        private boolean degraded;
    }
}

package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.engine.DividendCalculator;
import com.lilac.domain.dto.holding.HoldingBatchUpdateRequest;
import com.lilac.domain.dto.holding.HoldingBuyRequest;
import com.lilac.domain.dto.holding.HoldingChangeQueryRequest;
import com.lilac.domain.dto.holding.HoldingChangeRevokeRequest;
import com.lilac.domain.dto.holding.HoldingConvertRequest;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.holding.HoldingSellRequest;
import com.lilac.domain.entity.Holding;
import com.lilac.domain.entity.HoldingChange;
import com.lilac.domain.entity.Variety;
import com.lilac.domain.vo.HoldingBatchVO;
import com.lilac.domain.vo.HoldingChangeVO;
import com.lilac.domain.vo.HoldingConvertVO;
import com.lilac.domain.vo.HoldingDividendVO;
import com.lilac.domain.vo.HoldingOverviewVO;
import com.lilac.domain.vo.HoldingSummaryVO;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.HoldingVarietyVO;
import com.lilac.enums.DividendMethod;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingChangeType;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.VarietyType;
import com.lilac.exception.BusinessException;
import com.lilac.mapper.HoldingChangeMapper;
import com.lilac.mapper.HoldingMapper;
import com.lilac.mapper.VarietyMapper;
import com.lilac.service.FundNetWorthService;
import com.lilac.service.FundSnapshotService;
import com.lilac.service.HoldingService;
import com.lilac.utils.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持仓业务实现，买入校验、卖出先进先出、批次更正撤销与变动历史留痕
 */
@Slf4j
@Service
public class HoldingServiceImpl implements HoldingService {

    private static final int MAX_VARIETIES = 30;
    private static final int DEFAULT_CHANGE_LIMIT = 100;
    private static final int NET_WORTH_LOOKBACK_DAYS = 30;
    private static final int MONEY_SCALE = 2;
    private static final int PRICE_SCALE = 4;
    private static final int RATE_SCALE = 2;

    /**
     * 按需拉取净值的节流时长，分钟
     */
    private static final long SYNC_THROTTLE_MINUTES = 30;

    /**
     * 净值公布的期望时刻，过了该时刻交易日当日净值应已可用
     */
    private static final LocalTime NET_WORTH_EXPECTED_TIME = LocalTime.of(20, 0);

    /**
     * 按需拉取的节流表，基金代码到下次允许拉取的时间
     */
    private final Map<String, LocalDateTime> syncThrottle = new ConcurrentHashMap<>();

    @Resource
    private HoldingMapper holdingMapper;

    @Resource
    private HoldingChangeMapper holdingChangeMapper;

    @Resource
    private VarietyMapper varietyMapper;

    @Resource
    private FundNetWorthService fundNetWorthService;

    @Resource
    private FundSnapshotService fundSnapshotService;

    @Resource
    private MarketQuoteAdapter marketQuoteAdapter;

    @Resource
    private DividendCalculator dividendCalculator;

    /**
     * 统一查询未删除的持仓
     *
     * @param request 查询请求
     * @return 持仓视图列表
     */
    @Override
    public List<HoldingVO> listHolding(HoldingQueryRequest request) {
        HoldingQueryRequest query = request == null ? new HoldingQueryRequest() : request;
        List<Holding> rows = holdingMapper.selectListByQuery(QueryWrapper.create()
                .eq(Holding::getVarietyKey, query.getVarietyKey(), query.getVarietyKey() != null)
                .eq(Holding::getStatus, query.getStatus(), query.getStatus() != null));
        return BeanUtil.copyToList(rows, HoldingVO.class);
    }

    /**
     * 买入入库并写入变动历史，校验失败抛 BusinessException
     *
     * @param request 买入请求
     * @return 新增的持仓
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public HoldingVO buy(HoldingBuyRequest request) {
        return doBuy(request, null);
    }

    /**
     * 买入的实际实现，自带备注时用它替换默认的身份回显文案
     *
     * @param request 买入请求
     * @param note    变动留痕的备注，为空时写身份回显
     * @return 新增的持仓
     */
    private HoldingVO doBuy(HoldingBuyRequest request, String note) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        String varietyKey = trimKey(request.getVarietyKey());
        ThrowUtils.throwIf(varietyKey == null, HttpsCodeEnum.PARAMS_ERROR, "品种业务主键不能为空");
        ThrowUtils.throwIf(request.getShares() == null || request.getShares().signum() <= 0,
                HttpsCodeEnum.PARAMS_ERROR, "份额必须为正数");
        ThrowUtils.throwIf(request.getCostPrice() == null || request.getCostPrice().signum() <= 0,
                HttpsCodeEnum.PARAMS_ERROR, "成本价必须为正数，为 0 无法计算盈亏");

        Variety variety = varietyMapper.selectOneByQuery(QueryWrapper.create()
                .eq(Variety::getVarietyKey, varietyKey));
        ThrowUtils.throwIf(variety == null, HttpsCodeEnum.PARAMS_ERROR,
                "品种 " + varietyKey + " 尚未注册，请先通过代码消歧确认身份");
        ThrowUtils.throwIf(!Boolean.TRUE.equals(variety.getConfirmed()), HttpsCodeEnum.PARAMS_ERROR,
                "品种 " + varietyKey + " 类型待确认，请先指定类型再录入持仓");

        // 上限按「有持有中持仓的品种数」计算，已清仓的品种不占额度
        List<Holding> allHolding = holdingMapper.selectListByQuery(QueryWrapper.create()
                .eq(Holding::getStatus, HoldingStatus.HOLDING));
        long holdingVarieties = allHolding.stream().map(Holding::getVarietyKey).distinct().count();
        boolean alreadyHeld = allHolding.stream().anyMatch(holding -> varietyKey.equals(holding.getVarietyKey()));
        if (!alreadyHeld && holdingVarieties >= MAX_VARIETIES) {
            throw new BusinessException(HttpsCodeEnum.PARAMS_ERROR,
                    "持仓已达 " + MAX_VARIETIES + " 只上限，请先清仓或分批处理");
        }

        Holding holding = new Holding();
        holding.setVarietyKey(varietyKey);
        holding.setShares(request.getShares());
        holding.setCostPrice(request.getCostPrice());
        holding.setOpenDate(request.getOpenDate() == null ? LocalDate.now() : request.getOpenDate());
        holding.setStatus(HoldingStatus.HOLDING);
        holdingMapper.insertSelective(holding);

        BigDecimal after = totalShares(varietyKey);
        String changeNote = note != null ? note
                : "身份回显：" + variety.getVarietyName() + "（" + variety.getVarietyCode() + "）";
        saveChange(varietyKey, HoldingChangeType.BUY, request.getShares(), request.getCostPrice(), after,
                holding.getOpenDate(), changeNote, holding.getId());
        log.info("买入入库 varietyKey={} shares={} cost={}", varietyKey, request.getShares(), request.getCostPrice());
        return BeanUtil.copyProperties(holding, HoldingVO.class);
    }

    /**
     * 卖出按先进先出扣减持仓，扣完置为已清仓并逐笔留痕
     *
     * @param request 卖出请求
     * @return 变动后该品种的剩余持仓
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<HoldingVO> sell(HoldingSellRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        String varietyKey = trimKey(request.getVarietyKey());
        ThrowUtils.throwIf(varietyKey == null, HttpsCodeEnum.PARAMS_ERROR, "品种业务主键不能为空");
        ThrowUtils.throwIf(request.getShares() == null || request.getShares().signum() <= 0,
                HttpsCodeEnum.PARAMS_ERROR, "卖出份额必须为正数");

        List<Holding> holdings = holdingMapper.selectListByQuery(QueryWrapper.create()
                .eq(Holding::getVarietyKey, varietyKey)
                .eq(Holding::getStatus, HoldingStatus.HOLDING)
                .orderBy(Holding::getOpenDate, true)
                .orderBy(Holding::getId, true));
        BigDecimal available = holdings.stream()
                .map(Holding::getShares)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ThrowUtils.throwIf(available.signum() <= 0, HttpsCodeEnum.PARAMS_ERROR,
                "品种 " + varietyKey + " 没有持有中的持仓，无法卖出");
        ThrowUtils.throwIf(request.getShares().compareTo(available) > 0, HttpsCodeEnum.PARAMS_ERROR,
                "卖出份额超过当前持有 " + plain(available) + " 份");

        // 先进先出：按建仓日期从早到晚逐条扣减
        BigDecimal remaining = request.getShares();
        List<String> deductNotes = new ArrayList<>();
        BigDecimal weightedCost = BigDecimal.ZERO;
        for (Holding holding : holdings) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal take = holding.getShares().min(remaining);
            weightedCost = weightedCost.add(take.multiply(holding.getCostPrice()));
            deductNotes.add(plain(take) + " 份（" + plain(holding.getCostPrice()) + " 元/份，建仓 "
                    + holding.getOpenDate() + "）");
            BigDecimal left = holding.getShares().subtract(take);
            remaining = remaining.subtract(take);
            if (left.signum() == 0) {
                holding.setShares(left);
                holding.setStatus(HoldingStatus.CLOSED);
            } else {
                holding.setShares(left);
            }
            holdingMapper.update(holding);
        }

        BigDecimal avgPrice = weightedCost.divide(request.getShares(), PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal after = totalShares(varietyKey);
        saveChange(varietyKey, HoldingChangeType.SELL, request.getShares(), avgPrice, after,
                LocalDate.now(),
                "按先进先出扣减 " + String.join("、", deductNotes), null);
        log.info("卖出扣减 varietyKey={} shares={} 均价={}", varietyKey, request.getShares(), avgPrice);
        // 只返回仍持有中的记录，已清仓的留在历史里
        return listHolding(byVariety(varietyKey)).stream()
                .filter(holding -> holding.getStatus() == HoldingStatus.HOLDING)
                .toList();
    }

    /**
     * 更正某个持仓批次的份额、成本价与建仓日期，供录入出错时改成正确值
     *
     * @param request 更正请求
     * @return 更正后的持仓
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public HoldingVO updateBatch(HoldingBatchUpdateRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, HttpsCodeEnum.PARAMS_ERROR,
                "持仓批次主键不能为空");
        Holding holding = holdingMapper.selectOneById(request.getId());
        ThrowUtils.throwIf(holding == null, HttpsCodeEnum.NOT_FOUND_ERROR,
                "持仓批次 " + request.getId() + " 不存在或已撤销");
        ThrowUtils.throwIf(request.getShares() == null || request.getShares().signum() <= 0,
                HttpsCodeEnum.PARAMS_ERROR, "份额必须为正数");
        ThrowUtils.throwIf(request.getCostPrice() == null || request.getCostPrice().signum() <= 0,
                HttpsCodeEnum.PARAMS_ERROR, "成本价必须为正数，为 0 无法计算盈亏");
        BigDecimal sold = soldShares(holding);
        ThrowUtils.throwIf(request.getShares().compareTo(sold) < 0, HttpsCodeEnum.PARAMS_ERROR,
                "该批次已卖出 " + plain(sold) + " 份，份额不能改到比已卖出的还少");

        BigDecimal oldShares = holding.getShares();
        BigDecimal oldCost = holding.getCostPrice();
        LocalDate oldOpenDate = holding.getOpenDate();
        holding.setShares(request.getShares());
        holding.setCostPrice(request.getCostPrice());
        if (request.getOpenDate() != null) {
            holding.setOpenDate(request.getOpenDate());
        }
        holdingMapper.update(holding);
        // 变动历史是审计记录，不就地改旧行，只追加一条更正说明把前后值写清楚
        saveChange(holding.getVarietyKey(), HoldingChangeType.BUY, request.getShares(), request.getCostPrice(),
                totalShares(holding.getVarietyKey()), holding.getOpenDate(),
                "更正批次 " + holding.getId() + "：" + plain(oldShares) + " 份 @ " + plain(oldCost)
                        + " 元/份（建仓 " + oldOpenDate + "）→ " + plain(request.getShares()) + " 份 @ "
                        + plain(request.getCostPrice()) + " 元/份（建仓 " + holding.getOpenDate() + "）",
                holding.getId());
        log.info("持仓批次已更正 id={} key={} shares={} cost={}", holding.getId(), holding.getVarietyKey(),
                request.getShares(), request.getCostPrice());
        return BeanUtil.copyProperties(holding, HoldingVO.class);
    }

    /**
     * 撤销某个持仓批次，未卖出时直接作废，已卖出则拒绝并提示改为更正
     *
     * @param request 撤销请求
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeBatch(HoldingChangeRevokeRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, HttpsCodeEnum.PARAMS_ERROR,
                "持仓批次主键不能为空");
        Holding holding = holdingMapper.selectOneById(request.getId());
        ThrowUtils.throwIf(holding == null, HttpsCodeEnum.NOT_FOUND_ERROR,
                "持仓批次 " + request.getId() + " 不存在或已撤销");
        BigDecimal sold = soldShares(holding);
        ThrowUtils.throwIf(sold.signum() > 0, HttpsCodeEnum.PARAMS_ERROR,
                "该批次已卖出 " + plain(sold) + " 份，撤销会让卖出口径对不上，请改为更正份额");
        // 逻辑删除，变动历史与误差历史都保留，事后仍可追溯这笔录入
        holdingMapper.deleteById(holding.getId());
        saveChange(holding.getVarietyKey(), HoldingChangeType.SELL, holding.getShares(), holding.getCostPrice(),
                totalShares(holding.getVarietyKey()), holding.getOpenDate(),
                "撤销录入错误的批次 " + holding.getId() + "（" + plain(holding.getShares()) + " 份 @ "
                        + plain(holding.getCostPrice()) + " 元/份，建仓 " + holding.getOpenDate() + "）",
                holding.getId());
        log.info("持仓批次已撤销 id={} key={}", holding.getId(), holding.getVarietyKey());
        return true;
    }

    /**
     * 基金转换，同一事务内先按先进先出卖出、再买入转入品种，任一步失败整笔回滚
     *
     * @param request 转换请求
     * @return 转出与转入的变动后状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public HoldingConvertVO convert(HoldingConvertRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        String fromKey = trimKey(request.getFromVarietyKey());
        String toKey = trimKey(request.getToVarietyKey());
        ThrowUtils.throwIf(fromKey == null || toKey == null, HttpsCodeEnum.PARAMS_ERROR,
                "转出与转入品种都必须指定");
        // 同一只基金自己转自己没有意义，且会让 FIFO 扣减与新增互相干扰
        ThrowUtils.throwIf(fromKey.equals(toKey), HttpsCodeEnum.PARAMS_ERROR,
                "转出与转入不能是同一只基金");
        ThrowUtils.throwIf(request.getShares() == null || request.getShares().signum() <= 0,
                HttpsCodeEnum.PARAMS_ERROR, "转出份额必须为正数");
        ThrowUtils.throwIf(request.getToShares() == null || request.getToShares().signum() <= 0,
                HttpsCodeEnum.PARAMS_ERROR, "转入份额必须为正数");
        ThrowUtils.throwIf(request.getToCostPrice() == null || request.getToCostPrice().signum() <= 0,
                HttpsCodeEnum.PARAMS_ERROR, "转入成本价必须为正数");

        Variety toVariety = varietyMapper.selectOneByQuery(QueryWrapper.create()
                .eq(Variety::getVarietyKey, toKey));
        ThrowUtils.throwIf(toVariety == null, HttpsCodeEnum.PARAMS_ERROR,
                "品种 " + toKey + " 尚未注册，请先通过代码消歧确认身份");
        ThrowUtils.throwIf(!Boolean.TRUE.equals(toVariety.getConfirmed()), HttpsCodeEnum.PARAMS_ERROR,
                "品种 " + toKey + " 类型待确认，请先指定类型再转入");

        HoldingSellRequest sellRequest = new HoldingSellRequest();
        sellRequest.setVarietyKey(fromKey);
        sellRequest.setShares(request.getShares());
        sell(sellRequest);

        HoldingBuyRequest buyRequest = new HoldingBuyRequest();
        buyRequest.setVarietyKey(toKey);
        buyRequest.setShares(request.getToShares());
        buyRequest.setCostPrice(request.getToCostPrice());
        buyRequest.setOpenDate(request.getOpenDate());
        // 转换只留一条买入痕迹，备注写明来源，读历史时能一眼看出这笔是转换来的
        doBuy(buyRequest, "基金转换转入：由 " + fromKey + " 转出 " + plain(request.getShares()) + " 份");

        HoldingConvertVO vo = new HoldingConvertVO();
        vo.setFromVariety(rowOf(fromKey));
        vo.setToVariety(rowOf(toKey));
        vo.setOutCount(1);
        vo.setInCount(1);
        vo.setNote("已从 " + fromKey + " 转出 " + plain(request.getShares()) + " 份，转入 "
                + toVariety.getVarietyName() + " " + plain(request.getToShares()) + " 份");
        log.info("基金转换 from={} to={} out={} in={}", fromKey, toKey,
                request.getShares(), request.getToShares());
        return vo;
    }

    /**
     * 取单个品种的总览行，供转换结果回显
     *
     * @param varietyKey 品种业务主键
     * @return 聚合行，无持有中持仓时只带出份额为 0 的空行
     */
    private HoldingVarietyVO rowOf(String varietyKey) {
        List<Holding> holdings = holdingMapper.selectListByQuery(QueryWrapper.create()
                .eq(Holding::getVarietyKey, varietyKey)
                .eq(Holding::getStatus, HoldingStatus.HOLDING));
        HoldingVarietyVO row = new HoldingVarietyVO();
        row.setVarietyKey(varietyKey);
        row.setTotalShares(holdings.stream().map(Holding::getShares)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (holdings.isEmpty()) {
            row.setStatus(HoldingStatus.CLOSED);
            Variety variety = varietyMapper.selectOneByQuery(QueryWrapper.create()
                    .eq(Variety::getVarietyKey, varietyKey));
            if (variety != null) {
                row.setVarietyName(variety.getVarietyName());
                row.setVarietyCode(variety.getVarietyCode());
            }
            return row;
        }
        return buildVarietyRow(varietyKey, holdings);
    }

    /**
     * 查询变动历史并带出品种名称与本次金额，按时间倒序
     *
     * @param request 查询请求
     * @return 变动历史列表
     */
    @Override
    public List<HoldingChangeVO> listChanges(HoldingChangeQueryRequest request) {
        HoldingChangeQueryRequest query = request == null ? new HoldingChangeQueryRequest() : request;
        int limit = query.getLimit() == null || query.getLimit() <= 0 ? DEFAULT_CHANGE_LIMIT : query.getLimit();
        List<HoldingChange> rows = holdingChangeMapper.selectListByQuery(QueryWrapper.create()
                .eq(HoldingChange::getVarietyKey, query.getVarietyKey(), query.getVarietyKey() != null)
                .eq(HoldingChange::getChangeType, query.getChangeType(), query.getChangeType() != null)
                .orderBy(HoldingChange::getId, false)
                .limit(limit));
        List<HoldingChangeVO> result = BeanUtil.copyToList(rows, HoldingChangeVO.class);
        fillVarietyNames(result);
        return result;
    }

    /**
     * 持仓总览，按品种聚合、带出名称与最新价并汇总市值与当日收益
     *
     * @return 总览视图
     */
    @Override
    public HoldingOverviewVO overview() {
        List<Holding> holdings = holdingMapper.selectListByQuery(QueryWrapper.create()
                .eq(Holding::getStatus, HoldingStatus.HOLDING)
                .orderBy(Holding::getVarietyKey, true));
        Map<String, List<Holding>> grouped = new LinkedHashMap<>();
        for (Holding holding : holdings) {
            grouped.computeIfAbsent(holding.getVarietyKey(), key -> new ArrayList<>()).add(holding);
        }

        List<HoldingVarietyVO> items = new ArrayList<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalDayPnl = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalCostAmount = BigDecimal.ZERO;
        int missingPrice = 0;
        int missingDayPnl = 0;
        for (Map.Entry<String, List<Holding>> entry : grouped.entrySet()) {
            HoldingVarietyVO item = buildVarietyRow(entry.getKey(), entry.getValue());
            if (item.getMarketValue() == null) {
                missingPrice++;
            } else {
                totalValue = totalValue.add(item.getMarketValue());
            }
            if (item.getDailyProfitLoss() == null) {
                missingDayPnl++;
            } else {
                totalDayPnl = totalDayPnl.add(item.getDailyProfitLoss());
            }
            if (item.getTotalProfitLoss() != null) {
                totalProfit = totalProfit.add(item.getTotalProfitLoss());
            }
            if (item.getCostAmount() != null) {
                totalCostAmount = totalCostAmount.add(item.getCostAmount());
            }
            items.add(item);
        }
        items.sort(Comparator.comparing(HoldingVarietyVO::getVarietyKey));

        HoldingOverviewVO overview = new HoldingOverviewVO();
        overview.setVarietyCount(items.size());
        overview.setItems(items);
        overview.setTotalMarketValue(items.isEmpty() ? BigDecimal.ZERO
                : totalValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        overview.setTotalDailyProfitLoss(totalDayPnl.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        overview.setTotalProfitLoss(totalProfit.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        overview.setTotalCostAmount(totalCostAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        overview.setTotalProfitRate(totalCostAmount.signum() == 0 ? BigDecimal.ZERO
                : totalProfit.multiply(BigDecimal.valueOf(100))
                        .divide(totalCostAmount, RATE_SCALE, RoundingMode.HALF_UP));
        List<String> notices = new ArrayList<>();
        if (missingPrice > 0) {
            notices.add(missingPrice + " 只品种暂无最新净值或行情，市值未计入合计");
        }
        if (missingDayPnl > 0) {
            notices.add(missingDayPnl + " 只品种缺当日涨跌幅，当日收益未计入合计");
        }
        if (!notices.isEmpty()) {
            overview.setNotice(String.join("；", notices));
        }
        return overview;
    }

    /**
     * 按品种汇总持有中份额与加权成本
     *
     * @param varietyKey 品种业务主键
     * @return 汇总视图
     */
    @Override
    public HoldingSummaryVO summaryOf(String varietyKey) {
        HoldingSummaryVO summary = new HoldingSummaryVO();
        List<Holding> holdings = holdingMapper.selectListByQuery(QueryWrapper.create()
                .eq(Holding::getVarietyKey, varietyKey)
                .eq(Holding::getStatus, HoldingStatus.HOLDING));
        if (holdings.isEmpty()) {
            summary.setHolding(Boolean.FALSE);
            return summary;
        }
        summary.setHolding(Boolean.TRUE);
        BigDecimal shares = holdings.stream().map(Holding::getShares).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = holdings.stream()
                .map(holding -> holding.getShares().multiply(holding.getCostPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.setTotalShares(shares);
        summary.setAvgCostPrice(cost.divide(shares, PRICE_SCALE, RoundingMode.HALF_UP));
        return summary;
    }

    /**
     * 聚合单品种的总览行，带出名称、类型、最新价、当日收益与分红
     *
     * @param varietyKey 品种业务主键
     * @param holdings   该品种的持有中持仓
     * @return 聚合行
     */
    private HoldingVarietyVO buildVarietyRow(String varietyKey, List<Holding> holdings) {
        HoldingVarietyVO item = new HoldingVarietyVO();
        item.setVarietyKey(varietyKey);
        BigDecimal shares = holdings.stream().map(Holding::getShares).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = holdings.stream()
                .map(holding -> holding.getShares().multiply(holding.getCostPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        item.setTotalShares(shares);
        item.setAvgCostPrice(cost.divide(shares, PRICE_SCALE, RoundingMode.HALF_UP));
        item.setCostAmount(cost.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        item.setLastOpenDate(holdings.stream().map(Holding::getOpenDate)
                .max(LocalDate::compareTo).orElse(null));
        item.setStatus(HoldingStatus.HOLDING);
        item.setBatches(batchesOf(holdings));

        Variety variety = varietyMapper.selectOneByQuery(QueryWrapper.create()
                .eq(Variety::getVarietyKey, varietyKey));
        if (variety != null) {
            item.setVarietyType(variety.getVarietyType());
            item.setVarietyCode(variety.getVarietyCode());
            item.setVarietyName(variety.getVarietyName());
            item.setCategory(variety.getCategory());
            item.setCategoryLabel(variety.getCategory() == null ? FundCategory.UNKNOWN.getLabel()
                    : variety.getCategory().getLabel());
            item.setConfirmed(variety.getConfirmed());
            item.setDividendMethod(variety.getDividendMethod() == null
                    ? DividendMethod.CASH : variety.getDividendMethod());
        }
        LatestPrice price = latestPrice(variety);
        if (price != null) {
            item.setLatestPrice(price.value);
            item.setLatestPriceDate(price.date);
            item.setPriceSource(price.source);
            // 分红要先算出来，含分红的收益口径要用到它
            fillDividend(item, variety, holdings);
            BigDecimal marketValue = shares.multiply(price.value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal profitLoss = marketValue.subtract(cost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            item.setMarketValue(marketValue);
            item.setProfitLoss(profitLoss);
            item.setProfitRate(cost.signum() == 0 ? BigDecimal.ZERO
                    : profitLoss.multiply(BigDecimal.valueOf(100))
                            .divide(cost, RATE_SCALE, RoundingMode.HALF_UP));
            // 支付宝的「持有收益」含分红，单位净值除息后会掉，不加回分红会被读成亏损
            BigDecimal dividend = item.getDividendAmount() == null ? BigDecimal.ZERO : item.getDividendAmount();
            BigDecimal totalProfit = profitLoss.add(dividend).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            item.setTotalProfitLoss(totalProfit);
            item.setTotalProfitRate(cost.signum() == 0 ? BigDecimal.ZERO
                    : totalProfit.multiply(BigDecimal.valueOf(100))
                            .divide(cost, RATE_SCALE, RoundingMode.HALF_UP));
            item.setDailyChangePercent(price.changePercent);
            item.setDailyProfitLoss(dailyProfitLoss(marketValue, price.changePercent));
        }
        return item;
    }

    /**
     * 把持有中持仓转成批次视图，供页面逐条更正或撤销
     *
     * @param holdings 持有中持仓
     * @return 批次列表，按建仓日期升序
     */
    private List<HoldingBatchVO> batchesOf(List<Holding> holdings) {
        List<HoldingBatchVO> batches = new ArrayList<>(holdings.size());
        for (Holding holding : holdings) {
            HoldingBatchVO batch = new HoldingBatchVO();
            batch.setId(holding.getId());
            batch.setShares(holding.getShares());
            batch.setCostPrice(holding.getCostPrice());
            batch.setCostAmount(holding.getShares().multiply(holding.getCostPrice())
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            batch.setOpenDate(holding.getOpenDate());
            batch.setCreateTime(holding.getCreateTime());
            batches.add(batch);
        }
        batches.sort(Comparator.comparing(HoldingBatchVO::getOpenDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return batches;
    }

    /**
     * 按当日涨跌幅反推昨日市值，再与今日市值相减得到当日收益
     *
     * @param marketValue   今日市值
     * @param changePercent 当日涨跌幅，百分点
     * @return 当日收益金额，缺涨跌幅时为 null
     */
    private BigDecimal dailyProfitLoss(BigDecimal marketValue, BigDecimal changePercent) {
        if (marketValue == null || changePercent == null) {
            return null;
        }
        BigDecimal hundred = BigDecimal.valueOf(100);
        BigDecimal ratio = hundred.add(changePercent);
        if (ratio.signum() == 0) {
            return null;
        }
        BigDecimal previous = marketValue.multiply(hundred).divide(ratio, MONEY_SCALE, RoundingMode.HALF_UP);
        return marketValue.subtract(previous).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 推算持仓期间现金分红，逐笔明细同时带出供核对
     *
     * @param item     聚合行，原地补分红字段
     * @param variety  品种
     * @param holdings 该品种的持有中持仓
     */
    private void fillDividend(HoldingVarietyVO item, Variety variety, List<Holding> holdings) {
        if (variety == null || variety.getVarietyCode() == null) {
            return;
        }
        BigDecimal shares = item.getTotalShares();
        if (shares == null || shares.signum() <= 0) {
            return;
        }
        // 每个批次各自从自己的建仓日起算，早批能拿到它应得的分红，再合并成一行
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal perShareSum = BigDecimal.ZERO;
        // 每份分红是基金层面的全局值，同日多个批次必须去重，绝不能相加
        Map<LocalDate, BigDecimal> perShareByDate = new LinkedHashMap<>();
        // 同日实际拿到分红的份额，按批次累加，只有除息日已持有的批次才计入
        Map<LocalDate, BigDecimal> sharesByDate = new LinkedHashMap<>();
        for (Holding holding : holdings) {
            if (holding.getOpenDate() == null) {
                continue;
            }
            List<FundNetWorthPoint> history = fundNetWorthService.listRange(variety.getVarietyCode(),
                    holding.getOpenDate().minusDays(dividendCalculator.lookbackDays()), null);
            FundNetWorthPoint latest = latestPoint(history, item.getLatestPrice(), item.getLatestPriceDate());
            DividendCalculator.Result result = dividendCalculator.calculate(holding.getShares(),
                    holding.getOpenDate(), latest, history, item.getDividendMethod());
            if (result == null) {
                return;
            }
            total = total.add(result.amount());
            for (DividendCalculator.Distribution distribution : result.distributions()) {
                // 同一除息日只留一次每份金额，重复批次不再覆盖
                perShareByDate.putIfAbsent(distribution.date(), distribution.perShare());
                // 除息日当天买入的不享受该次分红，必须严格早于除息日才计入
                if (holding.getOpenDate().isBefore(distribution.date())) {
                    sharesByDate.merge(distribution.date(), holding.getShares(), BigDecimal::add);
                }
            }
        }
        List<HoldingDividendVO> details = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : perShareByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            HoldingDividendVO detail = new HoldingDividendVO();
            detail.setDate(entry.getKey());
            detail.setPerShare(entry.getValue().setScale(6, RoundingMode.HALF_UP));
            BigDecimal heldShares = sharesByDate.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            detail.setAmount(entry.getValue().multiply(heldShares).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            detail.setShares(heldShares.setScale(2, RoundingMode.HALF_UP));
            details.add(detail);
            perShareSum = perShareSum.add(entry.getValue());
        }
        LocalDate since = holdings.stream().map(Holding::getOpenDate)
                .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        item.setDividendAmount(total.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        item.setDividendDetails(details);
        item.setDividendNote(noteOfDividend(item.getDividendMethod(), since, details, perShareSum));
    }

    /**
     * 拼分红口径说明，红利再投与无除息两种情形必须各自说清
     *
     * @param method     分红方式
     * @param since      最早建仓日
     * @param details    逐笔明细
     * @param perShareSum 每份合计分红
     * @return 说明文字
     */
    private String noteOfDividend(DividendMethod method, LocalDate since, List<HoldingDividendVO> details,
                                  BigDecimal perShareSum) {
        if (method == DividendMethod.REINVEST) {
            return "分红方式为红利再投，分红已折成份额、不再单列现金；自 " + since + " 建仓以来共 "
                    + details.size() + " 次除息，每份合计 " + plain(perShareSum) + " 元";
        }
        if (details.isEmpty()) {
            return "自 " + since + " 建仓以来无除息记录";
        }
        return "自 " + since + " 建仓以来共 " + details.size() + " 次除息，每份合计 " + plain(perShareSum)
                + " 元，按当前份额折算，不含在浮盈亏内";
    }

    /**
     * 取最新净值点，库内有当期就用库内，否则用当次取到的最新价拼一个
     *
     * @param history   净值序列，可为空
     * @param price     最新价
     * @param priceDate 最新价日期
     * @return 最新净值点，取不到返回 null
     */
    private FundNetWorthPoint latestPoint(List<FundNetWorthPoint> history, BigDecimal price, LocalDate priceDate) {
        if (history == null || history.isEmpty()) {
            return price == null || priceDate == null ? null : new FundNetWorthPoint(priceDate, price, null, null);
        }
        FundNetWorthPoint last = history.get(history.size() - 1);
        if (price == null || priceDate == null || !priceDate.isAfter(last.getNetWorthDate())) {
            return last;
        }
        // 当日净值刚公布但库内还没落，用最新价补一个点，避免分红少算一天
        return new FundNetWorthPoint(priceDate, price, null, null);
    }

    /**
     * 取最新价，场内 ETF 用实时行情、其余用库内最新净值；库内从无净值时先按需拉一次上游
     *
     * @param variety 品种，可为 null
     * @return 最新价，取不到返回 null
     */
    private LatestPrice latestPrice(Variety variety) {
        if (variety == null || variety.getVarietyCode() == null) {
            return null;
        }
        if (variety.getVarietyType() == VarietyType.ETF) {
            try {
                MarketQuote quote = marketQuoteAdapter.quote(variety.getVarietyCode());
                if (quote != null && quote.getPrice() != null) {
                    String source = Boolean.TRUE.equals(quote.getRealtime()) ? "行情价（实时）" : "行情价（上一交易日）";
                    // 场内 ETF 没有单位净值口径，累计净值不参与分红推算
                    return new LatestPrice(quote.getPrice(), quote.getTradeDate(), source,
                            quote.getChangePercent(), null);
                }
            } catch (Exception e) {
                // 行情不可用不影响总览展示，回退净值
                log.warn("ETF 行情取数失败 code={}：{}", variety.getVarietyCode(), e.getMessage());
            }
        }
        List<FundNetWorthPoint> points = netWorthPoints(variety.getVarietyCode());
        for (int i = points.size() - 1; i >= 0; i--) {
            FundNetWorthPoint point = points.get(i);
            if (point.getUnitNetWorth() != null) {
                return new LatestPrice(point.getUnitNetWorth(), point.getNetWorthDate(),
                        "净值 " + point.getNetWorthDate(), point.getEquityReturn(),
                        point.getAccumulatedNetWorth());
            }
        }
        return null;
    }

    /**
     * 读库内近期净值；落后于应达日期时先按节流拉一次增量再读，避免长时间停机后市值停在旧日期
     *
     * @param fundCode 6 位基金代码
     * @return 净值序列，上游失败返回空列表
     */
    private List<FundNetWorthPoint> netWorthPoints(String fundCode) {
        LocalDate start = LocalDate.now().minusDays(NET_WORTH_LOOKBACK_DAYS);
        List<FundNetWorthPoint> points = fundNetWorthService.listRange(fundCode, start, null);
        // 库内没有净值是首次拉取，落后于应达日期是停机漏更，两者都触发节流刷新
        if (points.isEmpty() || isBehindSchedule(fundCode)) {
            refreshThrottled(fundCode);
            return fundNetWorthService.listRange(fundCode, start, null);
        }
        return points;
    }

    /**
     * 判断库内最新净值是否落后于应达日期，交易日 20 点后应达当日，其余时刻应达上一交易日
     *
     * @param fundCode 6 位基金代码
     * @return 落后返回 true
     */
    private boolean isBehindSchedule(String fundCode) {
        LocalDate latest = fundNetWorthService.latestDate(fundCode);
        if (latest == null) {
            return true;
        }
        return expectedNetWorthDate().isAfter(latest);
    }

    /**
     * 计算当前时刻净值应达的日期，非交易日与未过公布时刻时回退到更早的交易日
     *
     * @return 应达日期
     */
    private LocalDate expectedNetWorthDate() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate date = now.toLocalDate();
        // 净值一般晚于 20 点公布，之前只能期望上一交易日的数据
        if (!now.toLocalTime().isAfter(NET_WORTH_EXPECTED_TIME)) {
            date = date.minusDays(1);
        }
        // 周末与节假日没有净值，一路回退到最近的交易日
        while (date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                || date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            date = date.minusDays(1);
        }
        return date;
    }

    /**
     * 按节流刷新净值增量，同一基金 30 分钟最多拉一次，失败也不反复重试
     *
     * @param fundCode 6 位基金代码
     */
    private void refreshThrottled(String fundCode) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = syncThrottle.get(fundCode);
        if (next != null && next.isAfter(now)) {
            return;
        }
        syncThrottle.put(fundCode, now.plusMinutes(SYNC_THROTTLE_MINUTES));
        int added = fundSnapshotService.refreshNetWorth(fundCode);
        if (added >= 0) {
            log.info("持仓总览刷新净值 code={} 新增 {} 条", fundCode, added);
        }
        // 清掉过期节流项，避免长期驻留
        syncThrottle.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }

    /**
     * 为变动历史补品种名称与本次金额
     *
     * @param rows 变动历史视图
     */
    private void fillVarietyNames(List<HoldingChangeVO> rows) {
        if (rows.isEmpty()) {
            return;
        }
        for (HoldingChangeVO row : rows) {
            Variety variety = varietyMapper.selectOneByQuery(QueryWrapper.create()
                    .eq(Variety::getVarietyKey, row.getVarietyKey()));
            if (variety != null) {
                row.setVarietyName(variety.getVarietyName());
            }
            if (row.getShares() != null && row.getUnitPrice() != null) {
                row.setAmount(row.getShares().multiply(row.getUnitPrice())
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            }
        }
    }

    /**
     * 统计某个批次已被卖出的份额，已卖出则不允许撤销
     *
     * @param holding 持仓批次
     * @return 已卖出份额，无法判定时抛业务异常
     */
    private BigDecimal soldShares(Holding holding) {
        List<HoldingChange> changes = holdingChangeMapper.selectListByQuery(QueryWrapper.create()
                .eq(HoldingChange::getHoldingId, holding.getId())
                .eq(HoldingChange::getChangeType, HoldingChangeType.BUY)
                .orderBy(HoldingChange::getId, true));
        if (!changes.isEmpty() && changes.get(0).getShares() != null) {
            // 买入留痕的份额就是该批次最初的份额，卖出会就地扣减 holding.shares
            return changes.get(0).getShares().subtract(holding.getShares()).max(BigDecimal.ZERO);
        }
        // 老批次没有买入留痕：只有在该品种从未卖出过时才能确认这批没被扣减
        long sells = holdingChangeMapper.selectCountByQuery(QueryWrapper.create()
                .eq(HoldingChange::getVarietyKey, holding.getVarietyKey())
                .eq(HoldingChange::getChangeType, HoldingChangeType.SELL)
                .isNotNull(HoldingChange::getNote)
                .notLike(HoldingChange::getNote, "撤销录入错误的批次")
                .notLike(HoldingChange::getNote, "更正批次")
                .notLike(HoldingChange::getNote, "基金转换转入"));
        if (sells > 0) {
            throw new BusinessException(HttpsCodeEnum.PARAMS_ERROR,
                    "该品种已有卖出记录，但这一批次是早期录入、无法确认是否被扣减，请改用「更正」修改份额与成本");
        }
        return BigDecimal.ZERO;
    }

    /**
     * 写入一条变动历史
     *
     * @param varietyKey 品种业务主键
     * @param type       变动类型
     * @param shares     变动份额
     * @param unitPrice  成交单价
     * @param after      变动后合计份额
     * @param openDate   业务日期，买入为建仓日期，卖出为操作当日
     * @param note       备注
     * @param holdingId  对应的持仓批次主键，可为空
     */
    private void saveChange(String varietyKey, HoldingChangeType type, BigDecimal shares, BigDecimal unitPrice,
                            BigDecimal after, LocalDate openDate, String note, Long holdingId) {
        HoldingChange change = new HoldingChange();
        change.setVarietyKey(varietyKey);
        change.setChangeType(type);
        change.setShares(shares);
        change.setUnitPrice(unitPrice);
        change.setAfterShares(after);
        change.setOpenDate(openDate);
        change.setNote(note);
        change.setHoldingId(holdingId);
        holdingChangeMapper.insertSelective(change);
    }

    /**
     * 汇总某品种持有中份额
     *
     * @param varietyKey 品种业务主键
     * @return 合计份额
     */
    private BigDecimal totalShares(String varietyKey) {
        return holdingMapper.selectListByQuery(QueryWrapper.create()
                .eq(Holding::getVarietyKey, varietyKey)
                .eq(Holding::getStatus, HoldingStatus.HOLDING)).stream()
                .map(Holding::getShares)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 单品种查询条件
     *
     * @param varietyKey 品种业务主键
     * @return 查询请求
     */
    private HoldingQueryRequest byVariety(String varietyKey) {
        HoldingQueryRequest query = new HoldingQueryRequest();
        query.setVarietyKey(varietyKey);
        return query;
    }

    /**
     * 去空白并规范化品种主键
     *
     * @param varietyKey 原始主键
     * @return 规范化结果，空白返回 null
     */
    private String trimKey(String varietyKey) {
        if (varietyKey == null || varietyKey.isBlank()) {
            return null;
        }
        return varietyKey.trim();
    }

    /**
     * 数值转纯文本，去掉科学计数
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * 最新价的内部载体
     */
    private record LatestPrice(BigDecimal value, LocalDate date, String source, BigDecimal changePercent,
                               BigDecimal accumulated) {
    }
}

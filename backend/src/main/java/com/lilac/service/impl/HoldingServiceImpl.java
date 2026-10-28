package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.domain.dto.holding.HoldingBuyRequest;
import com.lilac.domain.dto.holding.HoldingChangeQueryRequest;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.holding.HoldingSellRequest;
import com.lilac.domain.entity.Holding;
import com.lilac.domain.entity.HoldingChange;
import com.lilac.domain.entity.Variety;
import com.lilac.domain.vo.HoldingChangeVO;
import com.lilac.domain.vo.HoldingOverviewVO;
import com.lilac.domain.vo.HoldingSummaryVO;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.HoldingVarietyVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingChangeType;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.VarietyType;
import com.lilac.mapper.HoldingChangeMapper;
import com.lilac.mapper.HoldingMapper;
import com.lilac.mapper.VarietyMapper;
import com.lilac.service.FundNetWorthService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 持仓业务实现，买入校验、卖出先进先出与变动历史留痕
 */
@Slf4j
@Service
public class HoldingServiceImpl implements HoldingService {

    private static final int MAX_VARIETIES = 30;
    private static final int DEFAULT_CHANGE_LIMIT = 100;
    private static final int NET_WORTH_LOOKBACK_DAYS = 30;
    private static final int MONEY_SCALE = 2;
    private static final int PRICE_SCALE = 4;

    @Resource
    private HoldingMapper holdingMapper;

    @Resource
    private HoldingChangeMapper holdingChangeMapper;

    @Resource
    private VarietyMapper varietyMapper;

    @Resource
    private FundNetWorthService fundNetWorthService;

    @Resource
    private MarketQuoteAdapter marketQuoteAdapter;

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
            throw new com.lilac.exception.BusinessException(HttpsCodeEnum.PARAMS_ERROR,
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
        saveChange(varietyKey, HoldingChangeType.BUY, request.getShares(), request.getCostPrice(), after,
                "身份回显：" + variety.getVarietyName() + "（" + variety.getVarietyCode() + "）");
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
                "按先进先出扣减 " + String.join("、", deductNotes));
        log.info("卖出扣减 varietyKey={} shares={} 均价={}", varietyKey, request.getShares(), avgPrice);
        // 只返回仍持有中的记录，已清仓的留在历史里
        return listHolding(byVariety(varietyKey)).stream()
                .filter(holding -> holding.getStatus() == HoldingStatus.HOLDING)
                .toList();
    }

    /**
     * 查询变动历史并带出品种名称，按时间倒序
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
     * 持仓总览，按品种聚合、带出名称与最新价并汇总市值
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
        int missingPrice = 0;
        for (Map.Entry<String, List<Holding>> entry : grouped.entrySet()) {
            HoldingVarietyVO item = buildVarietyRow(entry.getKey(), entry.getValue());
            if (item.getMarketValue() == null) {
                missingPrice++;
            } else {
                totalValue = totalValue.add(item.getMarketValue());
            }
            items.add(item);
        }
        items.sort(Comparator.comparing(HoldingVarietyVO::getVarietyKey));

        HoldingOverviewVO overview = new HoldingOverviewVO();
        overview.setVarietyCount(items.size());
        overview.setItems(items);
        overview.setTotalMarketValue(items.isEmpty() ? BigDecimal.ZERO
                : totalValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        if (missingPrice > 0) {
            overview.setNotice(missingPrice + " 只品种暂无最新净值或行情，市值未计入合计");
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
     * 聚合单品种的总览行，带出名称、类型与最新价
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
        item.setLastOpenDate(holdings.stream().map(Holding::getOpenDate)
                .max(LocalDate::compareTo).orElse(null));
        item.setStatus(HoldingStatus.HOLDING);

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
        }
        LatestPrice price = latestPrice(variety);
        if (price != null) {
            item.setLatestPrice(price.value);
            item.setLatestPriceDate(price.date);
            item.setPriceSource(price.source);
            item.setMarketValue(shares.multiply(price.value).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        }
        return item;
    }

    /**
     * 取最新价，场内 ETF 用实时行情、其余用库内最新净值
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
                    return new LatestPrice(quote.getPrice(), quote.getTradeDate(), source);
                }
            } catch (Exception e) {
                // 行情不可用不影响总览展示，回退净值
                log.warn("ETF 行情取数失败 code={}：{}", variety.getVarietyCode(), e.getMessage());
            }
        }
        LocalDate start = LocalDate.now().minusDays(NET_WORTH_LOOKBACK_DAYS);
        List<com.lilac.adapter.model.FundNetWorthPoint> points =
                fundNetWorthService.listRange(variety.getVarietyCode(), start, null);
        for (int i = points.size() - 1; i >= 0; i--) {
            com.lilac.adapter.model.FundNetWorthPoint point = points.get(i);
            if (point.getUnitNetWorth() != null) {
                return new LatestPrice(point.getUnitNetWorth(), point.getNetWorthDate(),
                        "净值 " + point.getNetWorthDate());
            }
        }
        return null;
    }

    /**
     * 为变动历史补品种名称
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
        }
    }

    /**
     * 写入一条变动历史
     *
     * @param varietyKey 品种业务主键
     * @param type       变动类型
     * @param shares     变动份额
     * @param unitPrice  成交单价
     * @param after      变动后合计份额
     * @param note       备注
     */
    private void saveChange(String varietyKey, HoldingChangeType type, BigDecimal shares, BigDecimal unitPrice,
                            BigDecimal after, String note) {
        HoldingChange change = new HoldingChange();
        change.setVarietyKey(varietyKey);
        change.setChangeType(type);
        change.setShares(shares);
        change.setUnitPrice(unitPrice);
        change.setAfterShares(after);
        change.setNote(note);
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
    private record LatestPrice(BigDecimal value, LocalDate date, String source) {
    }
}

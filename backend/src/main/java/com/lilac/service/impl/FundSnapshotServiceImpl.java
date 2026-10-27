package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.adapter.fund.FundCategoryResolver;
import com.lilac.adapter.fund.FundDisclosureAdapter;
import com.lilac.adapter.fund.FundHoldingAdapter;
import com.lilac.adapter.fund.FundPingzhongDataAdapter;
import com.lilac.adapter.fund.FundSnapshotCache;
import com.lilac.adapter.model.FundHoldingItem;
import com.lilac.adapter.model.FundHoldingSnapshot;
import com.lilac.adapter.model.FundPingzhongData;
import com.lilac.adapter.model.FundReportDisclosure;
import com.lilac.adapter.model.FundSnapshot;
import com.lilac.adapter.model.HoldingKind;
import com.lilac.domain.entity.FundCatalog;
import com.lilac.domain.vo.FundSnapshotVO;
import com.lilac.domain.vo.HoldingItemVO;
import com.lilac.domain.vo.NetWorthPointVO;
import com.lilac.enums.FundCategory;
import com.lilac.exception.BusinessException;
import com.lilac.service.FundCatalogService;
import com.lilac.service.FundNetWorthService;
import com.lilac.service.FundSnapshotService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 基金净值与持仓业务实现，负责编排三个接口并兜住上游结构变化
 */
@Slf4j
@Service
public class FundSnapshotServiceImpl implements FundSnapshotService {

    private static final String UNAVAILABLE_NO_NET_WORTH = "无历史净值，新基金暂无法预估";
    private static final String UNAVAILABLE_NO_HOLDING = "无已披露的股票持仓，暂无法预估";

    @Resource
    private FundPingzhongDataAdapter fundPingzhongDataAdapter;

    @Resource
    private FundHoldingAdapter fundHoldingAdapter;

    @Resource
    private FundDisclosureAdapter fundDisclosureAdapter;

    @Resource
    private FundSnapshotCache fundSnapshotCache;

    @Resource
    private FundNetWorthService fundNetWorthService;

    @Resource
    private FundCatalogService fundCatalogService;

    @Resource
    private FundCategoryResolver fundCategoryResolver;

    @Value("${fund.adapter.eastmoney.net-worth-history-days:400}")
    private int netWorthHistoryDays;

    /**
     * 取净值、持仓与三个日期，上游失败即回退最近一次成功快照并标记降级
     *
     * @param code  6 位基金代码
     * @param today 取数当日
     * @return 净值与持仓视图
     */
    @Override
    public FundSnapshotVO load(String code, LocalDate today) {
        try {
            FundSnapshot snapshot = fetch(code, today);
            fundSnapshotCache.save(snapshot);
            return toVO(snapshot, null);
        } catch (BusinessException e) {
            // 结构变化或断网时回退最近一次成功快照，让上层能明确说出数据截止时间
            Optional<FundSnapshot> cached = fundSnapshotCache.read(code);
            if (cached.isEmpty()) {
                throw e;
            }
            log.warn("上游取数失败，回退降级快照 code={}：{}", code, e.getMessage());
            return toVO(cached.get(), e.getMessage());
        }
    }

    /**
     * 拉取净值与持仓，持仓按基金类型决定取股票还是债券
     *
     * @param code  6 位基金代码
     * @param today 取数当日
     * @return 快照
     */
    private FundSnapshot fetch(String code, LocalDate today) {
        FundPingzhongData pingzhong = fundPingzhongDataAdapter.fetch(code);
        fundNetWorthService.saveIncremental(code, pingzhong.getNetWorthPoints());

        FundSnapshot snapshot = new FundSnapshot();
        snapshot.setCode(code);
        snapshot.setName(pingzhong.getName());
        snapshot.setNetWorthDate(pingzhong.latestNetWorthDate());
        snapshot.setAssetAllocation(pingzhong.latestAssetAllocation());
        snapshot.setDegraded(Boolean.FALSE);
        fillHoldings(snapshot, code, resolveHoldingKind(code), today);
        return snapshot;
    }

    /**
     * 按全集里的原始类型判断取股票还是债券明细，全集缺失时按股票处理
     *
     * @param code 6 位基金代码
     * @return 持仓类型
     */
    private HoldingKind resolveHoldingKind(String code) {
        FundCatalog catalog = fundCatalogService.findByCode(code);
        if (catalog == null) {
            return HoldingKind.STOCK;
        }
        FundCategory category = fundCategoryResolver.resolve(catalog.getFundType(), catalog.getFundName(), false);
        return category == FundCategory.BOND ? HoldingKind.BOND : HoldingKind.STOCK;
    }

    /**
     * 取最近一期已披露持仓，季报只有前十大、中报与年报才是全量
     *
     * @param snapshot 快照
     * @param code     6 位基金代码
     * @param kind     股票或债券
     * @param today    取数当日
     */
    private void fillHoldings(FundSnapshot snapshot, String code, HoldingKind kind, LocalDate today) {
        List<FundHoldingSnapshot> candidates = fundHoldingAdapter.fetchAll(code, kind, today);
        if (candidates.isEmpty()) {
            // 该基金确无此类持仓，明确标为空让上层说出不可预估，而不是编造空对象
            snapshot.setStockHoldings(List.of());
            snapshot.setBondHoldings(List.of());
            snapshot.setFullDisclosure(Boolean.FALSE);
            return;
        }
        FundHoldingSnapshot latest = candidates.get(candidates.size() - 1);
        if (HoldingKind.BOND == kind) {
            snapshot.setBondHoldings(latest.getItems());
            snapshot.setStockHoldings(List.of());
        } else {
            snapshot.setStockHoldings(latest.getItems());
            snapshot.setBondHoldings(List.of());
        }
        snapshot.setHoldingCutoffDate(latest.getCutoffDate());
        snapshot.setHoldingPeriod(latest.getPeriod());
        snapshot.setFullDisclosure(isFullDisclosure(latest.getPeriod()));
        snapshot.setHoldingDisclosureDate(resolveDisclosureDate(code, latest.getPeriod()));
    }

    /**
     * 查该报告期的披露日，查不到时返回 null 由上层提示
     *
     * @param code   6 位基金代码
     * @param period 报告期
     * @return 披露日
     */
    private LocalDate resolveDisclosureDate(String code, String period) {
        try {
            return fundDisclosureAdapter.fetchRegularReports(code).stream()
                    .filter(report -> report.getPeriod().equals(period))
                    .map(FundReportDisclosure::getPublishDate)
                    .findFirst()
                    .orElse(null);
        } catch (BusinessException e) {
            // 披露日只用于展示与可信度判断，取不到不影响净值与持仓本身
            log.warn("披露日获取失败 code={}：{}", code, e.getMessage());
            return null;
        }
    }

    /**
     * 二季度与四季度为全量披露，其余两季只有前十大
     *
     * @param period 报告期，如 2026Q2
     * @return 全量披露返回 true
     */
    private boolean isFullDisclosure(String period) {
        return period != null && (period.endsWith("Q2") || period.endsWith("Q4"));
    }

    /**
     * 组装对外视图，降级时带出数据截止时间与原因
     *
     * @param snapshot      快照
     * @param degradeReason 降级原因，未降级为 null
     * @return 视图
     */
    private FundSnapshotVO toVO(FundSnapshot snapshot, String degradeReason) {
        FundSnapshotVO vo = BeanUtil.copyProperties(snapshot, FundSnapshotVO.class, "stockHoldings", "bondHoldings");
        vo.setStockHoldings(toItemVOs(snapshot.getStockHoldings()));
        vo.setBondHoldings(toItemVOs(snapshot.getBondHoldings()));
        vo.setTotalStockWeight(snapshot.totalStockWeight());
        vo.setTotalBondWeight(snapshot.totalBondWeight());
        if (snapshot.getAssetAllocation() != null) {
            vo.setStockRatio(snapshot.getAssetAllocation().getStockRatio());
            vo.setBondRatio(snapshot.getAssetAllocation().getBondRatio());
            vo.setCashRatio(snapshot.getAssetAllocation().getCashRatio());
        }
        vo.setEstimable(snapshot.estimable());
        vo.setUnavailableReason(unavailableReason(snapshot));
        vo.setDegraded(degradeReason != null || Boolean.TRUE.equals(snapshot.getDegraded()));
        vo.setDataCutoffDate(degradeReason == null ? null : latestCutoff(snapshot));
        vo.setNetWorthPoints(toNetWorthVOs(snapshot.getCode()));
        return vo;
    }

    /**
     * 不可预估的原因，可预估时为 null
     *
     * @param snapshot 快照
     * @return 原因文案
     */
    private String unavailableReason(FundSnapshot snapshot) {
        if (snapshot.getNetWorthDate() == null) {
            return UNAVAILABLE_NO_NET_WORTH;
        }
        if (snapshot.getStockHoldings() == null || snapshot.getStockHoldings().isEmpty()) {
            return UNAVAILABLE_NO_HOLDING;
        }
        return null;
    }

    /**
     * 降级时的数据截止时间取净值日期与持仓截止日中较晚的一个
     *
     * @param snapshot 快照
     * @return 数据截止时间
     */
    private LocalDate latestCutoff(FundSnapshot snapshot) {
        LocalDate netWorthDate = snapshot.getNetWorthDate();
        LocalDate cutoff = snapshot.getHoldingCutoffDate();
        if (netWorthDate == null) {
            return cutoff;
        }
        if (cutoff == null) {
            return netWorthDate;
        }
        return netWorthDate.isAfter(cutoff) ? netWorthDate : cutoff;
    }

    /**
     * 明细转视图
     *
     * @param items 明细
     * @return 明细视图
     */
    private List<HoldingItemVO> toItemVOs(List<FundHoldingItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(item -> new HoldingItemVO(item.getCode(), item.getName(), item.getWeight())).toList();
    }

    /**
     * 取库内最近一段净值序列，供指标与页面复用
     *
     * @param code 6 位基金代码
     * @return 净值序列视图
     */
    private List<NetWorthPointVO> toNetWorthVOs(String code) {
        LocalDate start = LocalDate.now().minusDays(netWorthHistoryDays);
        return fundNetWorthService.listRange(code, start, null).stream()
                .map(point -> new NetWorthPointVO(point.getNetWorthDate(), point.getUnitNetWorth(),
                        point.getEquityReturn()))
                .toList();
    }
}

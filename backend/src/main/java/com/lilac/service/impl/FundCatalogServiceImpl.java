package com.lilac.service.impl;

import com.lilac.adapter.fund.FundCatalogAdapter;
import com.lilac.adapter.model.FundCatalogItem;
import com.lilac.domain.entity.FundCatalog;
import com.lilac.domain.vo.FundCatalogStatusVO;
import com.lilac.domain.vo.FundCatalogSyncVO;
import com.lilac.exception.BusinessException;
import com.lilac.mapper.FundCatalogMapper;
import com.lilac.service.FundCatalogService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 基金全集缓存业务实现，全量覆盖写入并记录同步时间
 */
@Slf4j
@Service
public class FundCatalogServiceImpl implements FundCatalogService {

    private static final int BATCH_SIZE = 1000;
    private static final int NAME_SEARCH_LIMIT = 20;
    private static final String ALWAYS_TRUE_CONDITION = "1 = 1";

    /**
     * 全集未命中代码时补同步的当日节流标记
     */
    private volatile LocalDate lastMissingCodeSyncDate;

    @Resource
    private FundCatalogMapper fundCatalogMapper;

    @Resource
    private FundCatalogAdapter fundCatalogAdapter;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Value("${fund.adapter.eastmoney.catalog-max-age-days:7}")
    private int maxAgeDays;

    /**
     * 从上游全量拉取并覆盖本地缓存，事务内先删后插保证不出现半截数据
     *
     * @return 同步结果
     */
    @Override
    public FundCatalogSyncVO sync() {
        long start = System.currentTimeMillis();
        // 取数放在事务外，上游失败时本地缓存完全不动
        List<FundCatalogItem> items = fundCatalogAdapter.fetchAll();
        transactionTemplate.executeWithoutResult(status -> replaceAll(items));
        FundCatalogSyncVO result = new FundCatalogSyncVO();
        result.setTotal((long) items.size());
        result.setSyncTime(LocalDateTime.now());
        result.setCostMs(System.currentTimeMillis() - start);
        log.info("基金全集缓存刷新完成，共 {} 条，耗时 {}ms", items.size(), result.getCostMs());
        return result;
    }

    /**
     * 缓存过期时同步，上游不可用时沿用旧缓存并告警
     *
     * @return 同步结果
     */
    @Override
    public FundCatalogSyncVO syncIfStale() {
        FundCatalogStatusVO current = status();
        if (!current.getStale()) {
            return cachedResult(current);
        }
        try {
            return sync();
        } catch (BusinessException e) {
            // 上游不可用时保留旧缓存，让消歧降级继续可用
            log.warn("基金全集同步失败，沿用旧缓存：{}", e.getMessage());
            return cachedResult(current);
        }
    }

    /**
     * 缓存未过期但查不到代码时补一次同步，当日已补过则跳过，用于兜住全集滞后的新发基金
     *
     * @return 本次是否真正执行了同步
     */
    @Override
    public boolean syncForMissingCode() {
        LocalDate today = LocalDate.now();
        if (today.equals(lastMissingCodeSyncDate)) {
            return false;
        }
        // 先占位再同步，并发请求同一天只放行一个
        lastMissingCodeSyncDate = today;
        try {
            sync();
            return true;
        } catch (BusinessException e) {
            // 同步失败释放占位，下次未命中还能再试
            lastMissingCodeSyncDate = null;
            log.warn("全集补同步失败，沿用旧缓存：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 查询缓存状态，以最新更新时间判断是否过期
     *
     * @return 缓存状态
     */
    @Override
    public FundCatalogStatusVO status() {
        Long total = fundCatalogMapper.selectCountByQuery(QueryWrapper.create());
        LocalDateTime syncTime = latestUpdateTime();
        FundCatalogStatusVO status = new FundCatalogStatusVO();
        status.setTotal(total);
        status.setSyncTime(syncTime);
        status.setMaxAgeDays(maxAgeDays);
        status.setStale(total == null || total == 0 || syncTime == null
                || syncTime.isBefore(LocalDateTime.now().minusDays(maxAgeDays)));
        return status;
    }

    /**
     * 按 6 位代码查缓存
     *
     * @param code 6 位基金代码
     * @return 缓存行，未命中返回 null
     */
    @Override
    public FundCatalog findByCode(String code) {
        return fundCatalogMapper.selectOneByQuery(QueryWrapper.create()
                .eq(FundCatalog::getFundCode, code));
    }

    /**
     * 按名称关键词模糊查缓存，按代码升序返回
     *
     * @param nameKeyword 名称关键词
     * @param limit       返回条数上限
     * @return 缓存行列表
     */
    @Override
    public List<FundCatalog> searchByName(String nameKeyword, int limit) {
        int size = limit <= 0 ? NAME_SEARCH_LIMIT : limit;
        return fundCatalogMapper.selectListByQuery(QueryWrapper.create()
                .like(FundCatalog::getFundName, nameKeyword)
                .orderBy(FundCatalog::getFundCode, true)
                .limit(size));
    }

    /**
     * 全量替换缓存表，必须在事务内调用
     *
     * @param items 全集数组
     */
    private void replaceAll(List<FundCatalogItem> items) {
        // 框架禁止无条件删除，显式写恒真条件，语义仍是清空重建
        fundCatalogMapper.deleteByQuery(QueryWrapper.create().where(ALWAYS_TRUE_CONDITION));
        for (int from = 0; from < items.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, items.size());
            List<FundCatalog> rows = items.subList(from, to).stream().map(this::toEntity).toList();
            fundCatalogMapper.insertBatchSelective(rows);
        }
    }

    /**
     * 用当前缓存状态拼同步结果，表示本次未真正刷新
     *
     * @param current 缓存状态
     * @return 同步结果
     */
    private FundCatalogSyncVO cachedResult(FundCatalogStatusVO current) {
        FundCatalogSyncVO cached = new FundCatalogSyncVO();
        cached.setTotal(current.getTotal());
        cached.setSyncTime(current.getSyncTime());
        cached.setCostMs(0L);
        return cached;
    }

    /**
     * 取缓存中最新一条更新时间，作为数据集时间
     *
     * @return 最近同步时间，空缓存返回 null
     */
    private LocalDateTime latestUpdateTime() {
        FundCatalog latest = fundCatalogMapper.selectOneByQuery(QueryWrapper.create()
                .orderBy(FundCatalog::getUpdateTime, false)
                .limit(1));
        return latest == null ? null : latest.getUpdateTime();
    }

    /**
     * 全集行转缓存实体
     *
     * @param item 全集行
     * @return 缓存实体
     */
    private FundCatalog toEntity(FundCatalogItem item) {
        FundCatalog entity = new FundCatalog();
        entity.setFundCode(item.getCode());
        entity.setFundName(item.getName());
        entity.setFundType(item.getType());
        return entity;
    }
}

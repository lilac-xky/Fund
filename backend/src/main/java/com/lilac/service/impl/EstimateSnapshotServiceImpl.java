package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.domain.entity.EstimateSnapshot;
import com.lilac.domain.vo.EstimateSnapshotVO;
import com.lilac.domain.vo.EstimateVO;
import com.lilac.mapper.EstimateSnapshotMapper;
import com.lilac.service.EstimateSnapshotService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 当日预估留存业务实现，按「基金 + 交易日」存在则覆盖，避免当日重跑留下多行
 */
@Slf4j
@Service
public class EstimateSnapshotServiceImpl implements EstimateSnapshotService {

    @Resource
    private EstimateSnapshotMapper estimateSnapshotMapper;

    /**
     * 留存一批预估结果，同一只基金同一天存在则覆盖
     *
     * @param items     预估结果
     * @param tradeDate 预估所属交易日
     * @return 实际写入条数
     */
    @Override
    public int saveBatch(List<EstimateVO> items, LocalDate tradeDate) {
        if (items == null || items.isEmpty() || tradeDate == null) {
            return 0;
        }
        Map<String, Long> existing = existingIds(tradeDate);
        int written = 0;
        for (EstimateVO item : items) {
            if (item == null || item.getVarietyKey() == null || item.getVarietyKey().isBlank()) {
                continue;
            }
            EstimateSnapshot entity = toEntity(item, tradeDate);
            Long id = existing.get(item.getVarietyKey());
            if (id == null) {
                written += estimateSnapshotMapper.insertSelective(entity);
                continue;
            }
            // 当日重跑要覆盖旧结论，否则校准会对着一份过期输出做判定
            entity.setId(id);
            written += estimateSnapshotMapper.update(entity);
        }
        log.info("预估留存完成 日期={} 共 {} 条", tradeDate, written);
        return written;
    }

    /**
     * 取指定交易日的全部留存，按品种业务主键索引
     *
     * @param tradeDate 交易日
     * @return 留存视图列表
     */
    @Override
    public List<EstimateSnapshotVO> listByDate(LocalDate tradeDate) {
        List<EstimateSnapshot> rows = estimateSnapshotMapper.selectListByQuery(QueryWrapper.create()
                .eq(EstimateSnapshot::getTradeDate, tradeDate)
                .orderBy(EstimateSnapshot::getVarietyKey, true));
        return BeanUtil.copyToList(rows, EstimateSnapshotVO.class);
    }

    /**
     * 取该交易日已存在的留存主键，用于判断新增还是覆盖
     *
     * @param tradeDate 交易日
     * @return 品种业务主键到主键的映射
     */
    private Map<String, Long> existingIds(LocalDate tradeDate) {
        Map<String, Long> existing = new LinkedHashMap<>();
        estimateSnapshotMapper.selectListByQuery(QueryWrapper.create()
                        .select(EstimateSnapshot::getId, EstimateSnapshot::getVarietyKey)
                        .eq(EstimateSnapshot::getTradeDate, tradeDate))
                .forEach(row -> existing.put(row.getVarietyKey(), row.getId()));
        return existing;
    }

    /**
     * 预估结果转留存实体
     *
     * @param item      预估结果
     * @param tradeDate 预估所属交易日
     * @return 留存实体
     */
    private EstimateSnapshot toEntity(EstimateVO item, LocalDate tradeDate) {
        EstimateSnapshot entity = new EstimateSnapshot();
        entity.setVarietyKey(item.getVarietyKey());
        entity.setTradeDate(tradeDate);
        entity.setCategory(item.getCategory() == null ? null : item.getCategory().name());
        entity.setBranch(item.getBranch() == null ? null : item.getBranch().name());
        entity.setOutputTier(item.getTier() == null ? null : item.getTier().name());
        entity.setEstimatedChange(item.getEstimatedChange());
        entity.setDirection(item.getDirection() == null ? null : item.getDirection().name());
        entity.setIntervalLow(item.getIntervalLow());
        entity.setIntervalHigh(item.getIntervalHigh());
        entity.setCoverage(item.getCoverage());
        entity.setEvaluable(Boolean.TRUE.equals(item.getEstimable()));
        entity.setUnavailableReason(item.getUnavailableReason());
        entity.setHoldingPeriod(item.getHoldingPeriod());
        entity.setHoldingCutoffDate(item.getHoldingCutoffDate());
        entity.setHoldingDisclosureDate(item.getHoldingDisclosureDate());
        entity.setBeta(item.getBeta());
        entity.setRho(item.getRho());
        entity.setResidualP90(item.getResidualP90());
        entity.setQuoteDate(item.getQuoteDate());
        entity.setDataCutoffDate(item.getQuoteDate() == null ? item.getNetWorthDate() : item.getQuoteDate());
        return entity;
    }
}

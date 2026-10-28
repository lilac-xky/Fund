package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.domain.entity.EstimateGoldBeta;
import com.lilac.domain.vo.GoldBetaRecordVO;
import com.lilac.mapper.EstimateGoldBetaMapper;
import com.lilac.service.EstimateGoldBetaService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 黄金 β 记录业务实现，同一天重跑只更新不新增
 */
@Slf4j
@Service
public class EstimateGoldBetaServiceImpl implements EstimateGoldBetaService {

    @Resource
    private EstimateGoldBetaMapper estimateGoldBetaMapper;

    /**
     * 按「基金 + 交易日」存在则更新、不存在则新增
     *
     * @param record 记录
     * @return 是否成功
     */
    @Override
    public boolean save(GoldBetaRecordVO record) {
        if (record == null || record.getVarietyKey() == null || record.getTradeDate() == null
                || record.getBeta() == null) {
            return false;
        }
        EstimateGoldBeta entity = BeanUtil.copyProperties(record, EstimateGoldBeta.class);
        EstimateGoldBeta existing = estimateGoldBetaMapper.selectOneByQuery(QueryWrapper.create()
                .eq(EstimateGoldBeta::getVarietyKey, record.getVarietyKey())
                .eq(EstimateGoldBeta::getTradeDate, record.getTradeDate()));
        if (existing == null) {
            return estimateGoldBetaMapper.insertSelective(entity) > 0;
        }
        // 沿用已有行主键，否则会撞上「基金 + 交易日」唯一索引
        entity.setId(existing.getId());
        return estimateGoldBetaMapper.update(entity) > 0;
    }

    /**
     * 查询黄金 β 记录，条件为空时不参与过滤
     *
     * @param varietyKey 品种业务主键，可为 null
     * @param startDate  起始日，可为 null
     * @param endDate    截止日，可为 null
     * @return 记录列表，按交易日升序
     */
    @Override
    public List<GoldBetaRecordVO> list(String varietyKey, LocalDate startDate, LocalDate endDate) {
        boolean hasRange = startDate != null && endDate != null;
        List<EstimateGoldBeta> rows = estimateGoldBetaMapper.selectListByQuery(QueryWrapper.create()
                .eq(EstimateGoldBeta::getVarietyKey, varietyKey, varietyKey != null)
                .between(EstimateGoldBeta::getTradeDate, startDate, endDate, hasRange)
                .orderBy(EstimateGoldBeta::getTradeDate, true));
        return BeanUtil.copyToList(rows, GoldBetaRecordVO.class);
    }

    /**
     * 取某只基金最近一条 β 记录
     *
     * @param varietyKey 品种业务主键
     * @return 记录，无记录返回 null
     */
    @Override
    public GoldBetaRecordVO latest(String varietyKey) {
        List<GoldBetaRecordVO> rows = list(varietyKey, null, null);
        return rows.isEmpty() ? null : rows.get(rows.size() - 1);
    }
}

package com.lilac.engine;

import com.lilac.engine.model.EstimateOutcome;
import com.lilac.domain.vo.EstimateVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 预估结果转视图，只做字段搬移与中文名补齐，不做任何计算
 */
@Slf4j
@Component
public class EstimateConverter {

    /**
     * 结果转视图
     *
     * @param outcome 预估结果
     * @return 视图
     */
    public EstimateVO toVO(EstimateOutcome outcome) {
        EstimateVO vo = new EstimateVO();
        vo.setVarietyKey(outcome.getVarietyKey());
        vo.setVarietyCode(outcome.getVarietyCode());
        vo.setVarietyName(outcome.getVarietyName());
        vo.setCategory(outcome.getCategory());
        vo.setCategoryLabel(outcome.getCategory() == null ? null : outcome.getCategory().getLabel());
        vo.setBranch(outcome.getBranch());
        vo.setBranchLabel(outcome.getBranch() == null ? null : outcome.getBranch().getLabel());
        vo.setTier(outcome.getTier());
        vo.setTierLabel(outcome.getTier() == null ? null : outcome.getTier().getLabel());
        vo.setConfidence(outcome.getConfidence());
        vo.setConfidenceLabel(outcome.getConfidence() == null ? null : outcome.getConfidence().getLabel());
        vo.setEstimatedChange(outcome.getEstimatedChange());
        vo.setDirection(outcome.getDirection());
        vo.setDirectionLabel(outcome.getDirection() == null ? null : outcome.getDirection().getLabel());
        vo.setIntervalLow(outcome.getIntervalLow());
        vo.setIntervalHigh(outcome.getIntervalHigh());
        vo.setCoverage(outcome.getCoverage());
        vo.setHoldingPeriod(outcome.getHoldingPeriod());
        vo.setHoldingCutoffDate(outcome.getHoldingCutoffDate());
        vo.setHoldingDisclosureDate(outcome.getHoldingDisclosureDate());
        vo.setNetWorthDate(outcome.getNetWorthDate());
        vo.setQuoteDate(outcome.getQuoteDate());
        vo.setRealtime(outcome.getRealtime());
        vo.setProxySymbol(outcome.getProxySymbol());
        vo.setBeta(outcome.getBeta());
        vo.setRho(outcome.getRho());
        vo.setDegraded(outcome.getDegraded());
        vo.setEstimable(outcome.estimable());
        vo.setUnavailableReason(outcome.getUnavailableReason());
        vo.setUnavailableDetail(outcome.getUnavailableDetail());
        vo.setRemarks(outcome.getRemarks() == null ? List.of() : outcome.getRemarks());
        return vo;
    }
}

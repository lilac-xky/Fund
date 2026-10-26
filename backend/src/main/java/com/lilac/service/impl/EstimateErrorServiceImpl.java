package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.domain.dto.estimate.EstimateErrorQueryRequest;
import com.lilac.domain.dto.estimate.EstimateErrorSaveRequest;
import com.lilac.domain.entity.EstimateError;
import com.lilac.domain.vo.EstimateErrorVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.mapper.EstimateErrorMapper;
import com.lilac.service.EstimateErrorService;
import com.lilac.utils.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 误差历史业务实现
 */
@Slf4j
@Service
public class EstimateErrorServiceImpl implements EstimateErrorService {

    @Resource
    private EstimateErrorMapper estimateErrorMapper;

    /**
     * 统一查询误差历史
     *
     * @param request 查询请求
     * @return 误差视图列表，按交易日升序
     */
    @Override
    public List<EstimateErrorVO> listEstimateError(EstimateErrorQueryRequest request) {
        EstimateErrorQueryRequest query = request == null ? new EstimateErrorQueryRequest() : request;
        boolean hasRange = query.getStartDate() != null && query.getEndDate() != null;
        List<EstimateError> rows = estimateErrorMapper.selectListByQuery(QueryWrapper.create()
                .eq(EstimateError::getVarietyKey, query.getVarietyKey(), query.getVarietyKey() != null)
                .eq(EstimateError::getOverThreshold, query.getOverThreshold(), query.getOverThreshold() != null)
                .between(EstimateError::getTradeDate, query.getStartDate(), query.getEndDate(), hasRange)
                .orderBy(EstimateError::getTradeDate, true));
        return BeanUtil.copyToList(rows, EstimateErrorVO.class);
    }

    /**
     * 按「基金 + 交易日」存在则更新、不存在则新增，避免当日重跑校准撞唯一索引
     *
     * @param request 保存请求
     * @return 是否成功
     */
    @Override
    public boolean saveEstimateError(EstimateErrorSaveRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getVarietyKey() == null || request.getTradeDate() == null,
                HttpsCodeEnum.PARAMS_ERROR, "品种业务主键与交易日不能为空");
        EstimateError error = BeanUtil.copyProperties(request, EstimateError.class);
        List<EstimateError> existing = estimateErrorMapper.selectListByQuery(QueryWrapper.create()
                .eq(EstimateError::getVarietyKey, request.getVarietyKey())
                .eq(EstimateError::getTradeDate, request.getTradeDate()));
        if (existing.isEmpty()) {
            return estimateErrorMapper.insertSelective(error) > 0;
        }
        // 沿用已存在行的主键，否则会写成新行并撞上「基金 + 交易日」唯一索引
        error.setId(existing.get(0).getId());
        return estimateErrorMapper.update(error) > 0;
    }
}

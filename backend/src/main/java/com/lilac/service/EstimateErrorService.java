package com.lilac.service;

import com.lilac.domain.dto.estimate.EstimateErrorQueryRequest;
import com.lilac.domain.dto.estimate.EstimateErrorSaveRequest;
import com.lilac.domain.vo.EstimateErrorVO;

import java.util.List;

/**
 * 误差历史业务接口
 */
public interface EstimateErrorService {

    /**
     * 统一查询误差历史
     *
     * @param request 查询请求
     * @return 误差视图列表，按交易日升序
     */
    List<EstimateErrorVO> listEstimateError(EstimateErrorQueryRequest request);

    /**
     * 按「基金 + 交易日」存在则更新、不存在则新增
     *
     * @param request 保存请求
     * @return 是否成功
     */
    boolean saveEstimateError(EstimateErrorSaveRequest request);
}

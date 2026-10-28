package com.lilac.service;

import com.lilac.domain.dto.estimate.EstimateBatchRequest;
import com.lilac.domain.vo.EstimateBatchVO;
import com.lilac.domain.vo.EstimateVO;

/**
 * 预估业务接口，供页面、邮件与晚间校准取当日预估结果
 */
public interface EstimateService {

    /**
     * 按持仓批量预估当日涨跌，可预估与不可预估分两组返回
     *
     * @param request 预估请求
     * @return 预估总览
     */
    EstimateBatchVO estimateBatch(EstimateBatchRequest request);

    /**
     * 预估单只基金，供详情页与排障使用
     *
     * @param varietyKey 品种业务主键
     * @return 单只预估结果
     */
    EstimateVO estimateOne(String varietyKey);
}

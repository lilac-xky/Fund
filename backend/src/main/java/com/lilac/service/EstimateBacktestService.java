package com.lilac.service;

import com.lilac.domain.dto.estimate.EstimateBacktestRequest;
import com.lilac.domain.vo.BacktestResultVO;

/**
 * 因子回测业务接口，换一套参数把历史逐日重放一遍，看误差会变成什么样
 */
public interface EstimateBacktestService {

    /**
     * 按请求参数重放指定区间，不写任何库表
     *
     * @param request 回测请求
     * @return 回测结果
     */
    BacktestResultVO run(EstimateBacktestRequest request);
}

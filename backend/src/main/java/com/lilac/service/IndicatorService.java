package com.lilac.service;

import com.lilac.domain.dto.indicator.IndicatorQueryRequest;
import com.lilac.domain.vo.IndicatorAdvisoryVO;
import com.lilac.domain.vo.IndicatorVO;

/**
 * 技术指标业务接口，按使用者设定的参数算 RSI / KDJ / MACD 并给出参与策略触发的信号
 */
public interface IndicatorService {

    /**
     * 对单只标的算指标，可指定代码或品种主键
     *
     * @param request 查询请求
     * @return 指标视图
     */
    IndicatorVO computeOne(IndicatorQueryRequest request);

    /**
     * 对全部持有中的标的算指标并汇总信号
     *
     * @return 指标总览
     */
    IndicatorAdvisoryVO computeAll();
}

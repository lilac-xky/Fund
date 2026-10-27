package com.lilac.service;

import com.lilac.domain.dto.quote.QuoteBatchRequest;
import com.lilac.domain.vo.ForexRateVO;
import com.lilac.domain.vo.MarketHistoryVO;
import com.lilac.domain.vo.MarketQuoteVO;
import com.lilac.domain.vo.QuoteBatchVO;

import java.util.List;

/**
 * 行情业务接口，供预估引擎、网格与页面取当日行情与历史日线
 */
public interface MarketQuoteAppService {

    /**
     * 批量取当日行情，任一源失效自动切换到备用源
     *
     * @param request 批量请求
     * @return 批量行情视图
     */
    QuoteBatchVO batchQuotes(QuoteBatchRequest request);

    /**
     * 取单只标的的当日行情
     *
     * @param code 裸代码或带前缀代码
     * @return 行情视图
     */
    MarketQuoteVO quote(String code);

    /**
     * 取多只标的的日线
     *
     * @param codes 标的代码列表
     * @param limit 返回根数，小于等于 0 取配置默认值
     * @return 日线视图列表，按请求顺序
     */
    List<MarketHistoryVO> histories(List<String> codes, int limit);

    /**
     * 取港币兑人民币汇率
     *
     * @return 汇率视图
     */
    ForexRateVO hkdCny();
}

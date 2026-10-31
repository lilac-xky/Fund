package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.adapter.model.ForexRate;
import com.lilac.adapter.model.MarketBar;
import com.lilac.adapter.model.MarketHistory;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.model.QuoteStatus;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.domain.dto.quote.QuoteBatchRequest;
import com.lilac.domain.vo.ForexRateVO;
import com.lilac.domain.vo.MarketBarVO;
import com.lilac.domain.vo.MarketHistoryVO;
import com.lilac.domain.vo.MarketQuoteVO;
import com.lilac.domain.vo.QuoteBatchVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.service.MarketQuoteAppService;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 行情业务实现，负责把适配器结果转成视图并汇总可用性
 */
@Slf4j
@Service
public class MarketQuoteAppServiceImpl implements MarketQuoteAppService {

    private static final int MAX_BATCH = 600;

    @Resource
    private MarketQuoteAdapter marketQuoteAdapter;

    /**
     * 批量取当日行情并汇总可用、停牌、无行情三类计数
     *
     * @param request 批量请求
     * @return 批量行情视图
     */
    @Override
    public QuoteBatchVO batchQuotes(QuoteBatchRequest request) {
        ThrowUtils.throwIf(request == null || request.getCodes() == null || request.getCodes().isEmpty(),
                HttpsCodeEnum.PARAMS_ERROR, "代码列表不能为空");
        ThrowUtils.throwIf(request.getCodes().size() > MAX_BATCH,
                HttpsCodeEnum.PARAMS_ERROR, "单次最多查询 " + MAX_BATCH + " 个标的");
        Map<String, MarketQuote> quotes = marketQuoteAdapter.quotes(request.getCodes());
        QuoteBatchVO vo = new QuoteBatchVO();
        vo.setQuotes(quotes.values().stream().map(this::toVO).toList());
        vo.setRequested(request.getCodes().size());
        vo.setUsableCount((int) quotes.values().stream().filter(MarketQuote::usable).count());
        vo.setSuspendedCount((int) quotes.values().stream()
                .filter(quote -> quote.getStatus() == QuoteStatus.SUSPENDED).count());
        vo.setNoDataCount((int) quotes.values().stream()
                .filter(quote -> quote.getStatus() == QuoteStatus.NO_DATA
                        || quote.getStatus() == QuoteStatus.UNAVAILABLE).count());
        vo.setFallbackUsed(quotes.values().stream()
                .anyMatch(quote -> "sina".equals(quote.getSource())));
        vo.setDataDate(latestTradeDate(quotes));
        vo.setTradingHours(marketQuoteAdapter.tradingNow(request.getCodes()));
        return vo;
    }

    /**
     * 逐只取日线，单只失败不影响其他标的
     *
     * @param codes 标的代码列表
     * @param limit 返回根数
     * @return 日线视图列表
     */
    @Override
    public List<MarketHistoryVO> histories(List<String> codes, int limit) {
        ThrowUtils.throwIf(codes == null || codes.isEmpty(), HttpsCodeEnum.PARAMS_ERROR, "代码列表不能为空");
        ThrowUtils.throwIf(codes.size() > MAX_BATCH, HttpsCodeEnum.PARAMS_ERROR, "单次最多查询 " + MAX_BATCH + " 个标的");
        return codes.stream().map(code -> toVO(marketQuoteAdapter.history(code, limit))).toList();
    }

    /**
     * 取港币兑人民币汇率
     *
     * @return 汇率视图
     */
    @Override
    public ForexRateVO hkdCny() {
        ForexRate rate = marketQuoteAdapter.hkdCny();
        ForexRateVO vo = BeanUtil.copyProperties(rate, ForexRateVO.class);
        vo.setUsable(rate.usable());
        return vo;
    }

    /**
     * 行情转视图
     *
     * @param quote 行情
     * @return 行情视图
     */
    private MarketQuoteVO toVO(MarketQuote quote) {
        MarketQuoteVO vo = BeanUtil.copyProperties(quote, MarketQuoteVO.class, "symbol");
        vo.setSymbol(quote.getSymbol().symbol());
        vo.setUsable(quote.usable());
        return vo;
    }

    /**
     * 日线转视图
     *
     * @param history 日线
     * @return 日线视图
     */
    private MarketHistoryVO toVO(MarketHistory history) {
        MarketHistoryVO vo = BeanUtil.copyProperties(history, MarketHistoryVO.class, "symbol", "bars");
        vo.setSymbol(history.getSymbol().symbol());
        vo.setBars(toBarVOs(history.getBars()));
        vo.setSize(history.getBars() == null ? 0 : history.getBars().size());
        vo.setLatestDate(history.latestDate());
        vo.setLatestClose(history.latestClose());
        return vo;
    }

    /**
     * 日线明细转视图
     *
     * @param bars 日线
     * @return 视图列表
     */
    private List<MarketBarVO> toBarVOs(List<MarketBar> bars) {
        if (bars == null) {
            return List.of();
        }
        return bars.stream().map(bar -> BeanUtil.copyProperties(bar, MarketBarVO.class)).toList();
    }

    /**
     * 取可用标的中最新的交易日，供页面显示数据日期
     *
     * @param quotes 行情集合
     * @return 数据日期，全部不可用时为 null
     */
    private LocalDate latestTradeDate(Map<String, MarketQuote> quotes) {
        return quotes.values().stream()
                .filter(MarketQuote::usable)
                .map(MarketQuote::getTradeDate)
                .max(LocalDate::compareTo)
                .orElse(null);
    }
}

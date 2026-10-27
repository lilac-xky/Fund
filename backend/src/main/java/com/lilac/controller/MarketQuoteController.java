package com.lilac.controller;

import com.lilac.domain.dto.quote.QuoteBatchRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.ForexRateVO;
import com.lilac.domain.vo.MarketHistoryVO;
import com.lilac.domain.vo.MarketQuoteVO;
import com.lilac.domain.vo.QuoteBatchVO;
import com.lilac.service.MarketQuoteAppService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 行情接口，供预估引擎、网格提醒与页面取当日行情与日线
 */
@RestController
@RequestMapping("/quote")
public class MarketQuoteController {

    @Resource
    private MarketQuoteAppService marketQuoteAppService;

    /**
     * 批量取当日行情，任一源失效自动切到备用源
     */
    @PostMapping("/batch")
    public Result<QuoteBatchVO> batch(@RequestBody QuoteBatchRequest request) {
        return Result.success(marketQuoteAppService.batchQuotes(request));
    }

    /**
     * 取单只标的当日行情
     */
    @GetMapping("/single")
    public Result<MarketQuoteVO> single(@RequestParam("code") String code) {
        return Result.success(marketQuoteAppService.quote(code));
    }

    /**
     * 取港币兑人民币汇率
     */
    @GetMapping("/forex/hkd-cny")
    public Result<ForexRateVO> hkdCny() {
        return Result.success(marketQuoteAppService.hkdCny());
    }

    /**
     * 取多只标的的前复权日线
     */
    @GetMapping("/history")
    public Result<List<MarketHistoryVO>> history(@RequestParam("codes") List<String> codes,
                                                 @RequestParam(value = "limit", required = false, defaultValue = "0") int limit) {
        return Result.success(marketQuoteAppService.histories(codes, limit));
    }
}

package com.lilac.controller;

import com.lilac.domain.dto.indicator.IndicatorQueryRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.IndicatorAdvisoryVO;
import com.lilac.domain.vo.IndicatorVO;
import com.lilac.service.IndicatorService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 技术指标接口，按使用者设定的参数算 RSI / KDJ / MACD 并返回参与策略触发的信号
 */
@RestController
@RequestMapping("/indicator")
public class IndicatorController {

    @Resource
    private IndicatorService indicatorService;

    /**
     * 对指定代码算指标，核对与行情软件的偏差时用它
     */
    @GetMapping("/single")
    public Result<IndicatorVO> single(@RequestParam("code") String code,
                                      @RequestParam(value = "limit", required = false) Integer limit) {
        IndicatorQueryRequest request = new IndicatorQueryRequest();
        request.setCode(code);
        request.setLimit(limit);
        return Result.success(indicatorService.computeOne(request));
    }

    /**
     * 按品种业务主键算指标，页面切换标的时用它
     */
    @PostMapping("/one")
    public Result<IndicatorVO> one(@RequestBody IndicatorQueryRequest request) {
        return Result.success(indicatorService.computeOne(request));
    }

    /**
     * 对全部持有中的标的算指标并汇总信号
     */
    @PostMapping("/list")
    public Result<IndicatorAdvisoryVO> list() {
        return Result.success(indicatorService.computeAll());
    }
}

package com.lilac.controller;

import com.lilac.domain.dto.estimate.EstimateBatchRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.EstimateBatchVO;
import com.lilac.domain.vo.EstimateTrackVO;
import com.lilac.service.EstimateService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 预估接口，供页面、邮件与晚间校准取当日预估涨跌
 */
@RestController
@RequestMapping("/estimate")
public class EstimateController {

    @Resource
    private EstimateService estimateService;

    /**
     * 按持仓批量预估当日涨跌，不可预估的单独成组
     */
    @PostMapping("/batch")
    public Result<EstimateBatchVO> batch(@RequestBody(required = false) EstimateBatchRequest request) {
        return Result.success(estimateService.estimateBatch(request));
    }

    /**
     * 读取整体预估的盘中轨迹，行情条据此画出当天走向
     */
    @GetMapping("/track")
    public Result<EstimateTrackVO> track(@RequestParam(value = "tradeDate", required = false) LocalDate tradeDate) {
        return Result.success(estimateService.track(tradeDate));
    }
}

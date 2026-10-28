package com.lilac.controller;

import com.lilac.domain.dto.estimate.CalibrationRequest;
import com.lilac.domain.dto.estimate.EstimateErrorQueryRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.CalibrationResultVO;
import com.lilac.domain.vo.EstimateErrorTrendVO;
import com.lilac.domain.vo.EstimateErrorVO;
import com.lilac.domain.vo.GoldBetaRecordVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.service.EstimateCalibrationService;
import com.lilac.service.EstimateErrorService;
import com.lilac.service.EstimateGoldBetaService;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 晚间校准接口，供复盘页与定时任务取逐只误差、误差趋势与黄金 β 记录
 */
@RestController
@RequestMapping("/estimate/calibration")
public class EstimateCalibrationController {

    @Resource
    private EstimateCalibrationService estimateCalibrationService;

    @Resource
    private EstimateGoldBetaService estimateGoldBetaService;

    @Resource
    private EstimateErrorService estimateErrorService;

    /**
     * 执行某一交易日的晚间校准，重复执行同一天只覆盖不重复累计
     */
    @PostMapping("/run")
    public Result<CalibrationResultVO> run(@RequestBody(required = false) CalibrationRequest request) {
        return Result.success(estimateCalibrationService.calibrate(request));
    }

    /**
     * 查询单只基金的误差趋势
     */
    @GetMapping("/trend")
    public Result<EstimateErrorTrendVO> trend(@RequestParam("varietyKey") String varietyKey,
                                             @RequestParam(value = "days", required = false, defaultValue = "30") int days) {
        ThrowUtils.throwIf(varietyKey == null || varietyKey.isBlank(), HttpsCodeEnum.PARAMS_ERROR,
                "品种业务主键不能为空");
        return Result.success(estimateCalibrationService.trend(varietyKey.trim(), days));
    }

    /**
     * 列出建议停用预估的基金，连续多期超阈值才会出现
     */
    @GetMapping("/suspend-hints")
    public Result<List<EstimateErrorTrendVO>> suspendHints(
            @RequestParam(value = "tradeDate", required = false) LocalDate tradeDate) {
        return Result.success(estimateCalibrationService.suspensionHints(tradeDate));
    }

    /**
     * 查询黄金 β 记录，条件为空时不过滤
     */
    @GetMapping("/gold-beta")
    public Result<List<GoldBetaRecordVO>> goldBeta(
            @RequestParam(value = "varietyKey", required = false) String varietyKey,
            @RequestParam(value = "startDate", required = false) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) LocalDate endDate) {
        return Result.success(estimateGoldBetaService.list(varietyKey, startDate, endDate));
    }

    /**
     * 查询误差历史，条件为空时不过滤
     */
    @PostMapping("/errors")
    public Result<List<EstimateErrorVO>> errors(@RequestBody(required = false) EstimateErrorQueryRequest request) {
        return Result.success(estimateErrorService.listEstimateError(request));
    }
}

package com.lilac.controller;

import com.lilac.domain.dto.estimate.EstimateBatchRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.EstimateBatchVO;
import com.lilac.domain.vo.EstimateVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.service.EstimateService;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     * 预估单只基金
     */
    @PostMapping("/single")
    public Result<EstimateVO> single(@RequestParam("varietyKey") String varietyKey) {
        ThrowUtils.throwIf(varietyKey == null || varietyKey.isBlank(),
                HttpsCodeEnum.PARAMS_ERROR, "品种业务主键不能为空");
        return Result.success(estimateService.estimateOne(varietyKey.trim()));
    }
}

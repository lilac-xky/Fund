package com.lilac.controller;

import com.lilac.domain.dto.param.ParamQueryRequest;
import com.lilac.domain.dto.param.ParamSaveRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.StrategyParamVO;
import com.lilac.service.StrategyParamService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 策略参数接口
 */
@RestController
@RequestMapping("/param")
public class StrategyParamController {

    @Resource
    private StrategyParamService strategyParamService;

    /**
     * 统一查询策略参数，各条件为空时不过滤
     */
    @PostMapping("/list")
    public Result<List<StrategyParamVO>> list(@RequestBody(required = false) ParamQueryRequest request) {
        return Result.success(strategyParamService.listParam(request));
    }

    /**
     * 按「分组 + 参数名」新增或更新参数
     */
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody ParamSaveRequest request) {
        return Result.success(strategyParamService.saveParam(request));
    }
}

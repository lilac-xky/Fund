package com.lilac.controller;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.common.DeleteRequest;
import com.lilac.domain.dto.param.ParamQueryRequest;
import com.lilac.domain.dto.param.ParamSaveRequest;
import com.lilac.domain.entity.StrategyParam;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.StrategyParamVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.mapper.StrategyParamMapper;
import com.lilac.service.StrategyParamService;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
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

    @Resource
    private StrategyParamMapper strategyParamMapper;

    /**
     * 统一查询策略参数，各条件为空时不过滤
     */
    @PostMapping("/list")
    public Result<List<StrategyParamVO>> list(@RequestBody(required = false) ParamQueryRequest request) {
        return Result.success(strategyParamService.listParam(request));
    }

    /**
     * 按主键查询单个参数
     */
    @GetMapping("/get")
    public Result<StrategyParamVO> getById(Long id) {
        ThrowUtils.throwIf(id == null, HttpsCodeEnum.PARAMS_ERROR);
        StrategyParam param = strategyParamMapper.selectOneById(id);
        ThrowUtils.throwIf(param == null, HttpsCodeEnum.NOT_FOUND_ERROR);
        return Result.success(BeanUtil.copyProperties(param, StrategyParamVO.class));
    }

    /**
     * 按「分组 + 参数名」新增或更新参数
     */
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody ParamSaveRequest request) {
        return Result.success(strategyParamService.saveParam(request));
    }

    /**
     * 按主键删除参数
     */
    @PostMapping("/delete")
    public Result<Boolean> delete(@RequestBody DeleteRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, HttpsCodeEnum.PARAMS_ERROR);
        return Result.success(strategyParamMapper.deleteById(request.getId()) > 0);
    }
}

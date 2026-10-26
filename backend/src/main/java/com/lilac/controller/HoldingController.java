package com.lilac.controller;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.common.DeleteRequest;
import com.lilac.domain.dto.holding.HoldingAddRequest;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.holding.HoldingUpdateRequest;
import com.lilac.domain.entity.Holding;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.mapper.HoldingMapper;
import com.lilac.service.HoldingService;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 持仓接口
 */
@RestController
@RequestMapping("/holding")
public class HoldingController {

    @Resource
    private HoldingService holdingService;

    @Resource
    private HoldingMapper holdingMapper;

    /**
     * 统一查询持仓，各条件为空时不过滤
     */
    @PostMapping("/list")
    public Result<List<HoldingVO>> list(@RequestBody(required = false) HoldingQueryRequest request) {
        return Result.success(holdingService.listHolding(request));
    }

    /**
     * 按主键查询单个持仓
     */
    @GetMapping("/get")
    public Result<HoldingVO> getById(Long id) {
        ThrowUtils.throwIf(id == null, HttpsCodeEnum.PARAMS_ERROR);
        Holding holding = holdingMapper.selectOneById(id);
        ThrowUtils.throwIf(holding == null, HttpsCodeEnum.NOT_FOUND_ERROR);
        return Result.success(BeanUtil.copyProperties(holding, HoldingVO.class));
    }

    /**
     * 新增持仓
     */
    @PostMapping("/add")
    public Result<Boolean> add(@RequestBody HoldingAddRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getVarietyKey() == null || request.getVarietyKey().isBlank(),
                HttpsCodeEnum.PARAMS_ERROR, "品种业务主键不能为空");
        Holding holding = BeanUtil.copyProperties(request, Holding.class);
        return Result.success(holdingMapper.insertSelective(holding) > 0);
    }

    /**
     * 按主键更新持仓
     */
    @PostMapping("/update")
    public Result<Boolean> update(@RequestBody HoldingUpdateRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, HttpsCodeEnum.PARAMS_ERROR);
        Holding holding = BeanUtil.copyProperties(request, Holding.class);
        return Result.success(holdingMapper.update(holding) > 0);
    }

    /**
     * 按主键逻辑删除持仓
     */
    @PostMapping("/delete")
    public Result<Boolean> delete(@RequestBody DeleteRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, HttpsCodeEnum.PARAMS_ERROR);
        return Result.success(holdingMapper.deleteById(request.getId()) > 0);
    }
}

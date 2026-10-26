package com.lilac.controller;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.common.DeleteRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.dto.variety.VarietySaveRequest;
import com.lilac.domain.entity.Variety;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.mapper.VarietyMapper;
import com.lilac.service.VarietyService;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 品种注册表接口
 */
@RestController
@RequestMapping("/variety")
public class VarietyController {

    @Resource
    private VarietyService varietyService;

    @Resource
    private VarietyMapper varietyMapper;

    /**
     * 统一查询品种，各条件为空时不过滤
     */
    @PostMapping("/list")
    public Result<List<VarietyVO>> list(@RequestBody(required = false) VarietyQueryRequest request) {
        return Result.success(varietyService.listVariety(request));
    }

    /**
     * 按主键查询单个品种
     */
    @GetMapping("/get")
    public Result<VarietyVO> getById(Long id) {
        ThrowUtils.throwIf(id == null, HttpsCodeEnum.PARAMS_ERROR);
        Variety variety = varietyMapper.selectOneById(id);
        ThrowUtils.throwIf(variety == null, HttpsCodeEnum.NOT_FOUND_ERROR);
        return Result.success(BeanUtil.copyProperties(variety, VarietyVO.class));
    }

    /**
     * 新增或按业务主键更新品种
     */
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody VarietySaveRequest request) {
        return Result.success(varietyService.saveVariety(request));
    }

    /**
     * 按主键删除品种
     */
    @PostMapping("/delete")
    public Result<Boolean> delete(@RequestBody DeleteRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, HttpsCodeEnum.PARAMS_ERROR);
        return Result.success(varietyMapper.deleteById(request.getId()) > 0);
    }
}

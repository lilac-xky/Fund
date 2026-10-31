package com.lilac.controller;

import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.dto.variety.VarietySaveRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.service.VarietyService;
import jakarta.annotation.Resource;
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

    /**
     * 统一查询品种，各条件为空时不过滤
     */
    @PostMapping("/list")
    public Result<List<VarietyVO>> list(@RequestBody(required = false) VarietyQueryRequest request) {
        return Result.success(varietyService.listVariety(request));
    }

    /**
     * 新增或按业务主键更新品种
     */
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody VarietySaveRequest request) {
        return Result.success(varietyService.saveVariety(request));
    }
}

package com.lilac.controller;

import com.lilac.domain.dto.variety.CategoryBatchRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.dto.variety.VarietySaveRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.PendingCategoryVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.service.VarietyService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * 列出待确认类型的标的，带推断依据与建议类型
     */
    @GetMapping("/pending")
    public Result<PendingCategoryVO> pending(
            @RequestParam(value = "holdingOnly", required = false, defaultValue = "false")
            boolean holdingOnly) {
        return Result.success(varietyService.listPending(holdingOnly));
    }

    /**
     * 批量指定类型与跟踪指数，指定后预估与策略立即按新类型生效
     */
    @PostMapping("/category/assign")
    public Result<Integer> assignCategory(@RequestBody CategoryBatchRequest request) {
        return Result.success(varietyService.assignCategory(request));
    }
}

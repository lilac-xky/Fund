package com.lilac.controller;

import com.lilac.domain.dto.fund.FundConfirmRequest;
import com.lilac.domain.dto.fund.FundDisambiguateRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.FundCatalogStatusVO;
import com.lilac.domain.vo.FundCatalogSyncVO;
import com.lilac.domain.vo.FundDisambiguateVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.service.FundDisambiguateService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基金代码消歧接口，供持仓录入页自动带出名称与类型
 */
@RestController
@RequestMapping("/fund")
public class FundController {

    @Resource
    private FundDisambiguateService fundDisambiguateService;

    /**
     * 按 6 位代码返回候选列表与身份回显
     */
    @PostMapping("/disambiguate")
    public Result<FundDisambiguateVO> disambiguate(@RequestBody FundDisambiguateRequest request) {
        return Result.success(fundDisambiguateService.disambiguate(request));
    }

    /**
     * 确认候选并写入品种注册表
     */
    @PostMapping("/confirm")
    public Result<VarietyVO> confirm(@RequestBody FundConfirmRequest request) {
        return Result.success(fundDisambiguateService.confirm(request));
    }

    /**
     * 强制刷新基金全集缓存
     */
    @PostMapping("/catalog/sync")
    public Result<FundCatalogSyncVO> syncCatalog() {
        return Result.success(fundDisambiguateService.syncCatalog());
    }

    /**
     * 查询基金全集缓存状态
     */
    @GetMapping("/catalog/status")
    public Result<FundCatalogStatusVO> catalogStatus() {
        return Result.success(fundDisambiguateService.catalogStatus());
    }
}

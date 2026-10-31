package com.lilac.controller;

import com.lilac.domain.dto.bond.BondMonitorRequest;
import com.lilac.domain.dto.bond.BondParamSaveRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.BondAdvisoryVO;
import com.lilac.domain.vo.BondMonitorVO;
import com.lilac.domain.vo.BondPlanVO;
import com.lilac.service.BondMonitorService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 债券监控接口，含参数读写、全部持仓判定与单只试算
 */
@RestController
@RequestMapping("/bond")
public class BondController {

    @Resource
    private BondMonitorService bondMonitorService;

    /**
     * 读取债券监控参数，含问题清单
     */
    @GetMapping("/plan")
    public Result<BondPlanVO> plan() {
        return Result.success(bondMonitorService.getPlan());
    }

    /**
     * 保存债券监控参数，横盘天数超过回看窗口时拒绝并指出字段
     */
    @PostMapping("/plan/save")
    public Result<BondPlanVO> savePlan(@RequestBody BondParamSaveRequest request) {
        return Result.success(bondMonitorService.savePlan(request));
    }

    /**
     * 对全部持有中的债券基金判定横盘与趋势转弱
     */
    @PostMapping("/list")
    public Result<BondAdvisoryVO> list() {
        return Result.success(bondMonitorService.adviseAll());
    }

    /**
     * 对单只债券基金试算，核对横盘天数与趋势指标
     */
    @PostMapping("/single")
    public Result<BondMonitorVO> single(@RequestBody BondMonitorRequest request) {
        return Result.success(bondMonitorService.adviseOne(request));
    }
}

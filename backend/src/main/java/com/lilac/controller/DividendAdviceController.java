package com.lilac.controller;

import com.lilac.domain.dto.dividend.DividendAdviceRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.DividendAdviceVO;
import com.lilac.domain.vo.DividendAdvisoryVO;
import com.lilac.domain.vo.DividendPlanVO;
import com.lilac.domain.vo.DividendWatchVO;
import com.lilac.service.DividendAdviceService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 红利加减仓建议接口，含参数读写与逐只建议
 */
@RestController
@RequestMapping("/dividend/advice")
public class DividendAdviceController {

    @Resource
    private DividendAdviceService dividendAdviceService;

    /**
     * 读取红利参数，含不合法组合的问题清单
     */
    @GetMapping("/plan")
    public Result<DividendPlanVO> plan() {
        return Result.success(dividendAdviceService.getPlan());
    }

    /**
     * 对全部持有中的红利基金逐只给加减仓建议
     */
    @PostMapping("/list")
    public Result<DividendAdvisoryVO> list() {
        return Result.success(dividendAdviceService.adviseAll());
    }

    /**
     * 对单只红利基金给建议，可手工指定当前金额用于核对规则
     */
    @PostMapping("/single")
    public Result<DividendAdviceVO> single(@RequestBody DividendAdviceRequest request) {
        return Result.success(dividendAdviceService.adviseOne(request));
    }

    /**
     * 列出红利策略的监控对象，说明这套参数管的是哪几只基金
     */
    @GetMapping("/watch")
    public Result<List<DividendWatchVO>> watch() {
        return Result.success(dividendAdviceService.watchList());
    }
}

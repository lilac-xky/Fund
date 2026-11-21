package com.lilac.controller;

import com.lilac.domain.dto.dividend.DividendAdviceRequest;
import com.lilac.domain.dto.dividend.DividendParamSaveRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 红利加减仓建议接口，参数按基金分开读写，另含逐只建议与监控对象
 */
@RestController
@RequestMapping("/dividend/advice")
public class DividendAdviceController {

    @Resource
    private DividendAdviceService dividendAdviceService;

    /**
     * 读取指定基金的红利参数，含不合法组合的问题清单
     */
    @GetMapping("/plan")
    public Result<DividendPlanVO> plan(@RequestParam("varietyKey") String varietyKey) {
        return Result.success(dividendAdviceService.getPlan(varietyKey));
    }

    /**
     * 保存指定基金的红利参数，保底高于基准或阈值颠倒时拒绝并指出字段
     */
    @PostMapping("/plan/save")
    public Result<DividendPlanVO> savePlan(@RequestBody DividendParamSaveRequest request) {
        return Result.success(dividendAdviceService.savePlan(request));
    }

    /**
     * 清空指定基金的红利参数，回到未配置状态
     */
    @PostMapping("/plan/remove")
    public Result<Boolean> removePlan(@RequestBody DividendParamSaveRequest request) {
        return Result.success(dividendAdviceService.removePlan(request == null ? null : request.getVarietyKey()));
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
     * 列出红利监控对象，每只带出跟踪指数、股息率与它自己的参数
     */
    @GetMapping("/watch")
    public Result<List<DividendWatchVO>> watch() {
        return Result.success(dividendAdviceService.watchList());
    }
}

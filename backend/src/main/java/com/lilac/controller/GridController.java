package com.lilac.controller;

import com.lilac.domain.dto.grid.GridAdviceRequest;
import com.lilac.domain.dto.grid.GridParamSaveRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.GridAdviceVO;
import com.lilac.domain.vo.GridAdvisoryVO;
import com.lilac.domain.vo.GridPlanVO;
import com.lilac.domain.vo.GridWatchVO;
import com.lilac.service.GridAdviceService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 网格到价提醒接口，参数按标的读写，不再提供全局兜底
 */
@RestController
@RequestMapping("/grid")
public class GridController {

    @Resource
    private GridAdviceService gridAdviceService;

    /**
     * 读取指定标的的网格参数，页面逐只编辑时用它
     */
    @GetMapping("/plan/single")
    public Result<GridPlanVO> planOf(@RequestParam("varietyKey") String varietyKey) {
        return Result.success(gridAdviceService.getPlanOf(varietyKey));
    }

    /**
     * 保存指定标的的网格参数，上下界颠倒或划不出两格时拒绝并指出字段
     */
    @PostMapping("/plan/save")
    public Result<GridPlanVO> savePlan(@RequestBody GridParamSaveRequest request) {
        return Result.success(gridAdviceService.savePlan(request));
    }

    /**
     * 清除指定标的的网格参数，回到未配置状态
     */
    @PostMapping("/plan/remove")
    public Result<Boolean> removePlan(@RequestBody GridParamSaveRequest request) {
        return Result.success(gridAdviceService.removePlan(request == null ? null : request.getVarietyKey()));
    }

    /**
     * 列出网格的监控对象，说明每只标的用的是哪套格子、当前价落在区间内外
     */
    @GetMapping("/watch")
    public Result<List<GridWatchVO>> watch() {
        return Result.success(gridAdviceService.watchList());
    }

    /**
     * 对全部持有中的标的逐只判断是否触及网格线
     */
    @PostMapping("/list")
    public Result<GridAdvisoryVO> list() {
        return Result.success(gridAdviceService.adviseAll());
    }

    /**
     * 对单只标的试算，可手工指定当前价与参考价
     */
    @PostMapping("/single")
    public Result<GridAdviceVO> single(@RequestBody GridAdviceRequest request) {
        return Result.success(gridAdviceService.adviseOne(request));
    }

    /**
     * 列出指定标的的网格线
     */
    @GetMapping("/lines")
    public Result<List<BigDecimal>> lines(@RequestParam("varietyKey") String varietyKey) {
        return Result.success(gridAdviceService.lines(varietyKey));
    }
}

package com.lilac.controller;

import com.lilac.domain.dto.dividend.DividendManualRequest;
import com.lilac.domain.dto.dividend.DividendQueryRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.IndexDividendOverviewVO;
import com.lilac.domain.vo.IndexDividendRefreshVO;
import com.lilac.service.IndexDividendService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 指数股息率接口，供红利策略取数与手工覆盖
 */
@RestController
@RequestMapping("/dividend")
public class IndexDividendController {

    @Resource
    private IndexDividendService indexDividendService;

    /**
     * 刷新股息率，指定指数代码时只刷这几只，留空则按红利持仓的跟踪指数推导
     */
    @PostMapping("/refresh")
    public Result<IndexDividendRefreshVO> refresh(@RequestBody(required = false) List<String> indexCodes) {
        return Result.success(indexDividendService.refresh(indexCodes));
    }

    /**
     * 查询股息率，条件为空时返回全部已知指数的最新一期
     */
    @PostMapping("/list")
    public Result<List<IndexDividendOverviewVO>> list(@RequestBody(required = false) DividendQueryRequest request) {
        return Result.success(indexDividendService.listOverview(request));
    }

    /**
     * 手工录入股息率，国证系列与抓取失败时走这条路径
     */
    @PostMapping("/manual")
    public Result<IndexDividendOverviewVO> manual(@RequestBody DividendManualRequest request) {
        return Result.success(indexDividendService.saveManual(request));
    }
}

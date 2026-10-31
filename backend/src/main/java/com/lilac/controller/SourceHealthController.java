package com.lilac.controller;

import com.lilac.domain.dto.source.SourceEventQueryRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.SourceHealthVO;
import com.lilac.service.SourceHealthService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据源探活接口，汇总各源健康度并给出降级矩阵
 */
@RestController
@RequestMapping("/source")
public class SourceHealthController {

    @Resource
    private SourceHealthService sourceHealthService;

    /**
     * 取各数据源健康度与降级矩阵，失败率偏高时给出改版提示
     */
    @PostMapping("/health")
    public Result<SourceHealthVO> health(@RequestBody(required = false) SourceEventQueryRequest request) {
        return Result.success(sourceHealthService.health(request));
    }
}

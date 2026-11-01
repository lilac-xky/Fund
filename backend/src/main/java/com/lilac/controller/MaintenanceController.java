package com.lilac.controller;

import com.lilac.domain.result.Result;
import com.lilac.domain.vo.DuplicateCleanVO;
import com.lilac.service.DuplicateCleanService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 重复数据清理接口，找出并删除完全重复的持仓批次与变动记录
 */
@RestController
@RequestMapping("/maintenance")
public class MaintenanceController {

    @Resource
    private DuplicateCleanService duplicateCleanService;

    /**
     * 预览重复批次，不写库
     */
    @GetMapping("/duplicate/preview")
    public Result<List<DuplicateCleanVO>> preview() {
        return Result.success(duplicateCleanService.preview());
    }

    /**
     * 执行清理，同组只留最早一条
     */
    @PostMapping("/duplicate/clean")
    public Result<String> clean() {
        return Result.success(duplicateCleanService.clean());
    }
}

package com.lilac.controller;

import com.lilac.domain.result.Result;
import com.lilac.domain.vo.ReviewReportVO;
import com.lilac.service.ReviewReportService;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 复盘报告接口，当日报告生成、对照导出与误差历史导出
 */
@RestController
@RequestMapping("/report")
public class ReviewReportController {

    @Resource
    private ReviewReportService reviewReportService;

    /**
     * 生成当日复盘报告并留存在 data/report 下
     */
    @PostMapping("/generate")
    public Result<ReviewReportVO> generate(
            @RequestParam(value = "tradeDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        return Result.success(reviewReportService.generate(tradeDate));
    }

    /**
     * 导出预估与实际对照的 CSV，Excel 打开中文不乱码
     */
    @GetMapping("/export/compare")
    public Result<ReviewReportVO> exportCompare(
            @RequestParam(value = "tradeDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        return Result.success(reviewReportService.exportComparison(tradeDate));
    }

    /**
     * 按日期区间导出误差历史 CSV
     */
    @GetMapping("/export/errors")
    public Result<String> exportErrors(
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(reviewReportService.exportErrorHistory(startDate, endDate));
    }
}

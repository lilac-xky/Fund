package com.lilac.controller;

import com.lilac.domain.dto.report.OperationLogQueryRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.OperationLogVO;
import com.lilac.service.OperationLogService;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 操作日志接口，把提醒发送与买卖变动合并成统一时间线并支持导出
 */
@RestController
@RequestMapping("/oplog")
public class OperationLogController {

    @Resource
    private OperationLogService operationLogService;

    /**
     * 查询统一操作日志，按发生时间倒序
     */
    @PostMapping("/list")
    public Result<List<OperationLogVO>> list(@RequestBody(required = false) OperationLogQueryRequest request) {
        return Result.success(operationLogService.list(request));
    }

    /**
     * 按日期区间导出操作日志 CSV，Excel 打开中文不乱码
     */
    @GetMapping("/export")
    public Result<String> export(
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        OperationLogQueryRequest request = new OperationLogQueryRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        return Result.success(operationLogService.exportCsv(request));
    }
}

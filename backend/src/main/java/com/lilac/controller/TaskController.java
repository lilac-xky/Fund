package com.lilac.controller;

import com.lilac.domain.dto.send.SendRecordQueryRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.SendRecordVO;
import com.lilac.domain.vo.TaskOverviewVO;
import com.lilac.enums.SendPoint;
import com.lilac.service.SendRecordService;
import com.lilac.task.TaskOrchestrator;
import com.lilac.task.model.PointOutcome;
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
 * 时点调度接口，供页面查看四个时点的发送状态、手动触发与补跑
 */
@RestController
@RequestMapping("/task")
public class TaskController {

    @Resource
    private TaskOrchestrator taskOrchestrator;

    @Resource
    private SendRecordService sendRecordService;

    /**
     * 查询当日四个时点的发送状态
     */
    @GetMapping("/overview")
    public Result<TaskOverviewVO> overview(
            @RequestParam(value = "tradeDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        return Result.success(taskOrchestrator.overview(tradeDate));
    }

    /**
     * 手动触发某个时点，供补发与调试使用
     */
    @PostMapping("/trigger")
    public Result<PointOutcome> trigger(
            @RequestParam("point") SendPoint point,
            @RequestParam(value = "tradeDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(value = "resend", required = false, defaultValue = "false") boolean resend) {
        return Result.success(taskOrchestrator.triggerManually(point, tradeDate, resend));
    }

    /**
     * 立即扫描当日应发未发的时点并补跑，等价于重启后的补跑动作
     */
    @PostMapping("/catch-up")
    public Result<Integer> catchUp() {
        return Result.success(taskOrchestrator.catchUpNow());
    }

    /**
     * 查询发送留痕，条件为空时不过滤
     */
    @PostMapping("/records")
    public Result<List<SendRecordVO>> records(@RequestBody(required = false) SendRecordQueryRequest request) {
        return Result.success(sendRecordService.listSendRecord(request));
    }
}

package com.lilac.controller;

import com.lilac.domain.dto.lesson.LessonQueryRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.LessonAdvisoryVO;
import com.lilac.service.LessonAdviceService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 历史教训警示接口，按三类错误模式检查历史操作并返回可读的教训说明
 */
@RestController
@RequestMapping("/lesson")
public class LessonController {

    @Resource
    private LessonAdviceService lessonAdviceService;

    /**
     * 检查历史变动是否命中追高、盲目换仓或恐慌卖出
     */
    @PostMapping("/list")
    public Result<LessonAdvisoryVO> list(@RequestBody(required = false) LessonQueryRequest request) {
        return Result.success(lessonAdviceService.advise(request));
    }
}

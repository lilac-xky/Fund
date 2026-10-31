package com.lilac.service;

import com.lilac.domain.dto.lesson.LessonQueryRequest;
import com.lilac.domain.dto.lesson.LessonTrialRequest;
import com.lilac.domain.vo.LessonAdvisoryVO;
import com.lilac.domain.vo.LessonTrialVO;

/**
 * 历史教训警示业务接口，按三类错误模式检查历史操作并给出可读的教训说明
 */
public interface LessonAdviceService {

    /**
     * 检查历史变动，命中三类错误模式时给出警示
     *
     * @param request 查询请求，可为空表示检查全部
     * @return 警示总览
     */
    LessonAdvisoryVO advise(LessonQueryRequest request);

    /**
     * 用给定参数在历史数据上试算，不落库，供规则页面回看是否触发
     *
     * @param request 试算请求
     * @return 试算结果，含所用参数与命中明细
     */
    LessonTrialVO trial(LessonTrialRequest request);
}

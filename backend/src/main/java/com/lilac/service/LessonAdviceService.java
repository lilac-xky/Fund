package com.lilac.service;

import com.lilac.domain.dto.lesson.LessonQueryRequest;
import com.lilac.domain.vo.LessonAdvisoryVO;

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
}

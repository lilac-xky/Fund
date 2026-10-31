package com.lilac.domain.dto.lesson;

import lombok.Data;

import java.io.Serializable;

/**
 * 历史教训警示查询请求，留空时检查全部历史变动
 */
@Data
public class LessonQueryRequest implements Serializable {

    /**
     * 只检查该品种，为空时检查全部
     */
    private String varietyKey;

    /**
     * 回看天数，为空时取默认值
     */
    private Integer days;

    private static final long serialVersionUID = 1L;
}

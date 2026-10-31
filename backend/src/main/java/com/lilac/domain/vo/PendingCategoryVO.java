package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 待确认类型的标的清单，说明为什么无法自动识别以及建议怎么填
 */
@Data
public class PendingCategoryVO implements Serializable {

    /**
     * 待确认的标的
     */
    private List<PendingItemVO> items;

    /**
     * 待确认总数
     */
    private Integer total;

    /**
     * 其中属于黄金或红利的可疑项数量
     */
    private Integer suspectedCount;

    /**
     * 操作提示
     */
    private String notice;

    private static final long serialVersionUID = 1L;
}

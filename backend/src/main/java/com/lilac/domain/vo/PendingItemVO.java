package com.lilac.domain.vo;

import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 单个待确认标的，带推断依据与可选类型
 */
@Data
public class PendingItemVO implements Serializable {

    /**
     * 业务主键
     */
    private String varietyKey;

    /**
     * 6 位代码
     */
    private String varietyCode;

    /**
     * 标的名称
     */
    private String varietyName;

    /**
     * 品种类型
     */
    private VarietyType varietyType;

    /**
     * 当前类型，待确认时为 UNKNOWN
     */
    private FundCategory category;

    /**
     * 跟踪指数代码
     */
    private String trackIndexCode;

    /**
     * 为什么推断不出来，一句话
     */
    private String reason;

    /**
     * 名称里命中的关键词，供使用者判断
     */
    private List<String> hints;

    /**
     * 建议的类型，没有建议时为空
     */
    private FundCategory suggested;

    /**
     * 是否持有中，只有持仓的才影响预估与策略
     */
    private Boolean holding;

    private static final long serialVersionUID = 1L;
}

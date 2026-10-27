package com.lilac.domain.vo;

import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import lombok.Data;

import java.io.Serializable;

/**
 * 消歧候选项，一个代码在一种品种类型下的身份
 */
@Data
public class FundCandidateVO implements Serializable {

    /**
     * 业务主键，如 OF:000001
     */
    private String varietyKey;

    /**
     * 品种类型
     */
    private VarietyType varietyType;

    /**
     * 品种类型中文名
     */
    private String varietyTypeLabel;

    /**
     * 6 位代码
     */
    private String varietyCode;

    /**
     * 标的名称
     */
    private String varietyName;

    /**
     * 基金类型
     */
    private FundCategory category;

    /**
     * 基金类型中文名
     */
    private String categoryLabel;

    /**
     * 类型是否已确定，false 表示须使用者手动指定
     */
    private Boolean confirmed;

    /**
     * 跟踪指数代码，红利基金用于查股息率
     */
    private String trackIndexCode;

    /**
     * 是否可从持仓入口录入，指数与股票为 false
     */
    private Boolean recordable;

    /**
     * 身份提示，说明判定依据或需要注意的地方
     */
    private String note;

    private static final long serialVersionUID = 1L;
}

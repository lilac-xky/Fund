package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 消歧结果，含候选列表、同名份额与降级标记
 */
@Data
public class FundDisambiguateVO implements Serializable {

    /**
     * 查询的 6 位代码
     */
    private String code;

    /**
     * 是否命中任何候选
     */
    private Boolean exists;

    /**
     * 候选列表，按场外基金、场内 ETF、指数顺序
     */
    private List<FundCandidateVO> candidates;

    /**
     * 名称相近的其他份额，A/C 类并列展示供核对
     */
    private List<FundCandidateVO> relatedShareClasses;

    /**
     * 同码股票提示，股票不从持仓入口录入
     */
    private String stockHint;

    /**
     * 是否存在类型待确认的候选
     */
    private Boolean needConfirm;

    /**
     * 是否降级，指数校验失败时为 true
     */
    private Boolean degraded;

    /**
     * 基金全集缓存的数据时间
     */
    private LocalDateTime catalogUpdatedAt;

    private static final long serialVersionUID = 1L;
}

package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 重复批次清理预览，列出将删除的批次与保留的批次，执行前供人工确认
 */
@Data
public class DuplicateCleanVO implements Serializable {

    /**
     * 品种业务主键
     */
    private String varietyKey;

    /**
     * 品种名称
     */
    private String varietyName;

    /**
     * 6 位代码
     */
    private String varietyCode;

    /**
     * 份额
     */
    private BigDecimal shares;

    /**
     * 成本价
     */
    private BigDecimal costPrice;

    /**
     * 建仓日期
     */
    private LocalDate openDate;

    /**
     * 该组合出现的次数
     */
    private Integer count;

    /**
     * 将保留的批次主键，取最早创建的那条
     */
    private Long keepId;

    /**
     * 将删除的批次主键
     */
    private String removeIds;

    private static final long serialVersionUID = 1L;
}

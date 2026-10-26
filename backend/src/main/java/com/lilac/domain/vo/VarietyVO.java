package com.lilac.domain.vo;

import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 品种视图对象
 */
@Data
public class VarietyVO implements Serializable {

    /**
     * 主键
     */
    private Long id;

    /**
     * 业务主键
     */
    private String varietyKey;

    /**
     * 品种类型
     */
    private VarietyType varietyType;

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
     * 跟踪指数代码
     */
    private String trackIndexCode;

    /**
     * 类型是否已确认
     */
    private Boolean confirmed;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}

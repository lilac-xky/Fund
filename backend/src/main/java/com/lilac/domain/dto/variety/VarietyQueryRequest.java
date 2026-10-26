package com.lilac.domain.dto.variety;

import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import lombok.Data;

import java.io.Serializable;

/**
 * 品种查询请求，各字段为空时不参与过滤
 */
@Data
public class VarietyQueryRequest implements Serializable {

    /**
     * 业务主键
     */
    private String varietyKey;

    /**
     * 6 位代码
     */
    private String varietyCode;

    /**
     * 品种类型
     */
    private VarietyType varietyType;

    /**
     * 基金类型
     */
    private FundCategory category;

    /**
     * 是否已确认类型
     */
    private Boolean confirmed;

    private static final long serialVersionUID = 1L;
}

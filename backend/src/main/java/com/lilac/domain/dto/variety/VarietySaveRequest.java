package com.lilac.domain.dto.variety;

import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import lombok.Data;

import java.io.Serializable;

/**
 * 品种新增或更新请求
 */
@Data
public class VarietySaveRequest implements Serializable {

    /**
     * 业务主键，形如 OF:000001
     */
    private String varietyKey;

    /**
     * 代码所属品种类型
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

    private static final long serialVersionUID = 1L;
}

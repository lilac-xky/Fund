package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 未能校准的基金与原因，绝不静默跳过
 */
@Data
public class CalibrationGapVO implements Serializable {

    /**
     * 品种业务主键
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
     * 未能校准的原因
     */
    private String reason;

    private static final long serialVersionUID = 1L;
}

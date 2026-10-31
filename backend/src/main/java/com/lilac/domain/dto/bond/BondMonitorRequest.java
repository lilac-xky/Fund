package com.lilac.domain.dto.bond;

import lombok.Data;

import java.io.Serializable;

/**
 * 单只债券基金的监控试算请求
 */
@Data
public class BondMonitorRequest implements Serializable {

    /**
     * 品种业务主键
     */
    private String varietyKey;

    private static final long serialVersionUID = 1L;
}

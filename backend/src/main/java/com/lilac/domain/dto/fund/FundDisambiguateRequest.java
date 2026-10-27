package com.lilac.domain.dto.fund;

import lombok.Data;

import java.io.Serializable;

/**
 * 基金代码消歧请求
 */
@Data
public class FundDisambiguateRequest implements Serializable {

    /**
     * 6 位基金或在市代码
     */
    private String code;

    private static final long serialVersionUID = 1L;
}

package com.lilac.domain.dto.holding;

import com.lilac.enums.HoldingChangeType;
import lombok.Data;

import java.io.Serializable;

/**
 * 持仓变动历史查询请求，各字段为空时不参与过滤
 */
@Data
public class HoldingChangeQueryRequest implements Serializable {

    /**
     * 品种业务主键
     */
    private String varietyKey;

    /**
     * 变动类型
     */
    private HoldingChangeType changeType;

    /**
     * 返回条数上限，为空时取默认值
     */
    private Integer limit;

    private static final long serialVersionUID = 1L;
}

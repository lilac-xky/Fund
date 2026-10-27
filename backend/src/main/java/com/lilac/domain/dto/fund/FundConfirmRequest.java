package com.lilac.domain.dto.fund;

import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import lombok.Data;

import java.io.Serializable;

/**
 * 消歧确认请求，类型无法自动判定时须显式指定
 */
@Data
public class FundConfirmRequest implements Serializable {

    /**
     * 6 位代码
     */
    private String code;

    /**
     * 品种类型，只能是场外基金或场内 ETF
     */
    private VarietyType varietyType;

    /**
     * 基金类型，自动判定为待确认时必填
     */
    private FundCategory category;

    /**
     * 跟踪指数代码，红利基金必填以取股息率
     */
    private String trackIndexCode;

    private static final long serialVersionUID = 1L;
}

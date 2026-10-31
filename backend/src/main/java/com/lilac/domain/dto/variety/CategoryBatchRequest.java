package com.lilac.domain.dto.variety;

import com.lilac.enums.DividendMethod;
import com.lilac.enums.FundCategory;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 待确认类型的批量指定请求，一次可给多个标的定同一类型
 */
@Data
public class CategoryBatchRequest implements Serializable {

    /**
     * 要处理的品种业务主键
     */
    private List<String> varietyKeys;

    /**
     * 指定为哪一类，不能是 UNKNOWN
     */
    private FundCategory category;

    /**
     * 跟踪指数代码，红利类型必填，用于查股息率
     */
    private String trackIndexCode;

    /**
     * 分红方式，留空时保持原值
     */
    private DividendMethod dividendMethod;

    private static final long serialVersionUID = 1L;
}

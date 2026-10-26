package com.lilac.domain.dto.param;

import com.lilac.enums.ParamGroup;
import lombok.Data;

import java.io.Serializable;

/**
 * 策略参数查询请求，各字段为空时不参与过滤
 */
@Data
public class ParamQueryRequest implements Serializable {

    /**
     * 参数分组
     */
    private ParamGroup paramGroup;

    /**
     * 参数名
     */
    private String paramKey;

    private static final long serialVersionUID = 1L;
}

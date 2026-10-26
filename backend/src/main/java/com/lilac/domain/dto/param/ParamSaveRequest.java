package com.lilac.domain.dto.param;

import com.lilac.enums.ParamGroup;
import lombok.Data;

import java.io.Serializable;

/**
 * 策略参数新增或更新请求
 */
@Data
public class ParamSaveRequest implements Serializable {

    /**
     * 参数分组
     */
    private ParamGroup paramGroup;

    /**
     * 参数名，组内唯一
     */
    private String paramKey;

    /**
     * 参数值
     */
    private String paramValue;

    /**
     * 参数说明
     */
    private String paramDesc;

    private static final long serialVersionUID = 1L;
}

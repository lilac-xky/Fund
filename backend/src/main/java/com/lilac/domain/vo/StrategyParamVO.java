package com.lilac.domain.vo;

import com.lilac.enums.ParamGroup;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 策略参数视图对象
 */
@Data
public class StrategyParamVO implements Serializable {

    /**
     * 主键
     */
    private Long id;

    /**
     * 参数分组
     */
    private ParamGroup paramGroup;

    /**
     * 参数名
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

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}

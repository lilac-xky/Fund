package com.lilac.domain.entity;

import com.lilac.enums.ParamGroup;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 策略参数，按分组加参数名存放，值统一为字符串
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "strategy_param", camelToUnderline = false, comment = "策略参数，按分组加参数名存放，值统一为字符串")
public class StrategyParam extends BaseEntity {

    /**
     * 参数分组
     */
    private ParamGroup paramGroup;

    /**
     * 参数名，组内唯一
     */
    private String paramKey;

    /**
     * 参数值，数值型由使用方自行解析
     */
    private String paramValue;

    /**
     * 参数说明，供设置页展示
     */
    private String paramDesc;
}

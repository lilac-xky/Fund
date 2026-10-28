package com.lilac.domain.dto.setting;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 分组参数批量保存请求，values 为「参数键 → 参数值」，值统一按字符串传
 */
@Data
public class SettingGroupSaveRequest implements Serializable {

    /**
     * 分组，与 ParamGroup 同名
     */
    private String group;

    /**
     * 参数键到参数值的映射，未出现的键保持原值不动
     */
    private Map<String, String> values = new LinkedHashMap<>();

    private static final long serialVersionUID = 1L;
}

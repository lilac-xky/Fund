package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一个参数分组的展示模型，含全部字段与分组级说明
 */
@Data
public class SettingGroupVO implements Serializable {

    /**
     * 分组，与 ParamGroup 同名
     */
    private String group;

    /**
     * 分组中文名
     */
    private String label;

    /**
     * 分组说明，一句话
     */
    private String description;

    /**
     * 分组内的字段
     */
    private List<SettingItemVO> items = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}

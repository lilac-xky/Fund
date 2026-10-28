package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 单个参数项的展示模型，含当前值、默认值与单位，页面直接按它渲染字段
 */
@Data
public class SettingItemVO implements Serializable {

    /**
     * 分组，与 ParamGroup 同名
     */
    private String group;

    /**
     * 参数键
     */
    private String key;

    /**
     * 字段中文名
     */
    private String label;

    /**
     * 字段说明，一句话
     */
    private String description;

    /**
     * 当前值，库里没有时取默认值，都没有则为 null
     */
    private String value;

    /**
     * 内置默认值，用于「恢复默认」与提示
     */
    private String defaultValue;

    /**
     * 单位，如 元 / % / 日
     */
    private String unit;

    /**
     * 控件类型，TEXT / DECIMAL / INTEGER / BOOLEAN
     */
    private String type;

    /**
     * 当前值是否来自库，false 表示仍在用默认值
     */
    private Boolean customized;

    private static final long serialVersionUID = 1L;
}

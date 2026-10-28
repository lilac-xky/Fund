package com.lilac.domain.dto.setting;

import lombok.Data;

import java.io.Serializable;

/**
 * 单个字段的校验错误，字段名与前端输入名一一对应，便于就地标红
 */
@Data
public class SettingFieldError implements Serializable {

    /**
     * 分组，与 ParamGroup 同名
     */
    private String group;

    /**
     * 字段名，与参数键同名
     */
    private String field;

    /**
     * 字段中文名，用于拼提示语
     */
    private String label;

    /**
     * 错误原因，直接展示给使用者
     */
    private String message;

    private static final long serialVersionUID = 1L;

    /**
     * 构造一条字段错误
     *
     * @param group   分组
     * @param field   字段名
     * @param label   字段中文名
     * @param message 错误原因
     * @return 字段错误
     */
    public static SettingFieldError of(String group, String field, String label, String message) {
        SettingFieldError error = new SettingFieldError();
        error.setGroup(group);
        error.setField(field);
        error.setLabel(label);
        error.setMessage(message);
        return error;
    }
}

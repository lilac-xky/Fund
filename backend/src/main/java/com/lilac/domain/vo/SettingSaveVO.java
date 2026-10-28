package com.lilac.domain.vo;

import com.lilac.domain.dto.setting.SettingFieldError;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 参数保存结果，ok 为 false 时 errors 逐字段说明哪里不对
 */
@Data
public class SettingSaveVO implements Serializable {

    /**
     * 是否保存成功
     */
    private Boolean ok;

    /**
     * 成功时写入的参数条数
     */
    private Integer savedCount;

    /**
     * 失败时的字段级错误，页面据此逐项标红
     */
    private List<SettingFieldError> errors = new ArrayList<>();

    /**
     * 汇总说明，成功与失败都有一句，便于底部保存条展示
     */
    private String message;

    /**
     * 本次保存后新生效的时间点说明，如「下一个时点 20:00 生效」
     */
    private String effectiveNote;

    private static final long serialVersionUID = 1L;
}

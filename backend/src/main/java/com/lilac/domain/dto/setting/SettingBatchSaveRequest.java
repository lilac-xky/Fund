package com.lilac.domain.dto.setting;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 全部参数批量保存请求，一次提交多个分组，任一字段非法则整批拒绝
 */
@Data
public class SettingBatchSaveRequest implements Serializable {

    /**
     * 待保存的分组列表
     */
    private List<SettingGroupSaveRequest> groups = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}

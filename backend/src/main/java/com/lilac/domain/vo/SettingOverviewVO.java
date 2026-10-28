package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 参数设置页的完整数据，一次请求带回四个分组、邮件状态与生效说明
 */
@Data
public class SettingOverviewVO implements Serializable {

    /**
     * 参数分组，按固定顺序排列
     */
    private List<SettingGroupVO> groups = new ArrayList<>();

    /**
     * 邮件通道状态，只回是否就绪与缺什么，不含任何口令
     */
    private MailStatusVO mailStatus;

    /**
     * 生效说明，告诉使用者改完何时能用上
     */
    private String effectiveNote;

    /**
     * 当前是否存在校验问题，存在时页面可直接提示
     */
    private Boolean hasIssues;

    private static final long serialVersionUID = 1L;
}

package com.lilac.service;

import com.lilac.domain.dto.setting.MailSettingSaveRequest;
import com.lilac.domain.dto.setting.SettingBatchSaveRequest;
import com.lilac.domain.vo.MailSettingVO;
import com.lilac.domain.vo.MailTestVO;
import com.lilac.domain.vo.SettingOverviewVO;
import com.lilac.domain.vo.SettingSaveVO;

/**
 * 参数设置业务接口，负责读取分组参数、批量保存并校验，以及邮件密钥的写入
 */
public interface SettingService {

    /**
     * 读取参数设置页的全部数据
     *
     * @return 分组参数、邮件状态与生效说明
     */
    SettingOverviewVO overview();

    /**
     * 批量保存分组参数，任一字段非法则整批拒绝并指出字段
     *
     * @param request 批量保存请求
     * @return 保存结果，含字段级错误
     */
    SettingSaveVO save(SettingBatchSaveRequest request);

    /**
     * 把指定分组的参数恢复为内置默认值
     *
     * @param group 分组名
     * @return 保存结果
     */
    SettingSaveVO resetGroup(String group);

    /**
     * 读取邮件设置，授权码只回「已配置 / 未配置」
     *
     * @return 邮件设置
     */
    MailSettingVO mailSetting();

    /**
     * 保存邮件设置，授权码写入密钥文件且永不回显
     *
     * @param request 邮件设置请求
     * @return 保存结果
     */
    SettingSaveVO saveMailSetting(MailSettingSaveRequest request);

    /**
     * 测试当前生效 SMTP 配置的连接与认证，不发送邮件
     *
     * @return 测试结果
     */
    MailTestVO testMailConnection();
}

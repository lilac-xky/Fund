package com.lilac.controller;

import com.lilac.domain.dto.setting.MailSettingSaveRequest;
import com.lilac.domain.dto.setting.SettingBatchSaveRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.MailSettingVO;
import com.lilac.domain.vo.SettingOverviewVO;
import com.lilac.domain.vo.SettingSaveVO;
import com.lilac.service.SettingService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 参数设置接口，分组参数读写与邮件设置，任何返回都不含授权码明文
 */
@RestController
@RequestMapping("/setting")
public class SettingController {

    @Resource
    private SettingService settingService;

    /**
     * 读取参数设置页的全部数据，含四个分组、邮件状态与生效说明
     */
    @GetMapping("/overview")
    public Result<SettingOverviewVO> overview() {
        return Result.success(settingService.overview());
    }

    /**
     * 批量保存参数，不合法时返回逐字段的原因且整批不落库
     */
    @PostMapping("/save")
    public Result<SettingSaveVO> save(@RequestBody SettingBatchSaveRequest request) {
        return Result.success(settingService.save(request));
    }

    /**
     * 把指定分组的参数恢复为内置默认值
     */
    @PostMapping("/reset")
    public Result<SettingSaveVO> reset(@RequestParam("group") String group) {
        return Result.success(settingService.resetGroup(group));
    }

    /**
     * 读取邮件设置，授权码只回「已配置 / 未配置」
     */
    @GetMapping("/mail")
    public Result<MailSettingVO> mailSetting() {
        return Result.success(settingService.mailSetting());
    }

    /**
     * 保存邮件设置，授权码写入密钥文件且永不回显
     */
    @PostMapping("/mail/save")
    public Result<SettingSaveVO> saveMailSetting(@RequestBody MailSettingSaveRequest request) {
        return Result.success(settingService.saveMailSetting(request));
    }
}

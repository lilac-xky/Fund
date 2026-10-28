package com.lilac.service.impl;

import com.lilac.domain.dto.param.ParamQueryRequest;
import com.lilac.domain.dto.param.ParamSaveRequest;
import com.lilac.domain.dto.setting.MailSettingSaveRequest;
import com.lilac.domain.dto.setting.SettingBatchSaveRequest;
import com.lilac.domain.dto.setting.SettingFieldError;
import com.lilac.domain.dto.setting.SettingGroupSaveRequest;
import com.lilac.domain.vo.MailSettingVO;
import com.lilac.domain.vo.MailTestVO;
import com.lilac.domain.vo.SettingGroupVO;
import com.lilac.domain.vo.SettingItemVO;
import com.lilac.domain.vo.SettingOverviewVO;
import com.lilac.domain.vo.SettingSaveVO;
import com.lilac.domain.vo.StrategyParamVO;
import com.lilac.enums.ParamGroup;
import com.lilac.mail.MailProperties;
import com.lilac.mail.SecretsWriter;
import com.lilac.mail.SmtpMailSender;
import com.lilac.mail.SmtpSettingsResolver;
import com.lilac.service.SettingService;
import com.lilac.service.StrategyParamService;
import com.lilac.setting.SettingFieldCatalog;
import com.lilac.setting.SettingValidator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数设置业务实现，读取时补默认值、保存时先校验后落库，邮件密钥只写不读
 */
@Slf4j
@Service
public class SettingServiceImpl implements SettingService {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private static final String GROUP_DIVIDEND = "DIVIDEND";

    private static final String GROUP_GRID = "GRID";

    private static final String GROUP_INDICATOR = "INDICATOR";

    private static final String GROUP_ALERT = "ALERT";

    @Resource
    private StrategyParamService strategyParamService;

    @Resource
    private SettingValidator settingValidator;

    @Resource
    private MailProperties mailProperties;

    @Resource
    private SecretsWriter secretsWriter;

    @Resource
    private SmtpSettingsResolver smtpSettingsResolver;

    @Resource
    private SmtpMailSender smtpMailSender;

    /**
     * 读取全部参数分组，库里没有的字段回落到内置默认值并标注未自定义
     *
     * @return 参数设置概览
     */
    @Override
    public SettingOverviewVO overview() {
        Map<String, String> stored = storedValues();
        List<SettingGroupVO> groups = new ArrayList<>();
        groups.add(buildGroup(ParamGroup.DIVIDEND, "红利策略", "股息率达标时补到基准仓位，减仓不得击穿保底金额", stored));
        groups.add(buildGroup(ParamGroup.GRID, "网格策略", "价格穿越网格线时给出买卖方向与金额", stored));
        groups.add(buildGroup(ParamGroup.INDICATOR, "技术指标", "已内置默认值，不改也能跑", stored));
        groups.add(buildGroup(ParamGroup.ALERT, "警示参数", "误差超阈值提示与股息率过期判定", stored));

        SettingOverviewVO overview = new SettingOverviewVO();
        overview.setGroups(groups);
        overview.setMailStatus(mailStatus());
        overview.setEffectiveNote(effectiveNote());
        overview.setHasIssues(Boolean.FALSE);
        return overview;
    }

    /**
     * 批量保存参数，先做字段级校验，有错即整批拒绝并原样返回错误
     *
     * @param request 批量保存请求
     * @return 保存结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SettingSaveVO save(SettingBatchSaveRequest request) {
        SettingSaveVO result = new SettingSaveVO();
        List<SettingGroupSaveRequest> groups = request == null || request.getGroups() == null
                ? List.of() : request.getGroups();
        if (groups.isEmpty()) {
            result.setOk(Boolean.FALSE);
            result.setMessage("没有需要保存的参数");
            return result;
        }
        List<SettingFieldError> errors = settingValidator.validate(groups, storedValues());
        if (!errors.isEmpty()) {
            // 有错就不落库，避免半套参数生效后规则自相矛盾
            result.setOk(Boolean.FALSE);
            result.setErrors(errors);
            result.setMessage("有 " + errors.size() + " 项参数不合法，未保存");
            return result;
        }
        int saved = 0;
        for (SettingGroupSaveRequest group : groups) {
            ParamGroup paramGroup = ParamGroup.valueOf(group.getGroup().trim().toUpperCase());
            if (group.getValues() == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : group.getValues().entrySet()) {
                SettingFieldCatalog.FieldDef def = SettingFieldCatalog.find(paramGroup, entry.getKey());
                if (def == null) {
                    continue;
                }
                ParamSaveRequest saveRequest = new ParamSaveRequest();
                saveRequest.setParamGroup(paramGroup);
                saveRequest.setParamKey(def.getKey());
                saveRequest.setParamValue(entry.getValue().trim());
                saveRequest.setParamDesc(def.getDescription());
                strategyParamService.saveParam(saveRequest);
                saved++;
            }
        }
        result.setOk(Boolean.TRUE);
        result.setSavedCount(saved);
        result.setMessage("已保存 " + saved + " 项参数");
        result.setEffectiveNote(effectiveNote());
        return result;
    }

    /**
     * 把分组参数清空，读取时会自动回落到内置默认值
     *
     * @param group 分组名
     * @return 保存结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SettingSaveVO resetGroup(String group) {
        SettingSaveVO result = new SettingSaveVO();
        ParamGroup paramGroup = parseGroup(group);
        if (paramGroup == null || !SettingFieldCatalog.writable(paramGroup)) {
            result.setOk(Boolean.FALSE);
            result.setMessage("未知的参数分组");
            return result;
        }
        for (SettingFieldCatalog.FieldDef def : SettingFieldCatalog.of(paramGroup)) {
            ParamSaveRequest saveRequest = new ParamSaveRequest();
            saveRequest.setParamGroup(paramGroup);
            saveRequest.setParamKey(def.getKey());
            saveRequest.setParamValue(def.getDefaultValue());
            saveRequest.setParamDesc(def.getDescription());
            if (def.getDefaultValue() == null) {
                continue;
            }
            strategyParamService.saveParam(saveRequest);
        }
        result.setOk(Boolean.TRUE);
        result.setMessage("已恢复为内置默认值");
        result.setEffectiveNote(effectiveNote());
        return result;
    }

    /**
     * 读取邮件设置，只说明授权码是否已配置，绝不返回内容
     *
     * @return 邮件设置
     */
    @Override
    public MailSettingVO mailSetting() {
        MailSettingVO vo = new MailSettingVO();
        SmtpSettingsResolver.SmtpSettings settings = smtpSettingsResolver.resolve();
        vo.setRecipients(settings.getRecipients() == null ? null : String.join(",", settings.getRecipients()));
        vo.setHost(settings.getHost());
        vo.setPort(String.valueOf(settings.getPort()));
        vo.setUsername(settings.getUsername());
        vo.setFrom(settings.getFrom());
        vo.setPasswordConfigured(settings.getPassword() != null && !settings.getPassword().isBlank());
        vo.setReady(mailProperties.isEnabled() && settings.complete());
        vo.setTestable(settings.complete());
        vo.setNote(!mailProperties.isEnabled() ? "邮件开关已关闭"
                : settings.complete() ? "当前配置完整，可以测试连接" : String.join("；", settings.getMissing()));
        return vo;
    }

    /**
     * 测试当前生效 SMTP 配置的连接与认证，不发送邮件
     *
     * @return 测试结果
     */
    @Override
    public MailTestVO testMailConnection() {
        MailTestVO result = new MailTestVO();
        SmtpSettingsResolver.SmtpSettings settings = smtpSettingsResolver.resolve();
        if (!mailProperties.isEnabled()) {
            result.setOk(Boolean.FALSE);
            result.setMessage("邮件开关已关闭");
            return result;
        }
        if (!settings.complete()) {
            result.setOk(Boolean.FALSE);
            result.setMessage(String.join("；", settings.getMissing()));
            return result;
        }
        try {
            smtpMailSender.testConnection(settings);
            result.setOk(Boolean.TRUE);
            result.setMessage("SMTP 连接与认证成功");
        } catch (RuntimeException e) {
            result.setOk(Boolean.FALSE);
            result.setMessage("SMTP 连接失败，请检查服务器、端口、账号或授权码");
            log.warn("SMTP 连接测试失败：{}", e.getClass().getSimpleName());
        }
        return result;
    }

    /**
     * 保存邮件设置，授权码为 null 表示不改，clearPassword 为 true 表示删除
     *
     * @param request 邮件设置请求
     * @return 保存结果
     */
    @Override
    public SettingSaveVO saveMailSetting(MailSettingSaveRequest request) {
        SettingSaveVO result = new SettingSaveVO();
        if (request == null) {
            result.setOk(Boolean.FALSE);
            result.setMessage("请求不能为空");
            return result;
        }
        List<SettingFieldError> errors = new ArrayList<>();
        checkMailField(errors, "host", "SMTP 服务器", request.getHost(), true);
        checkPort(errors, request.getPort());
        checkMailField(errors, "username", "账号", request.getUsername(), true);
        checkMailField(errors, "recipients", "收件地址", request.getRecipients(), true);
        if (request.getRecipients() != null && !request.getRecipients().isBlank()
                && !request.getRecipients().contains("@")) {
            errors.add(SettingFieldError.of("MAIL", "recipients", "收件地址", "收件地址必须包含 @"));
        }
        if (!errors.isEmpty()) {
            result.setOk(Boolean.FALSE);
            result.setErrors(errors);
            result.setMessage("有 " + errors.size() + " 项不合法，未保存");
            return result;
        }
        Map<String, String> values = new LinkedHashMap<>();
        if (request.getHost() != null) {
            values.put(SmtpSettingsResolver.HOST_KEY, request.getHost().trim());
        }
        if (request.getPort() != null) {
            values.put(SmtpSettingsResolver.PORT_KEY, request.getPort().trim());
        }
        if (request.getUsername() != null) {
            values.put(SmtpSettingsResolver.USERNAME_KEY, request.getUsername().trim());
        }
        if (request.getRecipients() != null) {
            values.put(SmtpSettingsResolver.RECIPIENTS_KEY, request.getRecipients().trim());
        }
        if (request.getFrom() != null) {
            values.put(SmtpSettingsResolver.FROM_KEY, request.getFrom().trim());
        }
        if (Boolean.TRUE.equals(request.getClearPassword())) {
            values.put(SmtpSettingsResolver.PASSWORD_KEY, null);
        } else if (request.getPassword() != null && !request.getPassword().isBlank()) {
            // 授权码只在写入这一侧出现，日志也不记录它
            values.put(SmtpSettingsResolver.PASSWORD_KEY, request.getPassword().trim());
        }
        SecretsWriter.WriteResult written = secretsWriter.write(mailProperties.getSecretsFile(), values);
        result.setOk(written.isOk());
        result.setMessage(written.getMessage());
        result.setSavedCount(written.isOk() ? values.size() : 0);
        result.setEffectiveNote("邮件参数在下一个收件时点生效");
        if (!written.isOk()) {
            log.warn("邮件设置写入失败：{}", written.getMessage());
        }
        return result;
    }

    /**
     * 拼接一个分组的字段，库值优先、默认值兜底
     *
     * @param group   分组
     * @param label   分组中文名
     * @param desc    分组说明
     * @param stored  库内取值
     * @return 分组视图
     */
    private SettingGroupVO buildGroup(ParamGroup group, String label, String desc, Map<String, String> stored) {
        SettingGroupVO vo = new SettingGroupVO();
        vo.setGroup(group.name());
        vo.setLabel(label);
        vo.setDescription(desc);
        List<SettingItemVO> items = new ArrayList<>();
        for (SettingFieldCatalog.FieldDef def : SettingFieldCatalog.of(group)) {
            SettingItemVO item = new SettingItemVO();
            item.setGroup(group.name());
            item.setKey(def.getKey());
            item.setLabel(def.getLabel());
            item.setDescription(def.getDescription());
            item.setUnit(def.getUnit());
            item.setType(def.getType());
            item.setDefaultValue(def.getDefaultValue());
            String current = stored.get(group.name() + "." + def.getKey());
            item.setCustomized(current != null);
            item.setValue(current != null ? current : def.getDefaultValue());
            items.add(item);
        }
        vo.setItems(items);
        return vo;
    }

    /**
     * 读出库内全部参数，键形如 DIVIDEND.baseAmount
     *
     * @return 取值映射
     */
    private Map<String, String> storedValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (StrategyParamVO vo : strategyParamService.listParam(new ParamQueryRequest())) {
            if (vo.getParamGroup() == null || vo.getParamKey() == null) {
                continue;
            }
            values.put(vo.getParamGroup().name() + "." + vo.getParamKey(), vo.getParamValue());
        }
        return values;
    }

    /**
     * 组装邮件通道状态
     *
     * @return 状态视图
     */
    private com.lilac.domain.vo.MailStatusVO mailStatus() {
        com.lilac.domain.vo.MailStatusVO status = new com.lilac.domain.vo.MailStatusVO();
        SmtpSettingsResolver.SmtpSettings settings = smtpSettingsResolver.resolve();
        status.setReady(mailProperties.isEnabled() && settings.complete());
        status.setTestable(settings.complete());
        status.setNote(!mailProperties.isEnabled() ? "邮件开关已关闭"
                : settings.complete() ? "当前配置完整，可以测试连接" : String.join("；", settings.getMissing()));
        return status;
    }

    /**
     * 校验邮件字段非空
     *
     * @param errors   错误清单
     * @param field    字段名
     * @param label    字段中文名
     * @param value    值
     * @param required 是否必填
     */
    private void checkMailField(List<SettingFieldError> errors, String field, String label,
                                String value, boolean required) {
        if (required && (value == null || value.isBlank())) {
            errors.add(SettingFieldError.of("MAIL", field, label, label + "不能为空"));
        }
    }

    /**
     * 校验端口，须在 1~65535 之间且为整数
     *
     * @param errors 错误清单
     * @param port   端口
     */
    private void checkPort(List<SettingFieldError> errors, String port) {
        if (port == null || port.isBlank()) {
            errors.add(SettingFieldError.of("MAIL", "port", "端口", "端口不能为空"));
            return;
        }
        try {
            int value = Integer.parseInt(port.trim());
            if (value <= 0 || value > 65535) {
                errors.add(SettingFieldError.of("MAIL", "port", "端口", "端口应在 1~65535 之间"));
            }
        } catch (NumberFormatException e) {
            errors.add(SettingFieldError.of("MAIL", "port", "端口", "端口必须是整数"));
        }
    }

    /**
     * 说明改动何时生效，用页面能直接显示的一句话
     *
     * @return 生效说明
     */
    private String effectiveNote() {
        return "改动在保存成功后的下一个定时时点生效，当日已发出的邮件不会重发（当前 " 
                + LocalDateTime.now().format(CLOCK) + "）";
    }

    /**
     * 取第一个非空值
     *
     * @param first  首选
     * @param second 兜底
     * @return 取值
     */
    private String firstNotBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    /**
     * 把地址列表拼成逗号分隔的字符串
     *
     * @param items 地址
     * @return 逗号分隔文本，空列表返回 null
     */
    private String join(List<String> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return String.join(",", items);
    }

    /**
     * 解析分组名
     *
     * @param name 分组名
     * @return 分组，未知返回 null
     */
    private ParamGroup parseGroup(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return ParamGroup.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

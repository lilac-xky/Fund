package com.lilac.setting;

import com.lilac.domain.dto.setting.MailSettingSaveRequest;
import com.lilac.domain.dto.setting.SettingBatchSaveRequest;
import com.lilac.domain.dto.setting.SettingFieldError;
import com.lilac.domain.dto.setting.SettingGroupSaveRequest;
import com.lilac.domain.vo.MailSettingVO;
import com.lilac.domain.vo.SettingGroupVO;
import com.lilac.domain.vo.SettingItemVO;
import com.lilac.domain.vo.SettingOverviewVO;
import com.lilac.domain.vo.SettingSaveVO;
import com.lilac.enums.ParamGroup;
import com.lilac.mapper.StrategyParamMapper;
import com.lilac.service.SettingService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 参数设置测试，核心是「非法值逐字段拒绝」「指标有默认值」与「授权码只写不回显」
 */
@SpringBootTest
@Transactional
class SettingServiceTest {

    @Autowired
    private SettingService settingService;

    @Autowired
    private StrategyParamMapper strategyParamMapper;

    /**
     * 清空策略参数，避免用例之间串数据
     */
    @BeforeEach
    void setUp() {
        // MyBatis-Flex 不允许无条件删除，按分组逐个清
        for (ParamGroup group : ParamGroup.values()) {
            strategyParamMapper.deleteByQuery(QueryWrapper.create()
                    .eq("paramGroup", group.name()));
        }
    }

    /**
     * 概览返回四个分组，且指标分组在没有库值时能给出默认值
     */
    @Test
    @DisplayName("指标参数有可用默认值，不改也能跑")
    void indicatorHasDefaults() {
        SettingOverviewVO overview = settingService.overview();

        assertThat(overview.getGroups()).hasSize(4);
        SettingGroupVO indicator = group(overview, "INDICATOR");
        assertThat(indicator.getItems()).isNotEmpty();
        for (SettingItemVO item : indicator.getItems()) {
            assertThat(item.getValue()).as("%s 必须有默认值", item.getKey()).isNotBlank();
            assertThat(item.getCustomized()).isFalse();
        }
        SettingItemVO rsi = item(indicator, "rsiPeriod");
        assertThat(rsi.getValue()).isEqualTo("14");
        assertThat(rsi.getDefaultValue()).isEqualTo("14");
    }

    /**
     * 合法参数能保存，刷新后读到的仍是保存值
     */
    @Test
    @DisplayName("全部参数可保存并持久化")
    void saveThenReload() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("baseAmount", "120000");
        values.put("floorAmount", "60000");
        values.put("addYield", "5.00");
        values.put("reduceYield", "3.00");
        SettingSaveVO result = settingService.save(batch("DIVIDEND", values));

        assertThat(result.getOk()).isTrue();
        assertThat(result.getSavedCount()).isEqualTo(4);

        SettingGroupVO dividend = group(settingService.overview(), "DIVIDEND");
        assertThat(item(dividend, "baseAmount").getValue()).isEqualTo("120000");
        assertThat(item(dividend, "baseAmount").getCustomized()).isTrue();
        assertThat(item(dividend, "floorAmount").getValue()).isEqualTo("60000");
    }

    /**
     * 保底高于基准必须被拒绝，且错误落在「保底分红金额」这个字段上
     */
    @Test
    @DisplayName("保底高于基准时拒绝保存并指出字段")
    void rejectFloorAboveBase() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("baseAmount", "120000");
        values.put("floorAmount", "130000");
        values.put("addYield", "5.00");
        values.put("reduceYield", "3.00");
        SettingSaveVO result = settingService.save(batch("DIVIDEND", values));

        assertThat(result.getOk()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors()).anyMatch(error -> "floorAmount".equals(error.getField()));
        assertThat(result.getErrors()).anyMatch(error -> error.getMessage().contains("不得高于基准仓位"));
        // 整批拒绝，库里不能留下半套参数
        assertThat(item(group(settingService.overview(), "DIVIDEND"), "baseAmount").getCustomized()).isFalse();
    }

    /**
     * 阈值顺序颠倒必须被拒绝
     */
    @Test
    @DisplayName("减仓阈值不低于加仓阈值时拒绝保存")
    void rejectYieldOrder() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("baseAmount", "120000");
        values.put("floorAmount", "60000");
        values.put("addYield", "3.00");
        values.put("reduceYield", "5.00");
        SettingSaveVO result = settingService.save(batch("DIVIDEND", values));

        assertThat(result.getOk()).isFalse();
        assertThat(result.getErrors()).anyMatch(error -> "reduceYield".equals(error.getField()));
    }

    /**
     * 份额为负、成本为 0 这类值在网格与金额字段上同样被拒绝
     */
    @Test
    @DisplayName("负数与零被逐字段拒绝")
    void rejectNonPositive() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("lowerBound", "0");
        values.put("upperBound", "-5");
        values.put("step", "abc");
        values.put("amountPerGrid", "0");
        SettingSaveVO result = settingService.save(batch("GRID", values));

        assertThat(result.getOk()).isFalse();
        List<String> fields = result.getErrors().stream().map(SettingFieldError::getField).toList();
        assertThat(fields).contains("lowerBound", "upperBound", "step", "amountPerGrid");
    }

    /**
     * 网格上下界与格子大小划不出两格时拒绝保存
     */
    @Test
    @DisplayName("格子过大导致划不出网格时拒绝保存")
    void rejectTooFewGrids() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("lowerBound", "3.200");
        values.put("upperBound", "4.600");
        values.put("step", "5.000");
        values.put("amountPerGrid", "2000");
        SettingSaveVO result = settingService.save(batch("GRID", values));

        assertThat(result.getOk()).isFalse();
        assertThat(result.getErrors()).anyMatch(error -> "step".equals(error.getField()));
    }

    /**
     * MACD 慢线必须大于快线
     */
    @Test
    @DisplayName("MACD 快线不小于慢线时拒绝保存")
    void rejectMacdOrder() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("macdFast", "26");
        values.put("macdSlow", "12");
        SettingSaveVO result = settingService.save(batch("INDICATOR", values));

        assertThat(result.getOk()).isFalse();
        assertThat(result.getErrors()).anyMatch(error -> "macdSlow".equals(error.getField()));
    }

    /**
     * 邮件设置读取时只说明授权码是否配置，返回值里根本不存在口令字段，也不暴露本地路径
     */
    @Test
    @DisplayName("邮件设置任何返回都不含授权码明文与本地路径")
    void mailSettingNeverReturnsPassword() {
        MailSettingVO vo = settingService.mailSetting();

        assertThat(vo.getPasswordConfigured()).isNotNull();
        // 只允许出现 boolean 形式的状态字段，不允许有 String 形式的明文载体
        assertThat(plaintextPasswordFields(MailSettingVO.class)).isEmpty();
        // 页面不需要知道密钥文件在哪，任何返回都不应出现本地路径字段
        assertThat(vo.getClass().getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("secrets")
                        || field.getName().toLowerCase().contains("file"));
    }

    /**
     * 授权码写入后状态变为已配置，但读取仍不返回内容
     */
    @Test
    @DisplayName("授权码可写入且写入后只回「已配置」")
    void passwordWrittenButNeverEchoed() {
        String marker = UUID.randomUUID().toString();
        MailSettingSaveRequest request = new MailSettingSaveRequest();
        request.setHost("smtp.example.com");
        request.setPort("465");
        request.setUsername("me@example.com");
        request.setRecipients("me@example.com");
        request.setPassword(marker);
        SettingSaveVO saved = settingService.saveMailSetting(request);

        assertThat(saved.getOk()).isTrue();
        MailSettingVO vo = settingService.mailSetting();
        assertThat(vo.getPasswordConfigured()).isTrue();
        assertThat(vo.getNote() == null ? "" : vo.getNote()).doesNotContain(marker);
    }

    /**
     * 端口非法时拒绝保存并指出字段
     */
    @Test
    @DisplayName("端口非法时逐字段拒绝")
    void rejectBadPort() {
        MailSettingSaveRequest request = new MailSettingSaveRequest();
        request.setHost("smtp.example.com");
        request.setPort("70000");
        request.setUsername("me@example.com");
        request.setRecipients("me@example.com");
        SettingSaveVO result = settingService.saveMailSetting(request);

        assertThat(result.getOk()).isFalse();
        assertThat(result.getErrors()).anyMatch(error -> "port".equals(error.getField()));
    }

    /**
     * 恢复默认后回到内置默认值
     */
    @Test
    @DisplayName("恢复默认后回到内置值")
    void resetGroupRestoresDefaults() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("rsiPeriod", "20");
        settingService.save(batch("INDICATOR", values));
        assertThat(item(group(settingService.overview(), "INDICATOR"), "rsiPeriod").getValue()).isEqualTo("20");

        settingService.resetGroup("INDICATOR");
        SettingItemVO rsi = item(group(settingService.overview(), "INDICATOR"), "rsiPeriod");
        assertThat(rsi.getValue()).isEqualTo("14");
    }

    /**
     * 组装批量保存请求
     *
     * @param group  分组名
     * @param values 参数值
     * @return 批量请求
     */
    private SettingBatchSaveRequest batch(String group, Map<String, String> values) {
        SettingGroupSaveRequest one = new SettingGroupSaveRequest();
        one.setGroup(group);
        one.setValues(values);
        SettingBatchSaveRequest request = new SettingBatchSaveRequest();
        request.setGroups(List.of(one));
        return request;
    }

    /**
     * 按分组名取分组
     *
     * @param overview 概览
     * @param group    分组名
     * @return 分组
     */
    private SettingGroupVO group(SettingOverviewVO overview, String group) {
        return overview.getGroups().stream()
                .filter(item -> group.equals(item.getGroup()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少分组 " + group));
    }

    /**
     * 按参数键取字段
     *
     * @param group 分组
     * @param key   参数键
     * @return 字段
     */
    private SettingItemVO item(SettingGroupVO group, String key) {
        return group.getItems().stream()
                .filter(item -> key.equals(item.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少字段 " + key));
    }

    /**
     * 找出可能承载口令明文的字符串字段，布尔状态字段不算
     *
     * @param type 类型
     * @return 命中的字段名
     */
    private List<String> plaintextPasswordFields(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredFields())
                .filter(field -> field.getName().toLowerCase().contains("password"))
                .filter(field -> field.getType() == String.class)
                .map(java.lang.reflect.Field::getName)
                .toList();
    }

    /**
     * 确认参数分组枚举与页面分组一致
     */
    @Test
    @DisplayName("四个分组与枚举一一对应")
    void groupsMatchEnum() {
        SettingOverviewVO overview = settingService.overview();
        List<String> names = overview.getGroups().stream().map(SettingGroupVO::getGroup).toList();
        assertThat(names).containsExactly(ParamGroup.DIVIDEND.name(), ParamGroup.GRID.name(),
                ParamGroup.INDICATOR.name(), ParamGroup.ALERT.name());
    }
}

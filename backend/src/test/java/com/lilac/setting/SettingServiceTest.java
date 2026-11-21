package com.lilac.setting;

import com.lilac.domain.dto.setting.MailSettingSaveRequest;
import com.lilac.domain.dto.setting.SettingBatchSaveRequest;
import com.lilac.domain.dto.setting.SettingGroupSaveRequest;
import com.lilac.domain.vo.MailSettingVO;
import com.lilac.domain.vo.SettingGroupVO;
import com.lilac.domain.vo.SettingItemVO;
import com.lilac.domain.vo.SettingOverviewVO;
import com.lilac.domain.vo.SettingSaveVO;
import com.lilac.enums.ParamGroup;
import com.lilac.mapper.StrategyParamMapper;
import com.lilac.service.SettingService;
import com.lilac.strategy.DividendPlanResolver;
import com.lilac.strategy.GridPlanResolver;
import com.lilac.strategy.model.DividendPlan;
import com.lilac.strategy.model.GridPlan;
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
 * 参数设置测试，核心是「非法值逐字段拒绝」「指标有默认值」「按标的的参数不走全局表」与「授权码只写不回显」
 */
@SpringBootTest
@Transactional
class SettingServiceTest {

    @Autowired
    private SettingService settingService;

    @Autowired
    private StrategyParamMapper strategyParamMapper;

    @Autowired
    private DividendPlanResolver dividendPlanResolver;

    @Autowired
    private GridPlanResolver gridPlanResolver;

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
     * 概览返回三个分组，且指标分组在没有库值时能给出默认值
     */
    @Test
    @DisplayName("指标参数有可用默认值，不改也能跑")
    void indicatorHasDefaults() {
        SettingOverviewVO overview = settingService.overview();

        assertThat(overview.getGroups()).hasSize(3);
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
        values.put("rsiPeriod", "20");
        values.put("kdjPeriod", "9");
        values.put("macdFast", "12");
        values.put("macdSlow", "26");
        SettingSaveVO result = settingService.save(batch("INDICATOR", values));

        assertThat(result.getOk()).isTrue();
        assertThat(result.getSavedCount()).isEqualTo(4);

        SettingGroupVO indicator = group(settingService.overview(), "INDICATOR");
        assertThat(item(indicator, "rsiPeriod").getValue()).isEqualTo("20");
        assertThat(item(indicator, "rsiPeriod").getCustomized()).isTrue();
        assertThat(item(indicator, "macdSlow").getValue()).isEqualTo("26");
    }

    /**
     * 红利与网格参数按基金、按标的各存一套，不允许从全局参数表写入
     */
    @Test
    @DisplayName("红利与网格分组不接受全局参数保存")
    void perVarietyGroupsRejected() {
        Map<String, String> dividend = new LinkedHashMap<>();
        dividend.put("baseAmount", "120000");
        SettingSaveVO dividendResult = settingService.save(batch("DIVIDEND", dividend));

        assertThat(dividendResult.getOk()).isFalse();
        assertThat(dividendResult.getErrors()).anyMatch(error -> error.getMessage().contains("不支持通过参数表保存"));

        Map<String, String> grid = new LinkedHashMap<>();
        grid.put("lowerBound", "3.60");
        SettingSaveVO gridResult = settingService.save(batch("GRID", grid));

        assertThat(gridResult.getOk()).isFalse();
        assertThat(gridResult.getErrors()).anyMatch(error -> error.getMessage().contains("不支持通过参数表保存"));
        // 拒写之后库里不能留下任何一行红利或网格参数
        assertThat(strategyParamMapper.selectCountByQuery(QueryWrapper.create()
                .eq("paramGroup", ParamGroup.DIVIDEND.name()))).isZero();
        assertThat(strategyParamMapper.selectCountByQuery(QueryWrapper.create()
                .eq("paramGroup", ParamGroup.GRID.name()))).isZero();
    }

    /**
     * 股息率阈值顺序颠倒必须被拒绝，这条规则现在落在按基金的红利参数上
     */
    @Test
    @DisplayName("减仓阈值不低于加仓阈值时拒绝保存")
    void rejectYieldOrder() {
        DividendPlan plan = new DividendPlan();
        plan.setVarietyKey("OF:007467");
        plan.setBaseAmount(new java.math.BigDecimal("120000"));
        plan.setFloorAmount(new java.math.BigDecimal("60000"));
        plan.setAddYield(new java.math.BigDecimal("3.00"));
        plan.setReduceYield(new java.math.BigDecimal("5.00"));

        assertThat(dividendPlanResolver.validate(plan))
                .anyMatch(text -> text.contains("必须低于加仓阈值"));
    }

    /**
     * 网格上下界与格子大小划不出两格时拒绝保存，这条规则现在落在按标的的网格参数上
     */
    @Test
    @DisplayName("格子过大导致划不出网格时拒绝保存")
    void rejectTooFewGrids() {
        GridPlan plan = new GridPlan();
        plan.setVarietyKey("ETF:510300");
        plan.setLowerBound(new java.math.BigDecimal("3.200"));
        plan.setUpperBound(new java.math.BigDecimal("4.600"));
        plan.setStep(new java.math.BigDecimal("5.000"));
        plan.setAmountPerGrid(new java.math.BigDecimal("2000"));

        assertThat(gridPlanResolver.validate(plan)).anyMatch(text -> text.contains("至少需要"));
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
    @DisplayName("五个分组与枚举一一对应")
    void groupsMatchEnum() {
        SettingOverviewVO overview = settingService.overview();
        List<String> names = overview.getGroups().stream().map(SettingGroupVO::getGroup).toList();
        // 红利与网格参数按基金、按标的各存一套，不在这张全局参数表里
        assertThat(names).containsExactly(ParamGroup.BOND.name(), ParamGroup.INDICATOR.name(),
                ParamGroup.ALERT.name());
    }
}

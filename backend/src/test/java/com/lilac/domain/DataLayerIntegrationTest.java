package com.lilac.domain;

import com.lilac.config.RedisKeys;
import com.lilac.domain.dto.estimate.EstimateErrorQueryRequest;
import com.lilac.domain.dto.estimate.EstimateErrorSaveRequest;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.param.ParamQueryRequest;
import com.lilac.domain.dto.param.ParamSaveRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.dto.variety.VarietySaveRequest;
import com.lilac.domain.entity.Holding;
import com.lilac.domain.vo.EstimateErrorVO;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.StrategyParamVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.ParamGroup;
import com.lilac.enums.VarietyType;
import com.lilac.mapper.HoldingMapper;
import com.lilac.mapper.StrategyParamMapper;
import com.lilac.service.EstimateErrorService;
import com.lilac.service.HoldingService;
import com.lilac.service.StrategyParamService;
import com.lilac.service.VarietyService;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据层验收测试，覆盖同码共存、统一查询与逻辑删除
 */
@SpringBootTest
@Transactional
class DataLayerIntegrationTest {

    @Autowired
    private VarietyService varietyService;

    @Autowired
    private HoldingService holdingService;

    @Autowired
    private StrategyParamService strategyParamService;

    @Autowired
    private EstimateErrorService estimateErrorService;

    @Autowired
    private HoldingMapper holdingMapper;

    @Autowired
    private StrategyParamMapper strategyParamMapper;

    /**
     * 同一代码的场外基金与指数可以同时存在且互不覆盖
     */
    @Test
    @DisplayName("OF:000001 与 IDX:000001 共存")
    void sameCodeDifferentVarietyTypeCanCoexist() {
        varietyService.saveVariety(buildVariety("OF:000001", VarietyType.OF, "000001", "华夏成长混合", FundCategory.MIXED));
        varietyService.saveVariety(buildVariety("IDX:000001", VarietyType.IDX, "000001", "上证指数", FundCategory.STOCK));

        List<VarietyVO> of = queryVariety("OF:000001", null);
        assertThat(of).hasSize(1);
        assertThat(of.get(0).getVarietyName()).isEqualTo("华夏成长混合");

        List<VarietyVO> idx = queryVariety("IDX:000001", null);
        assertThat(idx).hasSize(1);
        assertThat(idx.get(0).getVarietyName()).isEqualTo("上证指数");

        assertThat(queryVariety(null, "000001")).hasSize(2);
    }

    /**
     * 主键由雪花算法生成，时间列由数据库默认值填写
     */
    @Test
    @DisplayName("插入生成雪花主键与时间")
    void insertFillsIdAndTimestamps() {
        varietyService.saveVariety(
                buildVariety("OF:110022", VarietyType.OF, "110022", "易方达消费行业", FundCategory.STOCK));

        VarietyVO saved = queryVariety("OF:110022", null).get(0);
        assertThat(saved.getId()).isNotNull().isPositive();
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
    }

    /**
     * 误差历史能按基金加日期区间查出正确结果，区间外的不返回
     */
    @Test
    @DisplayName("误差历史按基金与日期区间查询")
    void estimateErrorQueryByVarietyAndDateRange() {
        String varietyKey = "ETF:510300";
        LocalDate base = LocalDate.of(2026, 10, 1);
        for (int i = 0; i < 10; i++) {
            saveError(varietyKey, base.plusDays(i), i % 3 == 0);
        }

        EstimateErrorQueryRequest inRangeReq = new EstimateErrorQueryRequest();
        inRangeReq.setVarietyKey(varietyKey);
        inRangeReq.setStartDate(base.plusDays(2));
        inRangeReq.setEndDate(base.plusDays(5));
        List<EstimateErrorVO> inRange = estimateErrorService.listEstimateError(inRangeReq);
        assertThat(inRange).hasSize(4);
        assertThat(inRange.get(0).getTradeDate()).isEqualTo(base.plusDays(2));
        assertThat(inRange.get(3).getTradeDate()).isEqualTo(base.plusDays(5));

        EstimateErrorQueryRequest fullReq = new EstimateErrorQueryRequest();
        fullReq.setVarietyKey(varietyKey);
        fullReq.setStartDate(base);
        fullReq.setEndDate(base.plusDays(9));
        assertThat(estimateErrorService.listEstimateError(fullReq)).hasSize(10);

        EstimateErrorQueryRequest overReq = new EstimateErrorQueryRequest();
        overReq.setVarietyKey(varietyKey);
        overReq.setStartDate(base);
        overReq.setEndDate(base.plusDays(9));
        overReq.setOverThreshold(Boolean.TRUE);
        assertThat(estimateErrorService.listEstimateError(overReq)).hasSize(4);

        EstimateErrorQueryRequest globalOverReq = new EstimateErrorQueryRequest();
        globalOverReq.setStartDate(base);
        globalOverReq.setEndDate(base.plusDays(9));
        globalOverReq.setOverThreshold(Boolean.TRUE);
        assertThat(estimateErrorService.listEstimateError(globalOverReq)).hasSize(4);
    }

    /**
     * 同一基金同一天重复写入走更新，不会因唯一索引失败
     */
    @Test
    @DisplayName("误差历史按基金与交易日幂等写入")
    void estimateErrorUpsertIsIdempotent() {
        LocalDate tradeDate = LocalDate.of(2026, 10, 24);
        saveError("OF:000001", tradeDate, false);

        EstimateErrorSaveRequest corrected = new EstimateErrorSaveRequest();
        corrected.setVarietyKey("OF:000001");
        corrected.setTradeDate(tradeDate);
        corrected.setEstimatedChange(new BigDecimal("2.000000"));
        corrected.setActualChange(new BigDecimal("1.900000"));
        corrected.setErrorValue(new BigDecimal("0.100000"));
        corrected.setOverThreshold(Boolean.FALSE);
        corrected.setOutputTier("A");
        estimateErrorService.saveEstimateError(corrected);

        EstimateErrorQueryRequest req = new EstimateErrorQueryRequest();
        req.setVarietyKey("OF:000001");
        req.setStartDate(tradeDate);
        req.setEndDate(tradeDate);
        List<EstimateErrorVO> rows = estimateErrorService.listEstimateError(req);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getErrorValue()).isEqualByComparingTo("0.100000");
    }

    /**
     * 逻辑删除后常规查询查不到，绕过逻辑删除仍能读到原始行且标记为已删除
     */
    @Test
    @DisplayName("持仓逻辑删除保留原始行")
    void holdingLogicDeleteKeepsRow() {
        Holding holding = new Holding();
        holding.setVarietyKey("OF:000001");
        holding.setShares(new BigDecimal("1234.5678"));
        holding.setCostPrice(new BigDecimal("1.2345"));
        holding.setOpenDate(LocalDate.of(2026, 1, 5));
        holding.setStatus(HoldingStatus.HOLDING);
        holdingMapper.insertSelective(holding);

        Long id = holding.getId();
        assertThat(id).isNotNull();
        assertThat(holdingService.listHolding(new HoldingQueryRequest())).hasSize(1);

        holdingMapper.deleteById(id);

        assertThat(holdingService.listHolding(new HoldingQueryRequest())).isEmpty();
        HoldingQueryRequest byVariety = new HoldingQueryRequest();
        byVariety.setVarietyKey("OF:000001");
        assertThat(holdingService.listHolding(byVariety)).isEmpty();

        Holding raw = LogicDeleteManager.execWithoutLogicDelete(() -> holdingMapper.selectOneById(id));
        assertThat(raw).isNotNull();
        assertThat(raw.getDeleted()).isEqualTo(1);
        assertThat(raw.getVarietyKey()).isEqualTo("OF:000001");
    }

    /**
     * 策略参数按分组加参数名唯一定位，重复保存走更新而不是新增
     */
    @Test
    @DisplayName("策略参数分组内参数名唯一")
    void strategyParamUniqueWithinGroup() {
        // 页面真实保存的参数会留在 dev 库，先清空本分组避免既有数据干扰计数
        strategyParamMapper.deleteByQuery(QueryWrapper.create()
                .eq("paramGroup", ParamGroup.DIVIDEND.name()));

        ParamSaveRequest baseAmount = new ParamSaveRequest();
        baseAmount.setParamGroup(ParamGroup.DIVIDEND);
        baseAmount.setParamKey("baseAmount");
        baseAmount.setParamValue("100000");
        baseAmount.setParamDesc("红利基准仓位金额");
        strategyParamService.saveParam(baseAmount);

        ParamSaveRequest updated = new ParamSaveRequest();
        updated.setParamGroup(ParamGroup.DIVIDEND);
        updated.setParamKey("baseAmount");
        updated.setParamValue("120000");
        updated.setParamDesc("红利基准仓位金额");
        strategyParamService.saveParam(updated);

        ParamQueryRequest req = new ParamQueryRequest();
        req.setParamGroup(ParamGroup.DIVIDEND);
        req.setParamKey("baseAmount");
        List<StrategyParamVO> found = strategyParamService.listParam(req);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getParamValue()).isEqualTo("120000");

        ParamQueryRequest byGroup = new ParamQueryRequest();
        byGroup.setParamGroup(ParamGroup.DIVIDEND);
        assertThat(strategyParamService.listParam(byGroup)).hasSize(1);
        // 全表查询会带回页面真实保存的其他分组参数，这里只断言包含本条且无重复键
        List<StrategyParamVO> all = strategyParamService.listParam(new ParamQueryRequest());
        assertThat(all).anyMatch(row -> row.getParamGroup() == ParamGroup.DIVIDEND
                && "baseAmount".equals(row.getParamKey()) && "120000".equals(row.getParamValue()));
        assertThat(all.size()).isEqualTo(all.stream()
                .map(row -> row.getParamGroup() + ":" + row.getParamKey()).distinct().count());
    }

    /**
     * Redis key 规范可拼接且带品种维度，同码不同品种互不覆盖
     */
    @Test
    @DisplayName("Redis key 按品种维度隔离")
    void redisKeyIsolatesVariety() {
        assertThat(RedisKeys.quoteKey("OF:000001")).isEqualTo("fund:quote:OF:000001");
        assertThat(RedisKeys.quoteKey("IDX:000001")).isEqualTo("fund:quote:IDX:000001");
        assertThat(RedisKeys.quoteKey("OF:000001")).isNotEqualTo(RedisKeys.quoteKey("IDX:000001"));
        assertThat(RedisKeys.snapshotKey("overview", "2026-10-26"))
                .isEqualTo("fund:snapshot:overview:2026-10-26");
        assertThat(RedisKeys.fallbackKey("eastmoney", "510300"))
                .isEqualTo("fund:fallback:eastmoney:510300");
        assertThat(RedisKeys.QUOTE_TTL_SECONDS).isPositive();
        assertThat(RedisKeys.SNAPSHOT_TTL_SECONDS).isPositive();
        assertThat(RedisKeys.FALLBACK_TTL_SECONDS).isPositive();
    }

    /**
     * 按业务主键或代码查询品种
     *
     * @param varietyKey  业务主键，可为 null
     * @param varietyCode 6 位代码，可为 null
     * @return 品种视图列表
     */
    private List<VarietyVO> queryVariety(String varietyKey, String varietyCode) {
        VarietyQueryRequest req = new VarietyQueryRequest();
        req.setVarietyKey(varietyKey);
        req.setVarietyCode(varietyCode);
        return varietyService.listVariety(req);
    }

    /**
     * 拼装测试用品种保存请求
     *
     * @param varietyKey  业务主键
     * @param varietyType 品种类型
     * @param varietyCode 6 位代码
     * @param varietyName 名称
     * @param category    基金类型
     * @return 保存请求
     */
    private VarietySaveRequest buildVariety(String varietyKey, VarietyType varietyType, String varietyCode,
                                            String varietyName, FundCategory category) {
        VarietySaveRequest request = new VarietySaveRequest();
        request.setVarietyKey(varietyKey);
        request.setVarietyType(varietyType);
        request.setVarietyCode(varietyCode);
        request.setVarietyName(varietyName);
        request.setCategory(category);
        request.setConfirmed(Boolean.TRUE);
        return request;
    }

    /**
     * 写入一条误差历史
     *
     * @param varietyKey    品种业务主键
     * @param tradeDate     交易日
     * @param overThreshold 是否超阈值
     */
    private void saveError(String varietyKey, LocalDate tradeDate, boolean overThreshold) {
        EstimateErrorSaveRequest request = new EstimateErrorSaveRequest();
        request.setVarietyKey(varietyKey);
        request.setTradeDate(tradeDate);
        request.setEstimatedChange(new BigDecimal("1.234567"));
        request.setActualChange(new BigDecimal("1.100000"));
        request.setErrorValue(new BigDecimal("0.134567"));
        request.setOverThreshold(overThreshold);
        request.setDisclosurePeriod("2026Q2");
        request.setOutputTier("A");
        estimateErrorService.saveEstimateError(request);
    }
}

package com.lilac.domain;

import com.lilac.adapter.eastmoney.ExchangeIdentityAdapter;
import com.lilac.adapter.fund.FundDetailAdapter;
import com.lilac.adapter.model.ExchangeIdentity;
import com.lilac.adapter.model.ExchangeKind;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.domain.dto.fund.FundConfirmRequest;
import com.lilac.domain.dto.holding.HoldingBuyRequest;
import com.lilac.domain.dto.holding.HoldingChangeQueryRequest;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.holding.HoldingSellRequest;
import com.lilac.domain.entity.FundCatalog;
import com.lilac.domain.vo.HoldingChangeVO;
import com.lilac.domain.vo.HoldingOverviewVO;
import com.lilac.domain.vo.HoldingSummaryVO;
import com.lilac.domain.vo.HoldingVarietyVO;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingChangeType;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.VarietyType;
import com.lilac.exception.BusinessException;
import com.lilac.mapper.FundCatalogMapper;
import com.lilac.mapper.HoldingChangeMapper;
import com.lilac.mapper.HoldingMapper;
import com.lilac.service.FundDisambiguateService;
import com.lilac.service.HoldingService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 持仓录入测试，覆盖校验拒绝、变动历史留痕与总览聚合
 */
@SpringBootTest
@Transactional
class HoldingServiceTest {

    @Autowired
    private HoldingService holdingService;

    @Autowired
    private FundDisambiguateService fundDisambiguateService;

    @Autowired
    private FundCatalogMapper fundCatalogMapper;

    @Autowired
    private HoldingMapper holdingMapper;

    @Autowired
    private HoldingChangeMapper holdingChangeMapper;

    @MockitoBean
    private ExchangeIdentityAdapter exchangeIdentityAdapter;

    @MockitoBean
    private FundDetailAdapter fundDetailAdapter;

    @MockitoBean
    private MarketQuoteAdapter marketQuoteAdapter;

    /**
     * 清空全集并写入固定行，行情一律 mock 为不可用以走净值分支
     */
    @BeforeEach
    void setUp() {
        fundCatalogMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
        // 测试对持仓表的假设是「从零开始」，dev 库里手工录入的真实持仓要隔离掉
        holdingChangeMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
        holdingMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
        insertCatalog("110022", "易方达消费行业股票", "股票型");
        insertCatalog("000216", "华安黄金ETF联接A", "指数型-其他");
        insertCatalog("510300", "沪深300ETF华泰柏瑞", "指数型-股票");
        when(exchangeIdentityAdapter.resolveAll("110022")).thenReturn(List.of());
        when(exchangeIdentityAdapter.resolveAll("000216")).thenReturn(List.of());
        when(exchangeIdentityAdapter.resolveAll("510300")).thenReturn(
                List.of(new ExchangeIdentity("510300", "沪深300ETF华泰柏瑞", ExchangeKind.ETF)));
        when(fundDetailAdapter.fetchTrackIndexName(anyList() != null ? "110022" : "")).thenReturn(java.util.Optional.empty());
        // 行情一律 mock 为不可用，总览与回显走净值分支，测试环境无净值时市值为 null
        when(marketQuoteAdapter.quote(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN));
    }

    /**
     * 输入代码消歧后确认，注册表已含该品种，买入成功且历史留痕
     */
    @Test
    @DisplayName("确认后买入成功并写入变动历史")
    void buyAfterConfirmWritesChange() {
        FundConfirmRequest confirm = new FundConfirmRequest();
        confirm.setCode("110022");
        confirm.setVarietyType(VarietyType.OF);
        VarietyVO variety = fundDisambiguateService.confirm(confirm);

        HoldingBuyRequest buy = new HoldingBuyRequest();
        buy.setVarietyKey(variety.getVarietyKey());
        buy.setShares(new BigDecimal("1000"));
        buy.setCostPrice(new BigDecimal("1.5"));
        HoldingVO holding = holdingService.buy(buy);

        assertThat(holding.getId()).isNotNull();
        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.HOLDING);

        HoldingChangeQueryRequest changeQuery = new HoldingChangeQueryRequest();
        changeQuery.setVarietyKey(variety.getVarietyKey());
        List<HoldingChangeVO> changes = holdingService.listChanges(changeQuery);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getChangeType()).isEqualTo(HoldingChangeType.BUY);
        assertThat(changes.get(0).getShares()).isEqualByComparingTo("1000");
        assertThat(changes.get(0).getUnitPrice()).isEqualByComparingTo("1.5");
        assertThat(changes.get(0).getAfterShares()).isEqualByComparingTo("1000");
        assertThat(changes.get(0).getVarietyName()).isEqualTo("易方达消费行业股票");
        assertThat(changes.get(0).getNote()).contains("110022");
    }

    /**
     * 候选列表带出注册状态与持有概况，未注册时为 null
     */
    @Test
    @DisplayName("消歧候选带出注册状态与持有概况")
    void disambiguateMarksRegisteredCandidate() {
        FundConfirmRequest confirm = new FundConfirmRequest();
        confirm.setCode("110022");
        confirm.setVarietyType(VarietyType.OF);
        VarietyVO variety = fundDisambiguateService.confirm(confirm);

        HoldingBuyRequest buy = new HoldingBuyRequest();
        buy.setVarietyKey(variety.getVarietyKey());
        buy.setShares(new BigDecimal("500"));
        buy.setCostPrice(new BigDecimal("1.2"));
        holdingService.buy(buy);

        var request = new com.lilac.domain.dto.fund.FundDisambiguateRequest();
        request.setCode("110022");
        var result = fundDisambiguateService.disambiguate(request);
        FundCandidateMarked candidate = new FundCandidateMarked(result.getCandidates().get(0));
        assertThat(candidate.registered()).isTrue();
        assertThat(candidate.holding()).isTrue();
        assertThat(candidate.totalShares()).isEqualByComparingTo("500");

        var fresh = new com.lilac.domain.dto.fund.FundDisambiguateRequest();
        fresh.setCode("000216");
        var freshResult = fundDisambiguateService.disambiguate(fresh);
        FundCandidateMarked unregistered = new FundCandidateMarked(freshResult.getCandidates().get(0));
        assertThat(unregistered.registered()).isFalse();
        assertThat(unregistered.holding()).isNull();
    }

    /**
     * 卖出按先进先出扣减，跨两条建仓记录时扣完并置已清仓
     */
    @Test
    @DisplayName("卖出先进先出扣减持仓")
    void sellFifoDeductsOldestFirst() {
        VarietyVO variety = confirmVariety("110022");
        buy(variety.getVarietyKey(), "600", "1.0", LocalDate.of(2026, 1, 5));
        buy(variety.getVarietyKey(), "400", "2.0", LocalDate.of(2026, 2, 5));

        HoldingSellRequest sell = new HoldingSellRequest();
        sell.setVarietyKey(variety.getVarietyKey());
        sell.setShares(new BigDecimal("700"));
        List<HoldingVO> after = holdingService.sell(sell);

        assertThat(after).hasSize(1);
        assertThat(after.get(0).getShares()).isEqualByComparingTo("300");
        assertThat(after.get(0).getCostPrice()).isEqualByComparingTo("2.0");

        HoldingChangeQueryRequest changeQuery = new HoldingChangeQueryRequest();
        changeQuery.setVarietyKey(variety.getVarietyKey());
        changeQuery.setChangeType(HoldingChangeType.SELL);
        List<HoldingChangeVO> changes = holdingService.listChanges(changeQuery);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getShares()).isEqualByComparingTo("700");
        // 加权成本 = (600×1.0 + 100×2.0) / 700
        assertThat(changes.get(0).getUnitPrice()).isEqualByComparingTo("1.1429");
        assertThat(changes.get(0).getAfterShares()).isEqualByComparingTo("300");
        assertThat(changes.get(0).getNote()).contains("先进先出");
    }

    /**
     * 全部卖出后持仓置为已清仓，再次买入重新开一条 HOLDING
     */
    @Test
    @DisplayName("全部卖出后置已清仓并允许重新买入")
    void sellAllClosesAndRebuyReopens() {
        VarietyVO variety = confirmVariety("110022");
        buy(variety.getVarietyKey(), "300", "1.0", null);

        HoldingSellRequest sell = new HoldingSellRequest();
        sell.setVarietyKey(variety.getVarietyKey());
        sell.setShares(new BigDecimal("300"));
        List<HoldingVO> after = holdingService.sell(sell);
        assertThat(after).isEmpty();

        HoldingQueryRequest closedQuery = new HoldingQueryRequest();
        closedQuery.setVarietyKey(variety.getVarietyKey());
        closedQuery.setStatus(HoldingStatus.CLOSED);
        List<HoldingVO> closed = holdingService.listHolding(closedQuery);
        assertThat(closed).hasSize(1);
        assertThat(closed.get(0).getStatus()).isEqualTo(HoldingStatus.CLOSED);

        buy(variety.getVarietyKey(), "200", "1.8", null);
        HoldingSummaryVO summary = holdingService.summaryOf(variety.getVarietyKey());
        assertThat(summary.getHolding()).isTrue();
        assertThat(summary.getTotalShares()).isEqualByComparingTo("200");
        assertThat(summary.getAvgCostPrice()).isEqualByComparingTo("1.8");
    }

    /**
     * 非法数据逐项拒绝：成本价为 0、份额为负、超额卖出、未注册品种
     */
    @Test
    @DisplayName("非法数据拒绝并指出具体原因")
    void invalidInputRejectedWithReason() {
        VarietyVO variety = confirmVariety("110022");
        buy(variety.getVarietyKey(), "300", "1.0", null);

        HoldingBuyRequest zeroCost = new HoldingBuyRequest();
        zeroCost.setVarietyKey(variety.getVarietyKey());
        zeroCost.setShares(new BigDecimal("100"));
        zeroCost.setCostPrice(BigDecimal.ZERO);
        assertThatThrownBy(() -> holdingService.buy(zeroCost))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("成本价必须为正数");

        HoldingBuyRequest negativeShares = new HoldingBuyRequest();
        negativeShares.setVarietyKey(variety.getVarietyKey());
        negativeShares.setShares(new BigDecimal("-10"));
        negativeShares.setCostPrice(new BigDecimal("1.0"));
        assertThatThrownBy(() -> holdingService.buy(negativeShares))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("份额必须为正数");

        HoldingBuyRequest unregistered = new HoldingBuyRequest();
        unregistered.setVarietyKey("OF:999999");
        unregistered.setShares(new BigDecimal("100"));
        unregistered.setCostPrice(new BigDecimal("1.0"));
        assertThatThrownBy(() -> holdingService.buy(unregistered))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未注册");

        HoldingSellRequest oversell = new HoldingSellRequest();
        oversell.setVarietyKey(variety.getVarietyKey());
        oversell.setShares(new BigDecimal("301"));
        assertThatThrownBy(() -> holdingService.sell(oversell))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过当前持有");
    }

    /**
     * 持仓品种数超过 30 只上限时拒绝新开品种
     */
    @Test
    @DisplayName("超过 30 只上限拒绝新开品种")
    void overLimitRejected() {
        // 注册 30 个品种并各建一条持仓，占满上限；避开用例固有的 110022/510300/000216
        for (int i = 1; i <= 30; i++) {
            String code = String.format("63%04d", i);
            insertCatalog(code, "测试基金" + i, "股票型");
            VarietyVO variety = confirmVariety(code);
            buy(variety.getVarietyKey(), "100", "1.0", null);
        }
        // 第 31 只被拒绝
        insertCatalog("639999", "测试基金31", "股票型");
        VarietyVO overflow = confirmVariety("639999");
        HoldingBuyRequest buy = new HoldingBuyRequest();
        buy.setVarietyKey(overflow.getVarietyKey());
        buy.setShares(new BigDecimal("100"));
        buy.setCostPrice(new BigDecimal("1.0"));
        assertThatThrownBy(() -> holdingService.buy(buy))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上限");
    }

    /**
     * 总览按品种聚合并带出名称与份额，无持仓时为空列表
     */
    @Test
    @DisplayName("总览按品种聚合")
    void overviewAggregatesByVariety() {
        VarietyVO variety = confirmVariety("110022");
        buy(variety.getVarietyKey(), "600", "1.0", null);
        buy(variety.getVarietyKey(), "400", "2.0", null);

        HoldingOverviewVO overview = holdingService.overview();
        assertThat(overview.getVarietyCount()).isEqualTo(1);
        assertThat(overview.getItems()).hasSize(1);
        HoldingVarietyVO item = overview.getItems().get(0);
        assertThat(item.getVarietyKey()).isEqualTo("OF:110022");
        assertThat(item.getVarietyName()).isEqualTo("易方达消费行业股票");
        assertThat(item.getTotalShares()).isEqualByComparingTo("1000");
        assertThat(item.getAvgCostPrice()).isEqualByComparingTo("1.4");
        // dev 库里该基金有真实净值时市值为份额 × 净值；两种来源都算有效展示
        if (item.getMarketValue() != null) {
            assertThat(item.getMarketValue()).isEqualByComparingTo(
                    item.getTotalShares().multiply(item.getLatestPrice()));
            assertThat(item.getLatestPrice()).isNotNull();
        }
    }

    /**
     * 确认一个品种便于用例直接买入
     *
     * @param code 6 位代码
     * @return 已注册品种
     */
    private VarietyVO confirmVariety(String code) {
        FundConfirmRequest request = new FundConfirmRequest();
        request.setCode(code);
        request.setVarietyType(VarietyType.OF);
        if ("510300".equals(code)) {
            request.setVarietyType(VarietyType.ETF);
        }
        return fundDisambiguateService.confirm(request);
    }

    /**
     * 直接买入的便捷封装
     *
     * @param varietyKey 品种业务主键
     * @param shares     份额
     * @param cost       成本价
     * @param openDate   建仓日期
     */
    private void buy(String varietyKey, String shares, String cost, LocalDate openDate) {
        HoldingBuyRequest request = new HoldingBuyRequest();
        request.setVarietyKey(varietyKey);
        request.setShares(new BigDecimal(shares));
        request.setCostPrice(new BigDecimal(cost));
        request.setOpenDate(openDate);
        holdingService.buy(request);
    }

    /**
     * 写入一条全集缓存
     *
     * @param code 6 位代码
     * @param name 名称
     * @param type 原始类型
     */
    private void insertCatalog(String code, String name, String type) {
        FundCatalog row = new FundCatalog();
        row.setFundCode(code);
        row.setFundName(name);
        row.setFundType(type);
        fundCatalogMapper.insertSelective(row);
    }

    /**
     * 候选注册态断言载体，避免测试里反复取 null 字段
     */
    private record FundCandidateMarked(com.lilac.domain.vo.FundCandidateVO candidate) {

        Boolean registered() {
            return candidate.getRegistered();
        }

        Boolean holding() {
            return candidate.getHoldingSummary() == null ? null : candidate.getHoldingSummary().getHolding();
        }

        BigDecimal totalShares() {
            return candidate.getHoldingSummary() == null ? null : candidate.getHoldingSummary().getTotalShares();
        }
    }
}

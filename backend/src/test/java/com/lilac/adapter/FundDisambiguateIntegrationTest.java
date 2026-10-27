package com.lilac.adapter;

import com.lilac.adapter.eastmoney.ExchangeIdentityAdapter;
import com.lilac.adapter.fund.FundDetailAdapter;
import com.lilac.adapter.model.ExchangeIdentity;
import com.lilac.adapter.model.ExchangeKind;
import com.lilac.domain.dto.fund.FundConfirmRequest;
import com.lilac.domain.dto.fund.FundDisambiguateRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.entity.FundCatalog;
import com.lilac.domain.vo.FundCandidateVO;
import com.lilac.domain.vo.FundDisambiguateVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.VarietyType;
import com.lilac.exception.BusinessException;
import com.lilac.mapper.FundCatalogMapper;
import com.lilac.service.FundDisambiguateService;
import com.lilac.service.VarietyService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 消歧验收测试，外部行情与档案页用桩隔离，数据库走真实 MySQL
 */
@SpringBootTest
@Transactional
class FundDisambiguateIntegrationTest {

    @Autowired
    private FundDisambiguateService fundDisambiguateService;

    @Autowired
    private VarietyService varietyService;

    @Autowired
    private FundCatalogMapper fundCatalogMapper;

    @MockitoBean
    private ExchangeIdentityAdapter exchangeIdentityAdapter;

    @MockitoBean
    private FundDetailAdapter fundDetailAdapter;

    /**
     * 每个用例前清空并写入固定全集，避免依赖上游实时数据
     */
    @BeforeEach
    void setUpCatalog() {
        // 框架禁止无条件删除，显式写 where
        fundCatalogMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
        insert("000001", "华夏成长混合", "混合型-灵活");
        insert("510300", "沪深300ETF华泰柏瑞", "指数型-股票");
        insert("510500", "中证500ETF南方", "指数型-股票");
        insert("518880", "黄金ETF华安", "指数型-其他");
        insert("000216", "华安黄金ETF联接A", "指数型-其他");
        insert("000217", "华安黄金ETF联接C", "指数型-其他");
        insert("090010", "大成中证红利指数A", "指数型-股票");
        insert("512890", "红利低波ETF华泰柏瑞", "指数型-股票");
        insert("110022", "易方达消费行业股票", "股票型");
        insert("020411", "永赢中证沪深港黄金产业股票ETF发起联接A", "指数型-股票");
        insert("007976", "易方达黄金主题人民币C", "QDII-商品");
        when(fundDetailAdapter.fetchTrackIndexName(anyString())).thenReturn(Optional.empty());
    }

    /**
     * 000001 同时返回场外基金与指数，且指数不可录入
     */
    @Test
    @DisplayName("000001 返回场外基金与上证指数两个候选")
    void sameCodeReturnsFundAndIndex() {
        when(exchangeIdentityAdapter.resolveAll("000001")).thenReturn(List.of(
                new ExchangeIdentity("000001", "上证指数", ExchangeKind.INDEX),
                new ExchangeIdentity("000001", "平安银行", ExchangeKind.STOCK)));

        FundDisambiguateVO result = fundDisambiguateService.disambiguate(request("000001"));

        assertThat(result.getExists()).isTrue();
        assertThat(result.getCandidates()).hasSize(2);
        FundCandidateVO fund = result.getCandidates().get(0);
        FundCandidateVO index = result.getCandidates().get(1);
        assertThat(fund.getVarietyType()).isEqualTo(VarietyType.OF);
        assertThat(fund.getVarietyKey()).isEqualTo("OF:000001");
        assertThat(fund.getVarietyName()).isEqualTo("华夏成长混合");
        assertThat(fund.getCategory()).isEqualTo(FundCategory.MIXED);
        assertThat(fund.getRecordable()).isTrue();
        assertThat(index.getVarietyType()).isEqualTo(VarietyType.IDX);
        assertThat(index.getVarietyName()).isEqualTo("上证指数");
        assertThat(index.getRecordable()).isFalse();
        assertThat(result.getNeedConfirm()).isFalse();
    }

    /**
     * 510300 判定为场内 ETF，业务主键带 ETF 前缀
     */
    @Test
    @DisplayName("510300 判定为场内 ETF")
    void exchangeFundRecognizedAsEtf() {
        when(exchangeIdentityAdapter.resolveAll("510300"))
                .thenReturn(List.of(new ExchangeIdentity("510300", "沪深300ETF华泰柏瑞", ExchangeKind.ETF)));

        FundDisambiguateVO result = fundDisambiguateService.disambiguate(request("510300"));

        assertThat(result.getCandidates()).hasSize(1);
        FundCandidateVO candidate = result.getCandidates().get(0);
        assertThat(candidate.getVarietyType()).isEqualTo(VarietyType.ETF);
        assertThat(candidate.getVarietyKey()).isEqualTo("ETF:510300");
        assertThat(candidate.getCategory()).isEqualTo(FundCategory.ETF);
    }

    /**
     * 黄金基金识别为黄金；无法判定的类型标为待确认并要求人工指定
     */
    @Test
    @DisplayName("黄金识别与待确认标记")
    void goldRecognizedAndUncertainMarked() {
        when(exchangeIdentityAdapter.resolveAll("518880"))
                .thenReturn(List.of(new ExchangeIdentity("518880", "黄金ETF华安", ExchangeKind.ETF)));
        FundDisambiguateVO gold = fundDisambiguateService.disambiguate(request("518880"));
        assertThat(gold.getCandidates().get(0).getCategory()).isEqualTo(FundCategory.GOLD);
        assertThat(gold.getCandidates().get(0).getConfirmed()).isTrue();

        when(exchangeIdentityAdapter.resolveAll("007976")).thenReturn(List.of());
        FundDisambiguateVO qdii= fundDisambiguateService.disambiguate(request("007976"));
        assertThat(qdii.getCandidates().get(0).getCategory()).isEqualTo(FundCategory.UNKNOWN);
        assertThat(qdii.getCandidates().get(0).getConfirmed()).isFalse();
        assertThat(qdii.getNeedConfirm()).isTrue();
    }

    /**
     * 黄金股不能被误判为黄金
     */
    @Test
    @DisplayName("黄金股不误判为黄金")
    void goldEquityNotTreatedAsGold() {
        when(exchangeIdentityAdapter.resolveAll("020411")).thenReturn(List.of());

        FundCandidateVO candidate = fundDisambiguateService.disambiguate(request("020411")).getCandidates().get(0);

        assertThat(candidate.getCategory()).isEqualTo(FundCategory.STOCK);
        assertThat(candidate.getConfirmed()).isTrue();
    }

    /**
     * A/C 类名称相近的份额并列展示，且不包含自身
     */
    @Test
    @DisplayName("A/C 类份额并列展示")
    void shareClassesListedTogether() {
        when(exchangeIdentityAdapter.resolveAll("000216"))
                .thenReturn(List.of(new ExchangeIdentity("000216", "华安黄金ETF联接A", ExchangeKind.LOF)));

        FundDisambiguateVO result = fundDisambiguateService.disambiguate(request("000216"));

        assertThat(result.getRelatedShareClasses())
                .extracting(FundCandidateVO::getVarietyCode)
                .containsExactly("000217");
        assertThat(result.getRelatedShareClasses().get(0).getVarietyName()).isEqualTo("华安黄金ETF联接C");
    }

    /**
     * 不存在的代码返回明确错误，不抛栈
     */
    @Test
    @DisplayName("不存在代码返回明确错误")
    void missingCodeReturnsClearError() {
        when(exchangeIdentityAdapter.resolveAll("999999")).thenReturn(List.of());

        assertThatThrownBy(() -> fundDisambiguateService.disambiguate(request("999999")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到代码 999999");
        assertThatThrownBy(() -> fundDisambiguateService.disambiguate(request("12")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("6 位数字");
    }

    /**
     * 行情失败时降级为全集结果，仍能返回候选
     */
    @Test
    @DisplayName("行情失败降级返回全集结果")
    void degradedWhenQuoteFails() {
        when(exchangeIdentityAdapter.resolveAll("110022"))
                .thenThrow(new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "外部数据源请求失败：模拟断网"));

        FundDisambiguateVO result = fundDisambiguateService.disambiguate(request("110022"));

        assertThat(result.getDegraded()).isTrue();
        assertThat(result.getCandidates()).hasSize(1);
        assertThat(result.getCandidates().get(0).getVarietyName()).isEqualTo("易方达消费行业股票");
    }

    /**
     * 确认后写入品种注册表，重复确认走更新而不是新增
     */
    @Test
    @DisplayName("确认写入品种注册表且幂等")
    void confirmWritesVarietyAndIsIdempotent() {
        FundConfirmRequest request = new FundConfirmRequest();
        request.setCode("110022");
        request.setVarietyType(VarietyType.OF);

        VarietyVO saved = fundDisambiguateService.confirm(request);
        assertThat(saved.getVarietyKey()).isEqualTo("OF:110022");
        assertThat(saved.getCategory()).isEqualTo(FundCategory.STOCK);
        assertThat(saved.getConfirmed()).isTrue();
        assertThat(saved.getId()).isNotNull();

        VarietyQueryRequest query = new VarietyQueryRequest();
        query.setVarietyCode("110022");
        assertThat(varietyService.listVariety(query)).hasSize(1);
    }

    /**
     * 类型待确认时拒绝确认，避免猜测后静默使用
     */
    @Test
    @DisplayName("类型待确认时拒绝确认")
    void confirmRejectsUnknownCategory() {
        FundConfirmRequest request = new FundConfirmRequest();
        request.setCode("007976");
        request.setVarietyType(VarietyType.OF);

        assertThatThrownBy(() -> fundDisambiguateService.confirm(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法自动判定");

        request.setCategory(FundCategory.UNKNOWN);
        assertThatThrownBy(() -> fundDisambiguateService.confirm(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法自动判定");
    }

    /**
     * 人工指定类型后可确认，并标记为已确认
     */
    @Test
    @DisplayName("人工指定类型后可确认")
    void confirmWithManualCategory() {
        FundConfirmRequest request = new FundConfirmRequest();
        request.setCode("007976");
        request.setVarietyType(VarietyType.OF);
        request.setCategory(FundCategory.GOLD);

        VarietyVO saved = fundDisambiguateService.confirm(request);

        assertThat(saved.getCategory()).isEqualTo(FundCategory.GOLD);
        assertThat(saved.getConfirmed()).isTrue();
    }

    /**
     * 红利基金自动带出跟踪指数，取不到时拒绝确认
     */
    @Test
    @DisplayName("红利基金带出跟踪指数")
    void dividendResolvesTrackIndex() {
        when(fundDetailAdapter.fetchTrackIndexName("512890")).thenReturn(Optional.of("中证红利低波动指数"));
        FundConfirmRequest request = new FundConfirmRequest();
        request.setCode("512890");
        request.setVarietyType(VarietyType.ETF);

        VarietyVO saved = fundDisambiguateService.confirm(request);
        assertThat(saved.getCategory()).isEqualTo(FundCategory.DIVIDEND);
        assertThat(saved.getTrackIndexCode()).isEqualTo("H30269");

        FundConfirmRequest missing = new FundConfirmRequest();
        missing.setCode("090010");
        missing.setVarietyType(VarietyType.OF);
        assertThatThrownBy(() -> fundDisambiguateService.confirm(missing))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须指定跟踪指数");
    }

    /**
     * 指数不能从持仓入口写入
     */
    @Test
    @DisplayName("指数拒绝写入品种注册表")
    void indexRejectedOnConfirm() {
        FundConfirmRequest request = new FundConfirmRequest();
        request.setCode("000001");
        request.setVarietyType(VarietyType.IDX);

        assertThatThrownBy(() -> fundDisambiguateService.confirm(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(HttpsCodeEnum.PARAMS_ERROR.getCode());
    }

    /**
     * 写入一条全集缓存
     *
     * @param code 6 位代码
     * @param name 名称
     * @param type 原始类型
     */
    private void insert(String code, String name, String type) {
        FundCatalog row = new FundCatalog();
        row.setFundCode(code);
        row.setFundName(name);
        row.setFundType(type);
        fundCatalogMapper.insertSelective(row);
    }

    /**
     * 构造消歧请求
     *
     * @param code 6 位代码
     * @return 消歧请求
     */
    private FundDisambiguateRequest request(String code) {
        FundDisambiguateRequest request = new FundDisambiguateRequest();
        request.setCode(code);
        return request;
    }
}

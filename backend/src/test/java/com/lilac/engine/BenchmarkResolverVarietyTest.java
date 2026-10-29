package com.lilac.engine;

import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实品种的代理标的解析测试，锁住 007467 这类红利联接基金能拿到可交易 ETF
 */
class BenchmarkResolverVarietyTest {

    /**
     * 007467 登记跟踪 H30269，必须解析成红利低波 ETF 而不是 null
     */
    @Test
    @DisplayName("红利联接基金解析出跟踪 ETF")
    void dividendLinkFundResolvesToEtf() {
        BenchmarkResolver resolver = new BenchmarkResolver(new EstimateThresholds());
        VarietyVO variety = new VarietyVO();
        variety.setVarietyKey("OF:007467");
        variety.setVarietyType(VarietyType.OF);
        variety.setVarietyCode("007467");
        variety.setVarietyName("华泰柏瑞中证红利低波ETF联接C");
        variety.setCategory(FundCategory.DIVIDEND);
        variety.setTrackIndexCode("H30269");

        String symbol = resolver.resolve(variety, "指数型-股票");

        // 解析不出代理标的是这只基金被判「覆盖率不足」的第一层原因
        assertThat(symbol).isNotNull();
        assertThat(symbol).isEqualTo("sh512890");
    }

    /**
     * 未登记跟踪指数时按名称兜底，红利低波仍能对上 H30269 的代理 ETF
     */
    @Test
    @DisplayName("未登记指数时按名称兜底")
    void fallsBackToName() {
        BenchmarkResolver resolver = new BenchmarkResolver(new EstimateThresholds());
        VarietyVO variety = new VarietyVO();
        variety.setVarietyKey("OF:007468");
        variety.setVarietyType(VarietyType.OF);
        variety.setVarietyCode("007468");
        variety.setVarietyName("华泰柏瑞中证红利低波ETF联接D");
        variety.setCategory(FundCategory.DIVIDEND);

        String symbol = resolver.resolve(variety, "指数型-股票");

        assertThat(symbol).isEqualTo("sh512890");
    }
}

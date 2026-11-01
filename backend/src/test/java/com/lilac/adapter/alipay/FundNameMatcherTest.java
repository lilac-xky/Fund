package com.lilac.adapter.alipay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 名称相似度匹配测试，用例取自使用者真实持仓与真实账单的叫法差异
 */
class FundNameMatcherTest {

    private final FundNameMatcher matcher = new FundNameMatcher();

    /**
     * 蚂蚁用全称、天天用简称，这四组真实差异必须都能对上
     */
    @Test
    void realWorldDiffersStillMatch() {
        assertMatch("中信保诚至泰中短债债券C", "中信保诚至泰中短债C");
        assertMatch("南方中证银行ETF联接C", "南方中证银行ETF发起联接C");
        assertMatch("华泰柏瑞中证红利低波动ETF联接C", "华泰柏瑞中证红利低波ETF联接C");
        assertMatch("摩根标普500指数(QDII)A", "摩根标普500指数(QDII)人民币A");
    }

    /**
     * A 类与 C 类是不同份额，再像也不能混
     */
    @Test
    void shareClassMustMatch() {
        String left = matcher.normalize("华商均衡成长混合C");
        String right = matcher.normalize("华商均衡成长混合A");
        assertThat(matcher.classCompatible(left, right)).isFalse();

        String leftC = matcher.normalize("华商均衡成长混合C");
        String rightC = matcher.normalize("华商均衡成长混合C");
        assertThat(matcher.classCompatible(leftC, rightC)).isTrue();
    }

    /**
     * 归一化要去掉括号、空格与全角，保证「(QDII)」与「（QDII）」等价
     */
    @Test
    void normalizeIgnoresPunctuation() {
        assertThat(matcher.normalize("摩根标普500指数（QDII）A"))
                .isEqualTo(matcher.normalize("摩根标普500指数(QDII)A"));
        assertThat(matcher.normalize("华泰柏瑞 中证红利-低波动 ETF联接C"))
                .isEqualTo(matcher.normalize("华泰柏瑞中证红利低波动ETF联接C"));
    }

    /**
     * 完全不同的基金不能被判为相似
     */
    @Test
    void unrelatedFundsScoreLow() {
        double score = matcher.similarity(matcher.normalize("国泰黄金ETF联接C"),
                matcher.normalize("华商均衡成长混合C"));
        assertThat(score).isLessThan(0.72);
    }

    /**
     * 断言两个叫法能按相似度对上
     *
     * @param alipayName   支付宝账单里的叫法
     * @param catalogName  天天基金（持仓）里的叫法
     */
    private void assertMatch(String alipayName, String catalogName) {
        String left = matcher.normalize(alipayName);
        String right = matcher.normalize(catalogName);
        double score = matcher.similarity(left, right);
        assertThat(matcher.classCompatible(left, right))
                .as("%s 与 %s 的份额类别应兼容", alipayName, catalogName)
                .isTrue();
        assertThat(score)
                .as("%s 与 %s 的相似度 %s 应不低于阈值", alipayName, catalogName, score)
                .isGreaterThanOrEqualTo(0.72);
    }
}

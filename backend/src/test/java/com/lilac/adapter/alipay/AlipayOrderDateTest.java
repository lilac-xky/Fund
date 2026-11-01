package com.lilac.adapter.alipay;

import com.lilac.enums.AlipayAction;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * 下单日取自交易号前缀的回归，锁定「创建时间是确认时间」这个坑
 */
class AlipayOrderDateTest {

    /**
     * 交易号 20260918… 的卖出，创建时间写 2026-09-21 15:28，确认日必须是 9-18
     */
    @Test
    void sellUsesTradeNoDate() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("..", "data", "alipay",
                "alipay_record_20261101_1642_1.csv"));
        List<AlipayBillRow> rows = new AlipayBillParser().parse(bytes);
        List<AlipayFundOrder> orders = new AlipayFundClassifier().classify(rows);

        AlipayFundOrder sell = orders.stream()
                .filter(order -> "20260918001080012404880012305864".equals(order.getTradeNo()))
                .findFirst()
                .orElseThrow();

        System.out.println("商品: " + sell.getProductName());
        System.out.println("下单日: " + LocalDate.of(2026, 9, 18) + "（交易号前缀）");
        System.out.println("成交净值日: " + sell.getConfirmDate());
        System.out.println("金额: " + sell.getAmount() + " 元");

        // 实测：9-18 净值 3.7585，18.08 ÷ 3.7585 = 4.81 份，与支付宝 App 显示一致
        // 说明成交净值取的是下单当日，过 15 点也不顺延
        org.assertj.core.api.Assertions.assertThat(sell.getConfirmDate())
                .isEqualTo(LocalDate.of(2026, 9, 18));
        java.math.BigDecimal shares = sell.getAmount()
                .divide(new java.math.BigDecimal("3.7585"), 2, java.math.RoundingMode.HALF_UP);
        System.out.println("按 9-18 净值折算份额: " + shares + "（App 显示 4.81）");
        org.assertj.core.api.Assertions.assertThat(shares)
                .isEqualByComparingTo("4.81");
    }

    /**
     * 只有卖出的创建时间与交易号日不同；买入与转换全部同日
     *
     * <p>使用者直接指出过这一点：「转换的订单号和创建也是同一时间，只有卖出不同」。
     * 这条用例把该事实钉住，避免以后又把转换算进「不同日」那类。</p>
     */
    @Test
    void onlySellDiffersFromCreatedDate() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("..", "data", "alipay",
                "alipay_record_20261101_1642_1.csv"));
        List<AlipayBillRow> rows = new AlipayBillParser().parse(bytes);
        List<AlipayFundOrder> orders = new AlipayFundClassifier().classify(rows);

        long buyDiff = differing(orders, AlipayAction.BUY);
        long convertDiff = differing(orders, AlipayAction.CONVERT);
        long sellDiff = differing(orders, AlipayAction.SELL);
        long sellTotal = orders.stream().filter(o -> o.getAction() == AlipayAction.SELL).count();

        System.out.println("买入不同日 " + buyDiff + " / 转换不同日 " + convertDiff
                + " / 卖出不同日 " + sellDiff + "（卖出共 " + sellTotal + "）");

        // 买入与转换必须全部同日
        org.assertj.core.api.Assertions.assertThat(buyDiff).isZero();
        org.assertj.core.api.Assertions.assertThat(convertDiff).isZero();
        // 卖出必须全部不同日，且条数与数据一致
        org.assertj.core.api.Assertions.assertThat(sellDiff).isEqualTo(sellTotal).isGreaterThan(0);
    }

    /**
     * 统计某类动作里「交易号日与创建日不同」的条数
     *
     * @param orders 动作列表
     * @param action 动作类型
     * @return 条数
     */
    private long differing(List<AlipayFundOrder> orders, AlipayAction action) {
        return orders.stream()
                .filter(order -> order.getAction() == action)
                .filter(order -> order.getOrderTime() != null)
                // orderTime 取的是创建时间，与 tradeDate（交易号日）比
                .filter(order -> !order.getOrderTime().toLocalDate().equals(order.getTradeDate()))
                .count();
    }
}

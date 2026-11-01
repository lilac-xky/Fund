package com.lilac.adapter.alipay;

import com.lilac.enums.AlipayAction;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 转换与转换退款的配对回归，两者交易号不同，只能按「下单日 + 基金对」配
 */
class AlipayConvertRefundTest {

    /**
     * 真实账单里 16 组转换都带一条退款，退款金额是净值差异的找零，
 * 应并入转换行而不是把整对丢弃
     */
    @Test
    void convertPairedWithRefund() throws Exception {
        Path bill = Path.of("..", "data", "alipay", "alipay_record_20261101_1642_1.csv");
        byte[] bytes = Files.readAllBytes(bill);
        List<AlipayFundOrder> orders = new AlipayFundClassifier()
                .classify(new AlipayBillParser().parse(bytes));

        System.out.println("=== 转换与退款配对检查 ===");
        long converts = orders.stream().filter(o -> o.getAction() == AlipayAction.CONVERT).count();
        long refunds = orders.stream()
                .filter(o -> o.getAction() == AlipayAction.REFUND
                        && o.getProductName() != null && o.getProductName().contains("[转换至]"))
                .count();
        System.out.println("转换行 " + converts + " 条，转换退款行 " + refunds + " 条");

        org.assertj.core.api.Assertions.assertThat(converts).isEqualTo(16);
        org.assertj.core.api.Assertions.assertThat(refunds).isEqualTo(16);

        // 逐组比对配对键是否一致；键用交易号日而非确认日，后者对 15 点后的转换会顺延
        int sameKey = 0;
        for (AlipayFundOrder convert : orders) {
            if (convert.getAction() != AlipayAction.CONVERT) {
                continue;
            }
            String key = convert.getTradeDate() + "|" + convert.getFundName() + "|"
                    + convert.getTargetFundName();
            boolean hit = orders.stream().anyMatch(other -> other.getAction() == AlipayAction.REFUND
                    && other.getProductName() != null && other.getProductName().contains("[转换至]")
                    && key.equals(other.getTradeDate() + "|" + other.getFundName() + "|"
                    + other.getTargetFundName()));
            if (hit) {
                sameKey++;
            } else {
                System.out.println("未配对: " + key);
            }
        }
        System.out.println("配对成功 " + sameKey + " / " + converts);
        org.assertj.core.api.Assertions.assertThat(sameKey).isEqualTo((int) converts);
    }
}

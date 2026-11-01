package com.lilac.adapter.alipay;

import com.lilac.enums.AlipayAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 基金动作识别器，把账单行按商品名称里的关键词归类并剥出基金名称
 */
@Slf4j
@Component
public class AlipayFundClassifier {

    /**
     * 基金申赎确认的分界时刻，这个点之后的订单按下一交易日净值算
     */
    /**
     * 平台前缀，蚂蚁财富同一机构会写成多种，统一剥掉
     */
    private static final List<String> PREFIXES = List.of(
            "蚂蚁财富-", "蚂蚁（杭州）基金销售有限公司-", "蚂蚁基金-");

    /**
     * 动作词尾缀，长串必须排在短串前面，否则「-卖出至余额宝」会被「-卖出」截成「至余额宝」
     */
    private static final List<String> SUFFIXES = List.of(
            "-[转换至]", "[转换至]", "-买入退款", "-转换退款", "-卖出退款", "-确认成功退款",
            "-买入撤单", "-卖出撤单", "-撤单",
            "-现金分红至余额宝", "-现金分红至银行卡", "-现金分红",
            "-卖出至余额宝", "-卖出至银行卡", "-卖出至支付宝", "-卖出",
            "-买入至余额宝", "-买入",
            "-赎回至余额宝", "-赎回至银行卡", "-赎回");

    /**
     * 基金申赎确认的分界时刻，这个点之后的订单按下一交易日净值算
     */
    private static final LocalTime CUTOFF = LocalTime.of(15, 0);

    /**
     * 转换行里分隔转入转出的标记
     */
    private static final Pattern CONVERT_SPLIT = Pattern.compile("\\[转换至\\]");

    /**
     * 退款类标记，命中即视为冲销而非新动作
     */
    private static final List<String> REFUND_WORDS = List.of("退款", "撤单");

    /**
     * 余额宝收益发放不是基金申赎，直接排除
     */
    private static final List<String> IGNORE_WORDS = List.of("余额宝-", "收益发放", "转账收款");

    /**
     * 把账单行逐条识别为基金动作；转换行会产出转出与转入两个动作
     *
     * @param rows 账单行
     * @return 识别结果，无法归类的也保留，action 为 UNKNOWN
     */
    public List<AlipayFundOrder> classify(List<AlipayBillRow> rows) {
        List<AlipayFundOrder> orders = new ArrayList<>();
        if (rows == null) {
            return orders;
        }
        for (AlipayBillRow row : rows) {
            if (row == null || row.getProductName() == null || row.getProductName().isBlank()) {
                continue;
            }
            String product = row.getProductName().strip();
            if (ignored(product)) {
                continue;
            }
            AlipayFundOrder order = new AlipayFundOrder();
            // 退款行的交易号是「原交易号_退款交易号」，归组只认前一段才能抵消原买入
            order.setTradeNo(headOfTradeNo(row.getTradeNo()));
            order.setProductName(product);
            order.setAmount(row.getAmount());
            order.setOrderTime(row.getCreatedTime());
            order.setNote(row.getNote());
            order.setAction(actionOf(product));
            // 交易号日不受 15 点影响，转换与它的退款靠它配对
            order.setTradeDate(row.getOrderDate());
            // 15 点判断只对买入与转换成立，卖出传进去也只会退回下单日
            order.setConfirmDate(confirmDateOf(row, order.getAction()));
            fillFundNames(order, product);
            // 退款行记下它冲销的是同交易号上的原动作，上层据此抵消
            if (order.getAction() == AlipayAction.REFUND) {
                order.setRefundedTradeNo(row.getTradeNo());
            }
            orders.add(order);
            // 转换行的转入侧在账单里没有独立记录，这里补一条，否则转入基金永远没有买入批次
            if (order.getAction() == AlipayAction.CONVERT
                    && order.getTargetFundName() != null && !order.getTargetFundName().isBlank()) {
                orders.add(invertedSide(order));
            }
        }
        return orders;
    }

    /**
     * 由转换行派生转入侧的动作：金额相同，方向相反，标记为转换转入
     *
     * @param order 转出侧动作
     * @return 转入侧动作
     */
    private AlipayFundOrder invertedSide(AlipayFundOrder order) {
        AlipayFundOrder target = new AlipayFundOrder();
        // 用「交易号#IN」区分，避免与转出侧在按交易号归组时被当成重复行删掉
        target.setTradeNo(order.getTradeNo() + AlipayAction.CONVERT_IN_SUFFIX);
        target.setProductName(order.getProductName());
        target.setAmount(order.getAmount());
        target.setOrderTime(order.getOrderTime());
        target.setTradeDate(order.getTradeDate());
        target.setConfirmDate(order.getConfirmDate());
        target.setFundName(order.getTargetFundName());
        target.setAction(AlipayAction.CONVERT_IN);
        target.setNote("由「" + order.getFundName() + "」转换转入");
        return target;
    }

    /**
     * 取交易号的主体：退款行的「原交易号_退款交易号」只留原交易号，其余原样返回
     *
     * @param tradeNo 原始交易号
     * @return 归组用的交易号
     */
    private String headOfTradeNo(String tradeNo) {
        if (tradeNo == null) {
            return "";
        }
        String value = tradeNo.strip();
        int underscore = value.indexOf('_');
        return underscore > 0 ? value.substring(0, underscore) : value;
    }

    /**
     * 是否属于与基金申赎无关的行
     *
     * @param product 商品名称
     * @return 无关返回 true
     */
    private boolean ignored(String product) {
        return IGNORE_WORDS.stream().anyMatch(product::contains);
    }

    /**
     * 判断动作类型，先判退款再判转换，避免「转换退款」被当成转换
     *
     * @param product 商品名称
     * @return 动作
     */
    private AlipayAction actionOf(String product) {
        if (REFUND_WORDS.stream().anyMatch(product::contains)) {
            return AlipayAction.REFUND;
        }
        if (product.contains("[转换至]")) {
            return AlipayAction.CONVERT;
        }
        if (product.contains("现金分红")) {
            return AlipayAction.CASH_DIVIDEND;
        }
        if (product.contains("卖出") || product.contains("赎回")) {
            return AlipayAction.SELL;
        }
        if (product.contains("买入")) {
            return AlipayAction.BUY;
        }
        return AlipayAction.UNKNOWN;
    }

    /**
     * 剥掉平台前缀与动作尾缀，取出基金名称；转换与转换退款行还要分出转入的那只
     *
     * @param order   动作
     * @param product 商品名称
     */
    private void fillFundNames(AlipayFundOrder order, String product) {
        String cleaned = strip(product);
        // 转换退款行的名称里也带 [转换至]，与转换行一样要拆成转出与转入两只
        if (!cleaned.contains("[转换至]")) {
            order.setFundName(cleaned);
            return;
        }
        String[] parts = CONVERT_SPLIT.split(cleaned, 2);
        order.setFundName(parts.length > 0 ? parts[0] : "");
        order.setTargetFundName(parts.length > 1 ? parts[1] : "");
        // 拆完两段末尾可能还挂着「-确认成功退款」之类的词，再剥一遍
        order.setFundName(strip(order.getFundName()));
        order.setTargetFundName(strip(order.getTargetFundName()));
    }

    /**
     * 迭代剥掉前缀与尾缀，直到不再变化，避免多层包裹只剥一层
     *
     * @param text 原始文本
     * @return 干净的基金名称
     */
    private String strip(String text) {
        String value = text == null ? "" : text.strip();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : PREFIXES) {
                if (value.startsWith(prefix)) {
                    value = value.substring(prefix.length()).strip();
                    changed = true;
                }
            }
            String trimmed = trimAction(value);
            if (!trimmed.equals(value)) {
                value = trimmed;
                changed = true;
            }
        }
        // 尾缀剥完后可能剩下孤立的分隔符，如「金信民兴债券C-」
        return trimSeparator(value);
    }

    /**
     * 去掉名称两端残留的连接符与空白
     *
     * @param text 文本
     * @return 清理后的文本
     */
    private String trimSeparator(String text) {
        String value = text == null ? "" : text;
        boolean changed = true;
        while (changed && !value.isEmpty()) {
            changed = false;
            for (String mark : List.of("-", "—", "－", "_", " ")) {
                if (value.endsWith(mark)) {
                    value = value.substring(0, value.length() - mark.length());
                    changed = true;
                }
                if (value.startsWith(mark)) {
                    value = value.substring(mark.length());
                    changed = true;
                }
            }
        }
        return value;
    }

    /**
     * 去掉名称末尾的动作词，只去一次，供 strip 循环调用
     *
     * @param text 文本
     * @return 去掉尾缀后的文本
     */
    private String trimAction(String text) {
        String value = text == null ? "" : text.strip();
        for (String suffix : SUFFIXES) {
            if (value.endsWith(suffix)) {
                return value.substring(0, value.length() - suffix.length()).strip();
            }
        }
        return value;
    }

    /**
     * 取成交净值日：买入与转换按创建时刻判 15 点顺延，卖出固定取交易号日（其创建时刻是到账时间，不可判）
     *
     * @param row    账单行
     * @param action 动作类型
     * @return 成交净值日；解析不出时退回创建时间的日期
     */
    private LocalDate confirmDateOf(AlipayBillRow row, AlipayAction action) {
        LocalDate orderDate = row.getOrderDate() != null
                ? row.getOrderDate()
                : (row.getCreatedTime() == null ? null : row.getCreatedTime().toLocalDate());
        if (orderDate == null) {
            return null;
        }
        // 卖出与转换退款的创建时刻是到账时刻，不参与 15 点判断
        boolean cutoffApplies = action == AlipayAction.BUY || action == AlipayAction.CONVERT
                || action == AlipayAction.CONVERT_IN;
        if (!cutoffApplies || row.getCreatedTime() == null) {
            return orderDate;
        }
        if (row.getCreatedTime().toLocalTime().isBefore(CUTOFF)) {
            return orderDate;
        }
        return nextWeekday(orderDate.plusDays(1));
    }

    /**
     * 往后找最近的周一至周五，只处理周末；节假日不影响 15 点这条规则的主体逻辑
     *
     * @param date 起始日
     * @return 非周末的日期
     */
    private LocalDate nextWeekday(LocalDate date) {
        LocalDate current = date;
        while (current.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                || current.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            current = current.plusDays(1);
        }
        return current;
    }

    /**
     * 判断金额是否可作为一条独立订单，零或负值直接丢弃
     *
     * @param amount 金额
     * @return 可入账返回 true
     */
    public boolean usableAmount(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }
}

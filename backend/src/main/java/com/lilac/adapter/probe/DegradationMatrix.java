package com.lilac.adapter.probe;

import com.lilac.domain.vo.DegradationRowVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 降级矩阵，固化「每个数据源失效时哪些功能降级、页面与邮件如何提示」
 */
public final class DegradationMatrix {

    /**
     * 列出全部数据源的降级影响，顺序为行情、基金、指数三组
     *
     * @return 降级矩阵
     */
    public static List<DegradationRowVO> rows() {
        List<DegradationRowVO> rows = new ArrayList<>();
        rows.add(row("tencent", "A 股、指数与港股实时行情 + 前复权日线", Boolean.TRUE,
                List.of("盘中预估", "网格到价提醒", "技术指标"),
                "自动切到新浪源；两源都失败时预估显示「今日无法预估」，网格与指标标注取数失败，不显示任何推算数字"));
        rows.add(row("sina", "腾讯被限流时的备用行情源 + 港股汇率", Boolean.TRUE,
                List.of("备用行情", "港股人民币折算"),
                "腾讯正常时无影响；汇率取不到时港股持仓按港币原值展示并标注未折算"));
        rows.add(row("eastmoney", "基金全集、历史净值、季末持仓与常驻报告", Boolean.FALSE,
                List.of("基金消歧", "净值曲线", "持仓加权预估", "晚间校准"),
                "净值取不到时预估走指数直取或显示无法预估；晚间校准报缺口并写明「净值未公布」"));
        rows.add(row("csindex", "中证指数股息率静态文件", Boolean.FALSE,
                List.of("红利加减仓建议"),
                "股息率过期或取不到时红利建议返回「不可判定」并写明原因，同时保留手工录入入口"));
        rows.add(row("smtp", "收盘前提醒邮件", Boolean.FALSE,
                List.of("邮件送达"),
                "发送失败时正文留存到 data/mail/，页面标记「未送达」，下次发送时附带补发段"));
        rows.add(row("redis", "行情缓存、预估缓存与指标缓存", Boolean.TRUE,
                List.of("缓存加速（不影响正确性）"),
                "缓存不可用时每次重算，日志记录告警，功能不降级"));
        return rows;
    }

    /**
     * 造一行降级说明
     *
     * @param source      数据源名
     * @param purpose     用途
     * @param hasFallback 是否有备用源
     * @param affected    受影响功能
     * @param notice      提示方式
     * @return 降级说明
     */
    private static DegradationRowVO row(String source, String purpose, Boolean hasFallback,
                                        List<String> affected, String notice) {
        DegradationRowVO row = new DegradationRowVO();
        row.setSource(source);
        row.setPurpose(purpose);
        row.setHasFallback(hasFallback);
        row.setAffected(affected);
        row.setNotice(notice);
        return row;
    }

    private DegradationMatrix() {
    }
}

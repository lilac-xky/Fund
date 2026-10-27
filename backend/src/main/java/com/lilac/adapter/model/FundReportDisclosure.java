package com.lilac.adapter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 定期报告的披露记录，用于判断某期持仓快照何时可用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundReportDisclosure {

    /**
     * 报告期，如 2026Q2
     */
    private String period;

    /**
     * 公告披露日
     */
    private LocalDate publishDate;

    /**
     * 是否为全量披露的报告，中报与年报为 true，季报为 false
     */
    private Boolean fullDisclosure;

    /**
     * 公告标题，便于核对
     */
    private String title;
}

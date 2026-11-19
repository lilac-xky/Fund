package com.lilac.service;

import com.lilac.engine.model.NetWorthReadiness;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 晚间净值就绪判定，回答「现在该不该发晚间邮件」，避免在净值还没公布时就发出一堆缺口
 */
public interface NetWorthReadinessService {

    /**
     * 判定指定交易日的净值是否已到齐可以发信
     *
     * @param tradeDate 交易日
     * @param now       当前时刻，用于判断是否已到截止时刻
     * @return 就绪判定
     */
    NetWorthReadiness check(LocalDate tradeDate, LocalDateTime now);
}

package com.lilac.service;

import com.lilac.domain.vo.FundSnapshotVO;

import java.time.LocalDate;

/**
 * 基金净值与持仓业务接口，供预估引擎与晚间校准取数
 */
public interface FundSnapshotService {

    /**
     * 取一只基金的净值、持仓与三个日期，上游失败时回退最近一次成功缓存
     *
     * @param code  6 位基金代码
     * @param today 取数当日
     * @return 净值与持仓视图
     */
    FundSnapshotVO load(String code, LocalDate today);

    /**
     * 只拉净值增量落库，不取持仓与披露明细，供持仓总览过期自愈使用
     *
     * @param code 6 位基金代码
     * @return 新增净值行数，上游失败返回 -1
     */
    int refreshNetWorth(String code);
}

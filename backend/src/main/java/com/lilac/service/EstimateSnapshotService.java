package com.lilac.service;

import com.lilac.domain.vo.EstimateSnapshotVO;
import com.lilac.domain.vo.EstimateVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 当日预估留存业务接口，把白天真正输出的结果留下来供晚间校准
 */
public interface EstimateSnapshotService {

    /**
     * 留存一批预估结果，同一只基金同一天存在则覆盖
     *
     * @param items     预估结果
     * @param tradeDate 预估所属交易日
     * @return 实际写入条数
     */
    int saveBatch(List<EstimateVO> items, LocalDate tradeDate);

    /**
     * 取指定交易日的全部留存，按品种业务主键索引
     *
     * @param tradeDate 交易日
     * @return 留存视图列表
     */
    List<EstimateSnapshotVO> listByDate(LocalDate tradeDate);

    /**
     * 取日期区间内的全部留存，供区间导出补齐区间上下界与方向
     *
     * @param startDate 起始日，含
     * @param endDate   截止日，含
     * @return 留存视图列表，按交易日升序
     */
    List<EstimateSnapshotVO> listByRange(LocalDate startDate, LocalDate endDate);
}

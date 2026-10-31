package com.lilac.service.impl;

import com.lilac.adapter.probe.DegradationMatrix;
import com.lilac.adapter.probe.SourceEventRecorder;
import com.lilac.domain.dto.source.SourceEventQueryRequest;
import com.lilac.domain.vo.SourceHealthVO;
import com.lilac.domain.vo.SourceStatVO;
import com.lilac.service.SourceHealthService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 数据源探活业务实现，汇总留痕并判定是否存在明显异常
 */
@Slf4j
@Service
public class SourceHealthServiceImpl implements SourceHealthService {

    /**
     * 失败率超过该值即判定该源有问题，百分点
     */
    private static final BigDecimal TROUBLE_RATE = BigDecimal.valueOf(30);

    /**
     * 事件数少于此值时不下结论，样本太小失败率没意义
     */
    private static final int MIN_SAMPLES = 5;

    @Resource
    private SourceEventRecorder sourceEventRecorder;

    /**
     * 汇总健康度与降级矩阵
     *
     * @param request 查询请求
     * @return 健康总览
     */
    @Override
    public SourceHealthVO health(SourceEventQueryRequest request) {
        SourceEventQueryRequest query = request == null ? new SourceEventQueryRequest() : request;
        List<SourceStatVO> sources = sourceEventRecorder.stats(query);
        SourceHealthVO vo = new SourceHealthVO();
        vo.setSources(sources);
        vo.setMatrix(DegradationMatrix.rows());
        vo.setStartDate(query.getStartDate() == null ? LocalDate.now().minusDays(7) : query.getStartDate());
        vo.setEndDate(query.getEndDate() == null ? LocalDate.now() : query.getEndDate());
        List<String> troubled = sources.stream()
                .filter(stat -> stat.getTotalCount() != null && stat.getTotalCount() >= MIN_SAMPLES)
                .filter(stat -> stat.getFailureRate() != null && stat.getFailureRate().compareTo(TROUBLE_RATE) > 0)
                .map(SourceStatVO::getSource)
                .toList();
        vo.setHasTrouble(!troubled.isEmpty());
        vo.setNote(noteOf(sources, troubled));
        return vo;
    }

    /**
     * 清理过期留痕
     *
     * @return 删除行数
     */
    @Override
    public int purge() {
        return sourceEventRecorder.purgeExpired();
    }

    /**
     * 拼总结说明
     *
     * @param sources  各源统计
     * @param troubled 失败率超阈值的源
     * @return 说明
     */
    private String noteOf(List<SourceStatVO> sources, List<String> troubled) {
        if (sources.isEmpty()) {
            return "统计区间内没有取数记录，留痕可能已关闭或应用刚启动";
        }
        if (!troubled.isEmpty()) {
            return "以下数据源失败率偏高，建议检查上游是否改版：" + String.join("、", troubled);
        }
        return "统计区间内各数据源失败率均在阈值内";
    }
}

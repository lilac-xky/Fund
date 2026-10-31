package com.lilac.service;

import com.lilac.domain.dto.variety.CategoryBatchRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.dto.variety.VarietySaveRequest;
import com.lilac.domain.vo.PendingCategoryVO;
import com.lilac.domain.vo.VarietyVO;

import java.util.List;

/**
 * 品种注册表业务接口
 */
public interface VarietyService {

    /**
     * 统一查询品种
     *
     * @param request 查询请求
     * @return 品种视图列表
     */
    List<VarietyVO> listVariety(VarietyQueryRequest request);

    /**
     * 按业务主键存在则更新、不存在则新增
     *
     * @param request 保存请求
     * @return 是否成功
     */
    boolean saveVariety(VarietySaveRequest request);

    /**
     * 列出待确认类型的标的，带推断依据与建议类型
     *
     * @param holdingOnly 是否只看持有中的
     * @return 待确认清单
     */
    PendingCategoryVO listPending(boolean holdingOnly);

    /**
     * 批量指定类型与跟踪指数，指定后立即生效
     *
     * @param request 批量请求
     * @return 实际处理的只数
     */
    int assignCategory(CategoryBatchRequest request);

    /**
     * 重置某品种的全部本地数据：清空持仓批次、变动历史、预估留存、误差历史、黄金 β，
     * 品种注册行保留（类型与名称不丢），用于录入错得太多时整只推倒重录
     *
     * @param varietyKey 品种业务主键
     * @return 各表清理行数的说明
     */
    String resetVariety(String varietyKey);
}

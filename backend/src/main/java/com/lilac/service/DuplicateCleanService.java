package com.lilac.service;

import com.lilac.domain.vo.DuplicateCleanVO;

import java.util.List;

/**
 * 重复数据清理业务接口，找出并删除完全重复的持仓批次与变动记录
 */
public interface DuplicateCleanService {

    /**
     * 预览将清理的重复批次，不写库
     *
     * @return 重复组合列表
     */
    List<DuplicateCleanVO> preview();

    /**
     * 执行清理：同一品种下份额、成本价与建仓日完全相同的批次只留最早一条，
     * 对应变动记录同步去重
     *
     * @return 清理结果的文字说明
     */
    String clean();
}

package com.lilac.service;

import com.lilac.domain.dto.send.SendRecordQueryRequest;
import com.lilac.domain.dto.send.SendRecordSaveRequest;
import com.lilac.domain.vo.SendRecordVO;

import java.util.List;

/**
 * 发送留痕业务接口
 */
public interface SendRecordService {

    /**
     * 统一查询发送留痕
     *
     * @param request 查询请求
     * @return 发送记录视图列表
     */
    List<SendRecordVO> listSendRecord(SendRecordQueryRequest request);

    /**
     * 按「日期 + 时点」存在则更新、不存在则新增
     *
     * @param request 保存请求
     * @return 是否成功
     */
    boolean saveSendRecord(SendRecordSaveRequest request);
}

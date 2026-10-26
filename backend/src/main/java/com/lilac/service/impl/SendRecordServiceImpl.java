package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.domain.dto.send.SendRecordQueryRequest;
import com.lilac.domain.dto.send.SendRecordSaveRequest;
import com.lilac.domain.entity.SendRecord;
import com.lilac.domain.vo.SendRecordVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.mapper.SendRecordMapper;
import com.lilac.service.SendRecordService;
import com.lilac.utils.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 发送留痕业务实现
 */
@Slf4j
@Service
public class SendRecordServiceImpl implements SendRecordService {

    @Resource
    private SendRecordMapper sendRecordMapper;

    /**
     * 统一查询发送留痕
     *
     * @param request 查询请求
     * @return 发送记录视图列表
     */
    @Override
    public List<SendRecordVO> listSendRecord(SendRecordQueryRequest request) {
        SendRecordQueryRequest query = request == null ? new SendRecordQueryRequest() : request;
        boolean hasRange = query.getStartDate() != null && query.getEndDate() != null;
        List<SendRecord> rows = sendRecordMapper.selectListByQuery(QueryWrapper.create()
                .eq(SendRecord::getSendDate, query.getSendDate(), query.getSendDate() != null)
                .eq(SendRecord::getSendPoint, query.getSendPoint(), query.getSendPoint() != null)
                .between(SendRecord::getSendDate, query.getStartDate(), query.getEndDate(), hasRange)
                .orderBy(SendRecord::getSendDate, false));
        return BeanUtil.copyToList(rows, SendRecordVO.class);
    }

    /**
     * 按「日期 + 时点」存在则更新、不存在则新增，定时发送与启动补跑共用同一条记录
     *
     * @param request 保存请求
     * @return 是否成功
     */
    @Override
    public boolean saveSendRecord(SendRecordSaveRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getSendDate() == null || request.getSendPoint() == null,
                HttpsCodeEnum.PARAMS_ERROR, "发送日期与时点不能为空");
        SendRecord record = BeanUtil.copyProperties(request, SendRecord.class);
        List<SendRecord> existing = sendRecordMapper.selectListByQuery(QueryWrapper.create()
                .eq(SendRecord::getSendDate, request.getSendDate())
                .eq(SendRecord::getSendPoint, request.getSendPoint()));
        if (existing.isEmpty()) {
            return sendRecordMapper.insertSelective(record) > 0;
        }
        // 沿用已存在行的主键，否则会写成新行并撞上「日期 + 时点」唯一索引
        record.setId(existing.get(0).getId());
        return sendRecordMapper.update(record) > 0;
    }
}

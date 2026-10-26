package com.lilac.domain.dto.send;

import com.lilac.enums.SendPoint;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 发送留痕查询请求，各字段为空时不参与过滤
 */
@Data
public class SendRecordQueryRequest implements Serializable {

    /**
     * 发送日期
     */
    private LocalDate sendDate;

    /**
     * 发送时点
     */
    private SendPoint sendPoint;

    /**
     * 区间起始日，含
     */
    private LocalDate startDate;

    /**
     * 区间截止日，含
     */
    private LocalDate endDate;

    private static final long serialVersionUID = 1L;
}

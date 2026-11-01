package com.lilac.service;

import com.lilac.adapter.alipay.AlipayImportRow;

import java.util.List;

/**
 * 支付宝账单导入业务接口，把账单明细识别成待确认的持仓变动
 */
public interface AlipayImportService {

    /**
     * 解析账单并给出待确认清单，不写任何持仓数据
     *
     * @param bytes    账单文件字节
     * @param fileName 原始文件名，仅用于回显
     * @return 待确认行
     */
    List<AlipayImportRow> preview(byte[] bytes, String fileName);
}

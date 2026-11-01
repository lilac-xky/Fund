package com.lilac.controller;

import com.lilac.adapter.alipay.AlipayImportRow;
import com.lilac.domain.result.Result;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.service.AlipayImportService;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 支付宝账单导入接口，把交易记录明细解析成待确认的持仓变动，不直接写库
 */
@RestController
@RequestMapping("/alipay")
public class AlipayImportController {

    @Resource
    private AlipayImportService alipayImportService;

    /**
     * 上传支付宝账单明细并给出待确认清单，解析失败明确指出原因
     */
    @PostMapping("/preview")
    public Result<List<AlipayImportRow>> preview(@RequestParam("file") MultipartFile file) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), HttpsCodeEnum.PARAMS_ERROR,
                "请选择支付宝账单文件");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            ThrowUtils.throwIf(true, HttpsCodeEnum.OPERATION_ERROR, "账单读取失败：" + e.getMessage());
            return Result.success(List.of());
        }
        String fileName = file.getOriginalFilename() == null ? "alipay.csv" : file.getOriginalFilename();
        return Result.success(alipayImportService.preview(bytes, fileName));
    }
}

package com.lilac.controller;

import com.lilac.domain.result.Result;
import com.lilac.domain.vo.FundSnapshotVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.service.FundSnapshotService;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * 基金净值与持仓接口，供预估引擎与页面取数
 */
@RestController
@RequestMapping("/fund")
public class FundSnapshotController {

    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");

    @Resource
    private FundSnapshotService fundSnapshotService;

    /**
     * 按 6 位代码取净值、持仓与三个日期，上游失败时回退最近一次成功缓存
     */
    @GetMapping("/snapshot")
    public Result<FundSnapshotVO> snapshot(@RequestParam("code") String code) {
        ThrowUtils.throwIf(code == null || !CODE_PATTERN.matcher(code.trim()).matches(),
                HttpsCodeEnum.PARAMS_ERROR, "代码必须是 6 位数字");
        return Result.success(fundSnapshotService.load(code.trim(), LocalDate.now()));
    }
}

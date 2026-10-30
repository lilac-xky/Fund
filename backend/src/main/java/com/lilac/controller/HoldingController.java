package com.lilac.controller;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.common.DeleteRequest;
import com.lilac.domain.dto.holding.HoldingBatchUpdateRequest;
import com.lilac.domain.dto.holding.HoldingBuyRequest;
import com.lilac.domain.dto.holding.HoldingChangeQueryRequest;
import com.lilac.domain.dto.holding.HoldingChangeRevokeRequest;
import com.lilac.domain.dto.holding.HoldingConvertRequest;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.holding.HoldingSellRequest;
import com.lilac.domain.entity.Holding;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.HoldingChangeVO;
import com.lilac.domain.vo.HoldingConvertVO;
import com.lilac.domain.vo.HoldingOverviewVO;
import com.lilac.domain.vo.HoldingSummaryVO;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.mapper.HoldingMapper;
import com.lilac.service.HoldingService;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 持仓接口，覆盖买入、卖出、总览、明细与变动历史
 */
@RestController
@RequestMapping("/holding")
public class HoldingController {

    @Resource
    private HoldingService holdingService;

    @Resource
    private HoldingMapper holdingMapper;

    /**
     * 统一查询持仓，各条件为空时不过滤
     */
    @PostMapping("/list")
    public Result<List<HoldingVO>> list(@RequestBody(required = false) HoldingQueryRequest request) {
        return Result.success(holdingService.listHolding(request));
    }

    /**
     * 持仓总览，按品种聚合并带出名称与最新净值
     */
    @GetMapping("/overview")
    public Result<HoldingOverviewVO> overview() {
        return Result.success(holdingService.overview());
    }

    /**
     * 按品种汇总持有中份额与加权成本，供消歧候选回显
     */
    @GetMapping("/summary")
    public Result<HoldingSummaryVO> summary(@RequestParam("varietyKey") String varietyKey) {
        ThrowUtils.throwIf(varietyKey == null || varietyKey.isBlank(), HttpsCodeEnum.PARAMS_ERROR, "品种业务主键不能为空");
        return Result.success(holdingService.summaryOf(varietyKey.trim()));
    }

    /**
     * 买入入库，校验失败拒绝并指出原因
     */
    @PostMapping("/buy")
    public Result<HoldingVO> buy(@RequestBody HoldingBuyRequest request) {
        return Result.success(holdingService.buy(request));
    }

    /**
     * 卖出扣减，按先进先出扣已有持仓
     */
    @PostMapping("/sell")
    public Result<List<HoldingVO>> sell(@RequestBody HoldingSellRequest request) {
        return Result.success(holdingService.sell(request));
    }

    /**
     * 基金转换，同一事务内先卖出再买入，任一步失败整笔回滚
     */
    @PostMapping("/convert")
    public Result<HoldingConvertVO> convert(@RequestBody HoldingConvertRequest request) {
        return Result.success(holdingService.convert(request));
    }

    /**
     * 更正某个持仓批次的份额、成本价与建仓日期，供录入出错时改正
     */
    @PostMapping("/batch/update")
    public Result<HoldingVO> updateBatch(@RequestBody HoldingBatchUpdateRequest request) {
        return Result.success(holdingService.updateBatch(request));
    }

    /**
     * 撤销某个持仓批次，已卖出时拒绝并提示改为更正
     */
    @PostMapping("/batch/revoke")
    public Result<Boolean> revokeBatch(@RequestBody HoldingChangeRevokeRequest request) {
        return Result.success(holdingService.revokeBatch(request));
    }

    /**
     * 查询持仓变动历史，按时间倒序
     */
    @PostMapping("/changes")
    public Result<List<HoldingChangeVO>> changes(@RequestBody(required = false) HoldingChangeQueryRequest request) {
        return Result.success(holdingService.listChanges(request));
    }

    /**
     * 按主键查询单个持仓
     */
    @GetMapping("/get")
    public Result<HoldingVO> getById(Long id) {
        ThrowUtils.throwIf(id == null, HttpsCodeEnum.PARAMS_ERROR);
        Holding holding = holdingMapper.selectOneById(id);
        ThrowUtils.throwIf(holding == null, HttpsCodeEnum.NOT_FOUND_ERROR);
        return Result.success(BeanUtil.copyProperties(holding, HoldingVO.class));
    }

    /**
     * 按主键逻辑删除持仓
     */
    @PostMapping("/delete")
    public Result<Boolean> delete(@RequestBody DeleteRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, HttpsCodeEnum.PARAMS_ERROR);
        return Result.success(holdingMapper.deleteById(request.getId()) > 0);
    }
}

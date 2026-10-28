package com.lilac.setting;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.constant.AlertParamKey;
import com.lilac.domain.dto.param.ParamQueryRequest;
import com.lilac.domain.vo.StrategyParamVO;
import com.lilac.engine.EstimateThresholds;
import com.lilac.enums.ParamGroup;
import com.lilac.service.StrategyParamService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 参数覆盖解析器，把设置页可改的阈值盖到配置文件默认值上，让页面改动真正生效
 */
@Slf4j
@Component
public class SettingOverrideResolver {

    /**
     * 债券误差阈值的百分点上限，超过它这条阈值就失去意义
     */
    private static final BigDecimal MAX_BOND_THRESHOLD = new BigDecimal("5");

    @Resource
    private StrategyParamService strategyParamService;

    /**
     * 生成一份带页面覆盖值的阈值配置，未配置或非法时沿用配置文件的值
     *
     * @param base 配置文件生成的阈值
     * @return 生效阈值，base 为 null 时返回 null
     */
    public EstimateThresholds effective(EstimateThresholds base) {
        if (base == null) {
            return null;
        }
        BigDecimal bond = bondErrorThreshold();
        if (bond == null || bond.compareTo(base.getBondErrorThreshold()) == 0) {
            return base;
        }
        // 复制一份再改，避免把页面值写回共享的单例
        EstimateThresholds copy = new EstimateThresholds();
        BeanUtil.copyProperties(base, copy);
        copy.setBondErrorThreshold(bond);
        return copy;
    }

    /**
     * 读取页面设置的债券误差阈值，未设置或非法返回 null
     *
     * @return 阈值，百分点
     */
    public BigDecimal bondErrorThreshold() {
        String raw = alertValues().get(AlertParamKey.BOND_ERROR_THRESHOLD);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.signum() <= 0 || value.compareTo(MAX_BOND_THRESHOLD) > 0) {
                log.warn("债券误差阈值超出合理区间，沿用配置文件默认值：{}", raw);
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            log.warn("债券误差阈值不是合法数值，沿用配置文件默认值：{}", raw);
            return null;
        }
    }

    /**
     * 读 ALERT 分组的全部参数
     *
     * @return 参数键到值的映射
     */
    private Map<String, String> alertValues() {
        ParamQueryRequest query = new ParamQueryRequest();
        query.setParamGroup(ParamGroup.ALERT);
        Map<String, String> values = new LinkedHashMap<>();
        for (StrategyParamVO vo : strategyParamService.listParam(query)) {
            if (vo.getParamKey() != null) {
                values.put(vo.getParamKey(), vo.getParamValue());
            }
        }
        return values;
    }
}

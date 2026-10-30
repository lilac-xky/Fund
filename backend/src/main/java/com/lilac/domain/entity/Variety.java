package com.lilac.domain.entity;

import com.lilac.enums.DividendMethod;
import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 品种注册表，以「品种类型:代码」唯一标识一个标的
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "variety", camelToUnderline = false, comment = "品种注册表，以品种类型:代码唯一标识一个标的")
public class Variety extends BaseEntity {

    /**
     * 业务主键，形如 OF:000001、ETF:510300、IDX:000300
     */
    private String varietyKey;

    /**
     * 代码所属品种类型，决定消歧结果
     */
    private VarietyType varietyType;

    /**
     * 6 位代码
     */
    private String varietyCode;

    /**
     * 标的名称
     */
    private String varietyName;

    /**
     * 基金类型，黄金与红利无法自动识别时置为 UNKNOWN
     */
    private FundCategory category;

    /**
     * 跟踪指数代码，红利基金用于查股息率
     */
    private String trackIndexCode;

    /**
     * 分红方式，红利再投时累计净值与单位净值的差不能当现金
     */
    private DividendMethod dividendMethod;

    /**
     * 类型是否已由使用者确认，false 表示待确认
     */
    private Boolean confirmed;
}

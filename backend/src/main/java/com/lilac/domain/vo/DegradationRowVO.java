package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 降级矩阵的一行，说明某个数据源失效时受影响的环节与提示方式
 */
@Data
public class DegradationRowVO implements Serializable {

    /**
     * 数据源名
     */
    private String source;

    /**
     * 数据源用途
     */
    private String purpose;

    /**
     * 失效后是否还有备用源
     */
    private Boolean hasFallback;

    /**
     * 失效时受影响的功能
     */
    private List<String> affected;

    /**
     * 页面与邮件如何提示
     */
    private String notice;

    private static final long serialVersionUID = 1L;
}

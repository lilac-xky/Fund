package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 持仓截图留存记录，供与已录入持仓并排核对
 */
@Data
public class ScreenshotVO implements Serializable {

    /**
     * 留存文件名，含时间戳
     */
    private String fileName;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 文件大小，字节
     */
    private Long size;

    /**
     * 上传时间
     */
    private LocalDateTime uploadedAt;

    /**
     * 相对 data 目录的路径，页面据此取图
     */
    private String path;

    private static final long serialVersionUID = 1L;
}

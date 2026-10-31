package com.lilac.service;

import com.lilac.domain.vo.ScreenshotCheckVO;
import com.lilac.domain.vo.ScreenshotVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 持仓截图业务接口，负责留存截图并把它与已录入持仓并排展示供人工核对
 */
public interface ScreenshotService {

    /**
     * 保存上传的截图，只接受常见图片格式
     *
     * @param file 上传的文件
     * @return 留存信息
     */
    ScreenshotVO upload(MultipartFile file);

    /**
     * 列出已留存的截图，按上传时间倒序
     *
     * @return 截图列表
     */
    List<ScreenshotVO> list();

    /**
     * 取指定截图与当前持仓的核对视图
     *
     * @param fileName 留存文件名
     * @return 核对结果
     */
    ScreenshotCheckVO check(String fileName);

    /**
     * 读取截图字节，供页面显示
     *
     * @param fileName 留存文件名
     * @return 图片字节
     */
    byte[] read(String fileName);
}

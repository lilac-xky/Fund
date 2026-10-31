package com.lilac.controller;

import com.lilac.domain.result.Result;
import com.lilac.domain.vo.ScreenshotCheckVO;
import com.lilac.domain.vo.ScreenshotVO;
import com.lilac.service.ScreenshotService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

/**
 * 持仓截图接口，只做留存与并排展示供人工核对，不做图片识别
 */
@RestController
@RequestMapping("/screenshot")
public class ScreenshotController {

    @Resource
    private ScreenshotService screenshotService;

    /**
     * 上传一张持仓截图，支持 png / jpg / jpeg / webp，上限 10MB
     */
    @PostMapping("/upload")
    public Result<ScreenshotVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(screenshotService.upload(file));
    }

    /**
     * 列出已留存的截图，按上传时间倒序
     */
    @GetMapping("/list")
    public Result<List<ScreenshotVO>> list() {
        return Result.success(screenshotService.list());
    }

    /**
     * 取指定截图与当前持仓的核对视图
     */
    @GetMapping("/check")
    public Result<ScreenshotCheckVO> check(@RequestParam("fileName") String fileName) {
        return Result.success(screenshotService.check(fileName));
    }

    /**
     * 直接返回图片字节，供页面 img 标签显示；二进制流无法包进 Result，属规范里唯一的例外
     */
    @GetMapping("/raw")
    public ResponseEntity<byte[]> raw(@RequestParam("fileName") String fileName) {
        byte[] bytes = screenshotService.read(fileName);
        return ResponseEntity.ok().contentType(mediaTypeOf(fileName)).body(bytes);
    }

    /**
     * 按扩展名给出图片类型，img 标签需要正确的 Content-Type 才能渲染
     *
     * @param fileName 文件名
     * @return 媒体类型
     */
    private MediaType mediaTypeOf(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (name.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}

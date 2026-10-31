package com.lilac.report;

import com.lilac.domain.vo.ScreenshotCheckVO;
import com.lilac.domain.vo.ScreenshotVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import com.lilac.service.HoldingService;
import com.lilac.service.impl.ScreenshotServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 持仓截图测试，重点是格式与大小校验、文件名穿越防护、以及「不假装能自动识别」
 */
class ScreenshotTest {

    @TempDir
    Path tempDir;

    private ScreenshotServiceImpl service;

    /**
     * 装配服务，数据目录指到临时目录
     */
    @BeforeEach
    void setUp() {
        service = new ScreenshotServiceImpl(mock(HoldingService.class));
        ReflectionTestUtils.setField(service, "dataRootResolver",
                new com.lilac.config.DataRootResolver(tempDir.toString()));
        service.initDirectory();
    }

    /**
     * 正常上传要落盘并返回带时间戳的文件名
     */
    @Test
    @DisplayName("上传 png 后落盘并可列出")
    void uploadAndList() {
        MockMultipartFile file = new MockMultipartFile("file", "我的持仓.png", "image/png",
                new byte[]{1, 2, 3, 4});

        ScreenshotVO vo = service.upload(file);

        assertThat(vo.getFileName()).startsWith("holding-").endsWith(".png");
        assertThat(vo.getSize()).isEqualTo(4L);
        assertThat(service.list()).hasSize(1);
    }

    /**
     * 非图片格式要被拒绝，否则目录会被任意文件塞满
     */
    @Test
    @DisplayName("拒绝非图片格式")
    void rejectsNonImage() {
        MockMultipartFile file = new MockMultipartFile("file", "恶意.exe", "application/octet-stream",
                new byte[]{1});

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只支持");
    }

    /**
     * 空文件要被拒绝
     */
    @Test
    @DisplayName("拒绝空文件")
    void rejectsEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择");
    }

    /**
     * 文件名带 ../ 时必须被拦住，不能读到目录外
     */
    @Test
    @DisplayName("文件名穿越被拒绝")
    void rejectsPathTraversal() throws Exception {
        Path outside = tempDir.getParent().resolve("secret.txt");
        Files.writeString(outside, "不该被读到");

        assertThatThrownBy(() -> service.read("../../secret.txt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(HttpsCodeEnum.NOT_FOUND_ERROR.getCode()));
    }

    /**
     * 不存在的文件名要给「截图不存在」而不是抛出文件系统异常
     */
    @Test
    @DisplayName("不存在的截图给出业务异常")
    void missingFile() {
        assertThatThrownBy(() -> service.read("nope.png"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    /**
     * 核对结果必须明确说明不做自动识别，不能让人以为差异已被自动判出
     */
    @Test
    @DisplayName("核对结果说明不做自动识别")
    void checkExplainsNoOcr() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{9});
        ScreenshotVO vo = service.upload(file);

        ScreenshotCheckVO check = service.check(vo.getFileName());

        assertThat(check.getScreenshot().getFileName()).isEqualTo(vo.getFileName());
        assertThat(check.getNotice()).contains("不做自动识别");
        assertThat(check.getRows()).isEmpty();
    }

    /**
     * 列表按上传时间倒序，最新的一张排最前
     */
    @Test
    @DisplayName("列表按时间倒序")
    void listSortedDesc() throws Exception {
        service.upload(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1}));
        Thread.sleep(1100);
        ScreenshotVO second = service.upload(new MockMultipartFile("file", "b.png", "image/png", new byte[]{2}));

        List<ScreenshotVO> list = service.list();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getFileName()).isEqualTo(second.getFileName());
    }
}

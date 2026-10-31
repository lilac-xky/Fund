package com.lilac.service.impl;

import com.lilac.domain.vo.HoldingOverviewVO;
import com.lilac.domain.vo.HoldingVarietyVO;
import com.lilac.domain.vo.ScreenshotCheckVO;
import com.lilac.domain.vo.ScreenshotVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import com.lilac.service.HoldingService;
import com.lilac.service.ScreenshotService;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 持仓截图业务实现，只做留存与并排展示，不解析图片内容
 */
@Slf4j
@Service
public class ScreenshotServiceImpl implements ScreenshotService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<String> ALLOWED_EXT = List.of("png", "jpg", "jpeg", "webp");
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    @Value("${fund.data.root:data}")
    private String dataRoot;

    private final HoldingService holdingService;

    /**
     * 注入持仓业务，核对视图要读当前持仓
     *
     * @param holdingService 持仓业务
     */
    public ScreenshotServiceImpl(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    /**
     * 启动时建好截图目录，避免首次上传才暴露路径问题
     */
    @PostConstruct
    public void initDirectory() {
        try {
            Files.createDirectories(dir());
        } catch (IOException e) {
            log.warn("截图目录创建失败：{}", e.getMessage());
        }
    }

    /**
     * 保存上传的截图
     *
     * @param file 上传的文件
     * @return 留存信息
     */
    @Override
    public ScreenshotVO upload(MultipartFile file) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), HttpsCodeEnum.PARAMS_ERROR, "请选择一个图片文件");
        ThrowUtils.throwIf(file.getSize() > MAX_BYTES, HttpsCodeEnum.PARAMS_ERROR,
                "图片不能超过 10MB");
        String original = file.getOriginalFilename() == null ? "screenshot" : file.getOriginalFilename();
        String ext = extensionOf(original);
        ThrowUtils.throwIf(!ALLOWED_EXT.contains(ext), HttpsCodeEnum.PARAMS_ERROR,
                "只支持 png / jpg / jpeg / webp，当前是 " + ext);
        String fileName = "holding-" + LocalDateTime.now().format(STAMP) + "." + ext;
        Path target = dir().resolve(fileName);
        try {
            Files.createDirectories(dir());
            file.transferTo(target.toAbsolutePath().normalize());
        } catch (IOException e) {
            log.warn("截图保存失败 {}：{}", fileName, e.getMessage());
            ThrowUtils.throwIf(true, HttpsCodeEnum.OPERATION_ERROR, "截图保存失败：" + e.getMessage());
        }
        log.info("持仓截图已留存 {}，{} 字节", fileName, file.getSize());
        return toVO(target, original);
    }

    /**
     * 列出已留存的截图
     *
     * @return 截图列表，按上传时间倒序
     */
    @Override
    public List<ScreenshotVO> list() {
        List<ScreenshotVO> result = new ArrayList<>();
        if (!Files.exists(dir())) {
            return result;
        }
        try (Stream<Path> stream = Files.list(dir())) {
            result = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> ALLOWED_EXT.contains(extensionOf(path.getFileName().toString())))
                    .map(path -> toVO(path, path.getFileName().toString()))
                    .sorted(Comparator.comparing(ScreenshotVO::getUploadedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .toList();
        } catch (IOException e) {
            log.warn("截图目录读取失败：{}", e.getMessage());
        }
        return result;
    }

    /**
     * 取核对视图，逐只列出已录入数字供与截图比对
     *
     * @param fileName 留存文件名
     * @return 核对结果
     */
    @Override
    public ScreenshotCheckVO check(String fileName) {
        Path target = resolve(fileName);
        ScreenshotCheckVO vo = new ScreenshotCheckVO();
        vo.setScreenshot(toVO(target, target.getFileName().toString()));
        HoldingOverviewVO overview = holdingService.overview();
        List<ScreenshotCheckVO.CheckRow> rows = new ArrayList<>();
        // 持仓侧异常时核对页仍要能打开，否则连截图都看不到
        List<HoldingVarietyVO> varieties = overview == null || overview.getItems() == null
                ? List.of() : overview.getItems();
        for (HoldingVarietyVO variety : varieties) {
            ScreenshotCheckVO.CheckRow row = new ScreenshotCheckVO.CheckRow();
            row.setVarietyKey(variety.getVarietyKey());
            row.setVarietyCode(variety.getVarietyCode());
            row.setVarietyName(variety.getVarietyName());
            row.setShares(variety.getTotalShares());
            row.setAvgCostPrice(variety.getAvgCostPrice());
            row.setMarketValue(variety.getMarketValue());
            row.setStatus("待人工核对");
            row.setHints(hintsOf(variety));
            rows.add(row);
        }
        vo.setRows(rows);
        // 没有 OCR，差异数无法自动判定，只能提示需要比对的项数，不编造「差异为 0」
        vo.setDiffCount(rows.size());
        // 「不做自动识别」任何时候都要说，否则使用者会以为差异已被自动判出
        String base = "图片不做自动识别，请对照左边的截图逐只核对上面的份额、成本价与金额；"
                + "本页只保证数字来自当前库内记录";
        vo.setNotice(rows.isEmpty() ? "尚无持仓可核对，请先到「持仓」页录入。" + base : base);
        return vo;
    }

    /**
     * 读取截图字节
     *
     * @param fileName 留存文件名
     * @return 图片字节
     */
    @Override
    public byte[] read(String fileName) {
        Path target = resolve(fileName);
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            log.warn("截图读取失败 {}：{}", fileName, e.getMessage());
            throw new BusinessException(HttpsCodeEnum.NOT_FOUND_ERROR,
                    "截图不存在或无法读取");
        }
    }

    /**
     * 拼每只基金需要人工比对的字段提示
     *
     * @param variety 持仓行
     * @return 提示
     */
    private List<String> hintsOf(HoldingVarietyVO variety) {
        List<String> hints = new ArrayList<>();
        if (variety.getTotalShares() == null) {
            hints.add("份额缺失，请从截图核对后补录");
        }
        if (variety.getAvgCostPrice() == null) {
            hints.add("成本价缺失");
        }
        if (variety.getMarketValue() == null) {
            hints.add("市值取不到（缺最新净值或行情）");
        }
        if (hints.isEmpty()) {
            hints.add("份额 " + plain(variety.getTotalShares())
                    + " · 成本价 " + plain(variety.getAvgCostPrice()));
        }
        return hints;
    }

    /**
     * 数值转文本
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * 解析并校验文件名，禁止路径穿越
     *
     * @param fileName 文件名
     * @return 绝对路径
     */
    private Path resolve(String fileName) {
        ThrowUtils.throwIf(fileName == null || fileName.isBlank(), HttpsCodeEnum.PARAMS_ERROR,
                "文件名不能为空");
        Path base = dir().toAbsolutePath().normalize();
        Path target = base.resolve(fileName).normalize();
        // 只允许读取本目录下的文件，防止 ../ 穿越到别处
        ThrowUtils.throwIf(!target.startsWith(base) || !Files.exists(target),
                HttpsCodeEnum.NOT_FOUND_ERROR, "截图不存在：" + fileName);
        return target;
    }

    /**
     * 截图目录
     *
     * @return 目录路径
     */
    private Path dir() {
        return Paths.get(dataRoot).toAbsolutePath().normalize().resolve("screenshot");
    }

    /**
     * 取扩展名，无扩展名返回空串
     *
     * @param name 文件名
     * @return 小写扩展名
     */
    private String extensionOf(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 路径转视图
     *
     * @param path         文件路径
     * @param originalName 原始文件名
     * @return 视图
     */
    private ScreenshotVO toVO(Path path, String originalName) {
        ScreenshotVO vo = new ScreenshotVO();
        vo.setFileName(path.getFileName().toString());
        vo.setOriginalName(originalName);
        vo.setPath("screenshot/" + path.getFileName());
        try {
            vo.setSize(Files.size(path));
            vo.setUploadedAt(LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault()));
        } catch (IOException e) {
            log.warn("截图属性读取失败 {}：{}", path.getFileName(), e.getMessage());
        }
        return vo;
    }
}

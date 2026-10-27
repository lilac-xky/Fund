package com.lilac.adapter.eastmoney;

import com.lilac.adapter.http.TextFetcher;
import com.lilac.adapter.model.ExchangeIdentity;
import com.lilac.adapter.model.ExchangeKind;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 交易场所身份适配器，用腾讯行情接口按「沪 sh / 深 sz」两个前缀回显名称与类型标记
 */
@Slf4j
@Component
public class ExchangeIdentityAdapter {

    private static final Pattern QUOTE_PATTERN = Pattern.compile("v_([a-z]{2}\\d{6})=\"([^\"]*)\"");
    private static final int FIELD_NAME = 1;
    private static final int FIELD_TYPE = 61;
    private static final Charset GBK = Charset.forName("GBK");

    private final TextFetcher textFetcher;
    private final String quoteUrl;

    /**
     * 注入取数封装与行情地址
     *
     * @param textFetcher 文本取数接口
     * @param quoteUrl    腾讯行情地址
     */
    public ExchangeIdentityAdapter(TextFetcher textFetcher,
                                   @Value("${fund.adapter.tencent.quote-url}") String quoteUrl) {
        this.textFetcher = textFetcher;
        this.quoteUrl = quoteUrl;
    }

    /**
     * 批量回显交易场所身份，同码在沪市与深市各有一条时全部保留
     *
     * @param codes 6 位代码集合
     * @return 代码到身份列表的有序映射
     */
    public Map<String, List<ExchangeIdentity>> resolve(List<String> codes) {
        Map<String, List<ExchangeIdentity>> result = new LinkedHashMap<>();
        if (codes == null || codes.isEmpty()) {
            return result;
        }
        String text = textFetcher.getText(quoteUrl + buildQuery(codes), GBK);
        Matcher matcher = QUOTE_PATTERN.matcher(text);
        while (matcher.find()) {
            String[] fields = matcher.group(2).split("~", -1);
            if (fields.length <= FIELD_TYPE) {
                continue;
            }
            ExchangeKind kind = toKind(fields[FIELD_TYPE]);
            if (kind == null) {
                continue;
            }
            String code = matcher.group(1).substring(2);
            result.computeIfAbsent(code, key -> new ArrayList<>())
                    .add(new ExchangeIdentity(code, fields[FIELD_NAME], kind));
        }
        log.info("交易场所身份回显完成，候选 {} 个，命中 {} 个", codes.size(), result.size());
        return result;
    }

    /**
     * 单个代码的全部身份
     *
     * @param code 6 位代码
     * @return 身份列表，未命中返回空列表
     */
    public List<ExchangeIdentity> resolveAll(String code) {
        return resolve(List.of(code)).getOrDefault(code, List.of());
    }

    /**
     * 把腾讯类型标记映射为身份类型，未知类型返回 null
     *
     * @param marker 类型标记，如 ZS、ETF、LOF、GP-A
     * @return 身份类型
     */
    private ExchangeKind toKind(String marker) {
        return switch (marker) {
            case "ZS" -> ExchangeKind.INDEX;
            case "ETF" -> ExchangeKind.ETF;
            case "LOF" -> ExchangeKind.LOF;
            default -> marker != null && marker.startsWith("GP") ? ExchangeKind.STOCK : null;
        };
    }

    /**
     * 按沪市 sh、深市 sz 各拼一次查询串，让同名代码的两个市场都参与比对
     *
     * @param codes 6 位代码集合
     * @return 查询参数，形如 sh000001,sz000001
     */
    private String buildQuery(List<String> codes) {
        List<String> symbols = new ArrayList<>(codes.size() * 2);
        for (String code : codes) {
            symbols.add("sh" + code);
            symbols.add("sz" + code);
        }
        return String.join(",", symbols);
    }
}

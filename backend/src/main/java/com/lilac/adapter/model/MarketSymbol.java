package com.lilac.adapter.model;

import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 行情标的，由交易所前缀与数字代码组成，前缀决定走哪个行情接口
 */
@Data
@NoArgsConstructor
public class MarketSymbol {

    private static final Pattern PREFIXED = Pattern.compile("^(sh|sz|bj|hk)(\\d{5,6})$");
    private static final Pattern DIGITS = Pattern.compile("^\\d{5,6}$");
    private static final String SH = "sh";
    private static final String SZ = "sz";
    private static final String BJ = "bj";
    private static final String HK = "hk";

    /**
     * 补查命中后的市场记忆，避免同一 000 段代码每次都先打错市场
     */
    private static final Map<String, String> RESOLVED_MARKETS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 记忆上限，防止异常代码把表撑大
     */
    private static final int MAX_REMEMBERED = 200;

    /**
     * 交易所前缀，sh / sz / bj / hk
     */
    private String prefix;

    /**
     * 数字代码，A 股 6 位、港股 5 位
     */
    private String code;

    /**
     * 直接构造一个标的，供内部与反序列化使用
     *
     * @param prefix 交易所前缀
     * @param code   数字代码
     */
    public MarketSymbol(String prefix, String code) {
        this.prefix = prefix;
        this.code = code;
    }

    /**
     * 带前缀的完整代码，如 sh600519
     *
     * @return 完整代码
     */
    public String symbol() {
        return prefix + code;
    }

    /**
     * 是否港股
     *
     * @return 港股返回 true
     */
    public boolean hongKong() {
        return HK.equals(prefix);
    }

    /**
     * 该代码是否可能落在沪深两个市场，000 段在沪是上证指数、在深是平安银行等个股
     *
     * @return 需要到另一个市场补查返回 true
     */
    public boolean ambiguous() {
        return !hongKong() && !BJ.equals(prefix) && code.startsWith("000");
    }

    /**
     * 另一个市场的同码标的，用于 000300 这类沪市指数的补查
     *
     * @return 另一市场的标的，港股与北交所返回 null
     */
    public MarketSymbol sibling() {
        if (HK.equals(prefix) || BJ.equals(prefix)) {
            return null;
        }
        return new MarketSymbol(SH.equals(prefix) ? SZ : SH, code);
    }

    /**
     * 按裸代码推断交易所前缀，带前缀时直接用
     *
     * @param rawCode 5 位或 6 位代码，也可带 sh/sz/bj/hk 前缀
     * @return 行情标的
     */
    public static MarketSymbol of(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new BusinessException(HttpsCodeEnum.PARAMS_ERROR, "行情代码不能为空");
        }
        String value = rawCode.trim().toLowerCase();
        var matcher = PREFIXED.matcher(value);
        if (matcher.find()) {
            return new MarketSymbol(matcher.group(1), matcher.group(2));
        }
        if (!DIGITS.matcher(value).matches()) {
            throw new BusinessException(HttpsCodeEnum.PARAMS_ERROR, "行情代码格式错误：" + rawCode);
        }
        // 000 段曾补查命中的市场记在进程内，下一次直接走对的市场，不再每次都先打错一遍
        String remembered = RESOLVED_MARKETS.get(value);
        return new MarketSymbol(remembered != null ? remembered : inferPrefix(value), value);
    }

    /**
     * 记录一次补查命中后的市场前缀，供同码标的下次直接使用
     *
     * @param code   数字代码
     * @param prefix 命中的市场前缀
     */
    public static void rememberMarket(String code, String prefix) {
        if (code != null && prefix != null && RESOLVED_MARKETS.size() < MAX_REMEMBERED) {
            RESOLVED_MARKETS.putIfAbsent(code, prefix);
        }
    }

    /**
     * 取前缀推断表，供测试核对记忆行为
     *
     * @return 只读视图
     */
    public static Map<String, String> rememberedMarkets() {
        return java.util.Collections.unmodifiableMap(RESOLVED_MARKETS);
    }

    /**
     * 按号段推断前缀，港股 5 位、沪 6/5、深 0/1/2/3、北交所 4/8/92
     *
     * @param code 数字代码
     * @return 前缀
     */
    private static String inferPrefix(String code) {
        if (code.length() == 5) {
            return HK;
        }
        String head = code.substring(0, 1);
        return switch (head) {
            case "6", "5" -> SH;
            case "0", "1", "2", "3" -> SZ;
            case "4", "8" -> BJ;
            case "9" -> code.startsWith("92") ? BJ : SH;
            default -> SH;
        };
    }
}

package com.lilac.adapter.alipay;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 基金名称相似度匹配，处理蚂蚁财富与天天基金对同一只基金的不同叫法
 */
@Component
public class FundNameMatcher {

    /**
     * 份额类别字母，末尾是这些之一时单独比对，A 类不能匹配到 C 类
     */
    private static final String CLASS_LETTERS = "ABCDEIY";

    /**
     * 归一化时丢掉的字符：标点、括号、空白、连接符
     */
    private static final List<Character> DROPPED = List.of(
            ' ', '\t', '\r', '\n', '　',
            '（', '）', '(', ')', '【', '】', '[', ']', '{', '}', '《', '》',
            '、', '，', ',', '.', '。', '·', '-', '—', '－', '_', '/', '\\', '|',
            ':', '：', ';', '；', '!', '！', '?', '？', '"', '\'', '“', '”', '‘', '’',
            '#', '&', '@', '%', '+', '*', '=');

    /**
     * 归一化：全角转半角、丢标点空白、字母统一大写
     *
     * @param raw 原始名称
     * @return 归一化名称
     */
    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(raw.length());
        for (char ch : raw.toCharArray()) {
            if (DROPPED.contains(ch)) {
                continue;
            }
            builder.append(toHalfWidth(ch));
        }
        return builder.toString().toUpperCase(Locale.ROOT);
    }

    /**
     * 全角字母数字转半角，其余原样
     *
     * @param ch 原始字符
     * @return 转换后的字符
     */
    private char toHalfWidth(char ch) {
        // 全角字母数字在 0xFF01 到 0xFF5E，与半角差 0xFEE0
        if (ch >= '\uFF01' && ch <= '\uFF5E') {
            return (char) (ch - 0xFEE0);
        }
        return ch;
    }

    /**
     * 取份额类别字母，末尾不是字母类别时返回空串
     *
     * @param normalized 归一化名称
     * @return 类别字母，如 C
     */
    public String shareClass(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return "";
        }
        char last = normalized.charAt(normalized.length() - 1);
        return CLASS_LETTERS.indexOf(last) >= 0 ? String.valueOf(last) : "";
    }

    /**
     * 判断某名称是否值得作为候选：字符逐个出现即可，不要求连续子串
     *
     * <p>账单「南方红利低波50ETF联接C」与全集「南方标普红利低波50ETF联接C」中间插了「标普」，
     * 用 SQL 的 LIKE '%账单名%' 查不到，故先在本地按字符覆盖率筛一遍再算相似度。</p>
     *
     * @param target     归一化后的目标名称
     * @param candidate  归一化后的候选名称
     * @param coverage   要求的字符覆盖率，0 到 1
     * @return 值得细算返回 true
     */
    public boolean covers(String target, String candidate, double coverage) {
        if (target == null || candidate == null || target.isEmpty() || candidate.isEmpty()) {
            return false;
        }
        if (candidate.contains(target)) {
            return true;
        }
        int hit = 0;
        int cursor = 0;
        for (char ch : target.toCharArray()) {
            int found = candidate.indexOf(ch, cursor);
            if (found >= 0) {
                hit++;
                cursor = found + 1;
            }
        }
        return (double) hit / target.length() >= coverage;
    }

    /**
     * 判断两个名称的份额类别是否可对，都无类别视为可对
     *
     * @param left  归一化名称
     * @param right 归一化名称
     * @return 可比返回 true
     */
    public boolean classCompatible(String left, String right) {
        String leftClass = shareClass(left);
        String rightClass = shareClass(right);
        if (leftClass.isEmpty() || rightClass.isEmpty()) {
            return true;
        }
        return leftClass.equals(rightClass);
    }

    /**
     * 相似度：以最长公共子序列算 Dice 系数，取值 0 到 1
     *
     * @param left  归一化名称
     * @param right 归一化名称
     * @return 相似度
     */
    public double similarity(String left, String right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        if (left.equals(right)) {
            return 1;
        }
        int lcs = longestCommonSubsequence(left, right);
        return (2.0 * lcs) / (left.length() + right.length());
    }

    /**
     * 最长公共子序列长度，名字都很短故直接二维动态规划
     *
     * @param left  左串
     * @param right 右串
     * @return 长度
     */
    private int longestCommonSubsequence(String left, String right) {
        int rows = left.length();
        int columns = right.length();
        int[] previous = new int[columns + 1];
        int[] current = new int[columns + 1];
        for (int row = 1; row <= rows; row++) {
            char leftChar = left.charAt(row - 1);
            for (int column = 1; column <= columns; column++) {
                if (leftChar == right.charAt(column - 1)) {
                    current[column] = previous[column - 1] + 1;
                } else {
                    current[column] = Math.max(previous[column], current[column - 1]);
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
            java.util.Arrays.fill(current, 0);
        }
        return previous[columns];
    }
}

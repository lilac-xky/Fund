package com.lilac.adapter.fund;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.lilac.adapter.http.TextFetcher;
import com.lilac.adapter.model.FundCatalogItem;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 天天基金代码全集适配器，去 JS 前缀后解析为基金数组
 */
@Slf4j
@Component
public class FundCatalogAdapter {

    private static final String ARRAY_START = "[";
    private static final String ARRAY_END = "]";
    private static final int EXPECTED_COLUMNS = 5;

    private final TextFetcher textFetcher;
    private final String universeUrl;

    /**
     * 注入取数封装与全集地址
     *
     * @param textFetcher 文本取数接口
     * @param universeUrl 基金全集 JS 地址
     */
    public FundCatalogAdapter(TextFetcher textFetcher,
                              @Value("${fund.adapter.eastmoney.fund-universe-url}") String universeUrl) {
        this.textFetcher = textFetcher;
        this.universeUrl = universeUrl;
    }

    /**
     * 拉取并解析基金全集，结构变化或为空时抛具名异常
     *
     * @return 基金全集数组
     */
    public List<FundCatalogItem> fetchAll() {
        List<FundCatalogItem> items = parse(textFetcher.getText(universeUrl));
        log.info("基金全集拉取完成，共 {} 条", items.size());
        return items;
    }

    /**
     * 从 "var r = [[...]];" 中截出数组并逐行转对象
     *
     * @param text 原始 JS 文本
     * @return 基金全集数组
     */
    List<FundCatalogItem> parse(String text) {
        int start = text.indexOf(ARRAY_START);
        int end = text.lastIndexOf(ARRAY_END);
        if (start < 0 || end <= start) {
            throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "基金全集返回结构变化：未找到数组边界");
        }
        JSONArray array;
        try {
            array = JSONUtil.parseArray(text.substring(start, end + 1));
        } catch (Exception e) {
            throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "基金全集返回结构变化：数组解析失败");
        }
        if (array.isEmpty()) {
            throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "基金全集为空，拒绝用空数据覆盖本地缓存");
        }
        List<FundCatalogItem> items = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONArray row = array.getJSONArray(i);
            // 每行固定 5 列，列数不对说明上游改了格式；只取代码、名称、类型，两列拼音用不上
            if (row == null || row.size() != EXPECTED_COLUMNS) {
                throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR,
                        "基金全集返回结构变化：第 " + i + " 行列数不是 " + EXPECTED_COLUMNS);
            }
            items.add(new FundCatalogItem(row.getStr(0), row.getStr(2), row.getStr(3)));
        }
        return items;
    }
}

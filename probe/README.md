# 探针脚本（ISSUE-001）

验证「重仓股 + 指数」推算预估能达到什么精度。**一次性研究代码，不进入主系统**，
不引入 akshare / pandas / numpy，只依赖 `requests`，避免绑定 Python 运行时。

结论与完整分析见 [`REPORT.md`](REPORT.md)。

## 运行

```bash
# 首次或需要重新取数（缓存写在 probe/.cache/，约 100 MB）
python probe/run_probe.py

# 已有缓存时重跑（约 2 分钟）
python probe/run_probe.py

# 自定义回看窗口（自然日），默认 92 天
python probe/run_probe.py --days 400

# 只跑部分基金
python probe/run_probe.py --only 005827,090010

# 生成汇总分析表
python probe/analyze.py
```

## 目录

```
probe/
├── run_probe.py          # 主流程：逐基金 × 逐方案 × 逐交易日算误差
├── analyze.py            # 把 detail_all.csv 加工成报告引用的表格
├── lib/
│   ├── sources.py        # 取数层：天天基金（净值/持仓/公告）+ 腾讯新浪（行情/汇率）
│   ├── estimate.py       # 预估方案与误差统计（含滚动 OLS 对照）
│   └── sample.py         # 15 只样本基金的选取与对标指数
├── .cache/               # 磁盘缓存（不入库）
└── out/                  # 结果（不入库）
```

## 七个方案对照

| 方案 | 已披露部分 | 残差部分 |
|------|-----------|---------|
| `index_only` | 无（纯指数基线） | 全部按指数 |
| `top10_index` | 前十大重仓股 | `(1−Σw)` 按指数 ← **ISSUE-001 原始口径** |
| `full_index` | 全量可披露持仓 | `(1−Σw)` 按指数 |
| `full_cash` | 全量可披露持仓 | `(1−Σw)` 按 **0** 计 |
| `full_pos` | 全量持仓归一后放大到股票仓位 | 按 0 |
| `ols_roll` | 无（滚动 OLS 回归对照） | β × 指数 |
| `zero` | 一律 0%（"不预估"对照） | — |

## 关键实现约束（实测得来，改动前请先读报告）

1. `Data_fundSharesPositions` 是**仓位时序**，不是重仓股权重。权重走 `fundf10` 的 `jjcc` 接口。
2. 季报只有前十大，中报/年报才全量。回测必须**滚动前瞻**，否则偷看未来。
3. 东财 `push2his` 会限流，行情必须多源回退（腾讯 → 新浪；港股用 `hkfqkline`）。
4. K 线用前复权，不复权在除权日会产生假跳空。
5. 东财净值时间戳是北京时间零点，须按 UTC+8 取日期。

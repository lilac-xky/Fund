"""预估方案与误差统计。

探针要回答的不是「某一种方案行不行」，而是「这一类方案里最好的那种能到什么精度」，
所以并排实现多种权重方案，用同一批数据算误差分布再比较。

记号（d 为估值日）：
    w_i          第 i 只重仓股占基金净值的比例（季报披露值，百分数）
    r_i(d)       第 i 只重仓股当日涨跌（百分点）
    covered      当日能取到行情的重仓股权重合计
    contrib      Σ w_i · r_i —— 已披露部分的加权贡献
    r_idx(d)     对标指数当日涨跌
    pos          基金季末股票占净比（Data_assetAllocation）

各方案：
    proxy         用底层可交易标的（黄金 ETF、债券指数 ETF 等）的涨跌直接当预估
    top10_index   前十大重仓股加权 + (1 − covered) · r_idx   ← ISSUE-001 原始口径
    full_index    全部可披露持仓加权 + (1 − covered) · r_idx
    full_cash     全部可披露持仓加权，残差按 0 计（视为现金/债）
    full_pos      已披露部分按 covered 归一后放大到股票仓位 pos，其余按 0 计
    index_only    纯指数基线（对照，衡量「预测能力为 0」时的误差）
    zero          一律预估 0%（对照，衡量「不预估」时的误差）
    ols_roll      滚动 OLS：用过去 N 日实际净值对指数回归，预测当日（对照）
"""

from __future__ import annotations

import datetime as dt
import math
from dataclasses import dataclass, field
from typing import Callable

# ------------------------------------------------------------------ 数据结构


@dataclass
class Holding:
    code: str
    name: str
    weight: float      # 占净值比例（百分数）
    secid: str         # 东财 secid，如 "0.300308" / "116.00700"


@dataclass
class Snapshot:
    quarter: str
    cutoff: dt.date
    disclosed: dt.date | None
    holdings: list[Holding]

    @property
    def covered(self) -> float:
        return sum(h.weight for h in self.holdings)


@dataclass
class Series:
    """日收益序列，key 为交易日，值为百分点。"""

    by_date: dict[dt.date, float] = field(default_factory=dict)

    def get(self, day: dt.date) -> float | None:
        return self.by_date.get(day)

    def window(self, days: list[dt.date]) -> list[tuple[dt.date, float]]:
        return [(d, self.by_date[d]) for d in days if d in self.by_date]


def pct_change(rows: list[tuple[dt.date, float]]) -> Series:
    """收盘价序列 → 日涨跌幅（百分点）。前复权价保证除权日不产生假跳空。"""
    out: dict[dt.date, float] = {}
    for i in range(1, len(rows)):
        prev, cur = rows[i - 1][1], rows[i][1]
        if prev > 0:
            out[rows[i][0]] = (cur / prev - 1.0) * 100.0
    return Series(out)


# ------------------------------------------------------------------ 方案

SCHEMES = ["proxy", "top10_index", "full_index", "full_cash", "full_pos",
           "index_only", "zero", "ols_roll"]


def predict(
    scheme: str,
    holdings: list[Holding],
    legs: dict[str, Series],
    fx: Series | None,
    hk: set[str],
    day: dt.date,
    index_ret: float | None,
    pos: float | None,
) -> float | None:
    """返回当日预估涨跌幅（百分点）。数据不足时返回 None——宁可不给数，不给错数。"""
    if scheme == "zero":
        return 0.0
    if scheme == "index_only" or scheme == "proxy":
        return index_ret

    if scheme == "ols_roll":
        return None  # 由调用方单独实现

    contrib = 0.0
    covered = 0.0
    for h in holdings:
        series = legs.get(h.secid)
        if series is None:
            continue
        value = series.get(day)
        if value is None:
            continue
        if fx is not None and h.secid in hk:
            rate = fx.get(day)
            if rate is None:
                continue
            value = ((1 + value / 100) * (1 + rate / 100) - 1) * 100
        contrib += h.weight * value
        covered += h.weight

    if covered <= 0:
        return None
    contrib /= 100.0
    covered /= 100.0

    if scheme == "full_cash":
        return contrib
    if scheme == "full_pos":
        if not pos or covered <= 0:
            return None
        # contrib/covered = 已披露部分的加权平均收益（百分点）
        # 乘以股票仓位占净值的比例 (pos/100)，得到对全净值涨跌的贡献
        return contrib / covered * (pos / 100.0)
    if scheme in ("top10_index", "full_index"):
        if index_ret is None:
            return None
        return contrib + (1.0 - covered) * index_ret
    raise ValueError(f"未知方案 {scheme}")


def ols_predict(actual: Series, index: Series, days: list[dt.date],
                window: int = 40, min_obs: int = 20) -> dict[dt.date, float]:
    """滚动 OLS：t 日只用 t 日之前 window 个交易日的数据估计 alpha/beta，再预测 t 日。

    严格不使用当日及之后的实际净值，避免前视偏差。
    """
    out: dict[dt.date, float] = {}
    for i, day in enumerate(days):
        if i < min_obs:
            continue
        hist = days[max(0, i - window):i]
        xs, ys = [], []
        for d in hist:
            x, y = index.get(d), actual.get(d)
            if x is not None and y is not None:
                xs.append(x)
                ys.append(y)
        if len(xs) < min_obs:
            continue
        mx = sum(xs) / len(xs)
        my = sum(ys) / len(ys)
        var = sum((x - mx) ** 2 for x in xs)
        if var <= 0:
            continue
        beta = sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / var
        alpha = my - beta * mx
        today = index.get(day)
        if today is None:
            continue
        out[day] = alpha + beta * today
    return out


# ------------------------------------------------------------------ 统计


@dataclass
class Stats:
    n: int = 0
    mae: float = float("nan")
    median: float = float("nan")
    p90: float = float("nan")
    p95: float = float("nan")
    max_abs: float = float("nan")
    bias: float = float("nan")
    rmse: float = float("nan")
    hit: float = float("nan")     # |误差| ≤ 阈值 的比例

    def row(self) -> dict:
        return {
            "n": self.n, "MAE": self.mae, "中位数": self.median,
            "P90": self.p90, "P95": self.p95, "最大": self.max_abs,
            "偏差": self.bias, "RMSE": self.rmse, "命中率": self.hit,
        }


def quantile(values: list[float], q: float) -> float:
    if not values:
        return float("nan")
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    pos = (len(ordered) - 1) * q
    low, high = math.floor(pos), math.ceil(pos)
    if low == high:
        return ordered[int(pos)]
    return ordered[low] + (ordered[high] - ordered[low]) * (pos - low)


def summarize(errors: list[float], threshold: float = 0.30) -> Stats:
    """errors 为有符号误差（百分点）：预估 − 实际。"""
    if not errors:
        return Stats()
    abs_err = [abs(e) for e in errors]
    return Stats(
        n=len(errors),
        mae=sum(abs_err) / len(abs_err),
        median=quantile(abs_err, 0.50),
        p90=quantile(abs_err, 0.90),
        p95=quantile(abs_err, 0.95),
        max_abs=max(abs_err),
        bias=sum(errors) / len(errors),
        rmse=math.sqrt(sum(e * e for e in errors) / len(errors)),
        hit=sum(1 for e in abs_err if e <= threshold) / len(abs_err),
    )


def spearman(xs: list[float], ys: list[float]) -> float:
    """秩相关，用于判断误差是否随披露后天数单调放大。"""

    def rank(values: list[float]) -> list[float]:
        order = sorted(range(len(values)), key=lambda i: values[i])
        ranks = [0.0] * len(values)
        for position, idx in enumerate(order):
            ranks[idx] = float(position)
        return ranks

    if len(xs) < 3:
        return float("nan")
    rx, ry = rank(xs), rank(ys)
    mx, my = sum(rx) / len(rx), sum(ry) / len(ry)
    num = sum((a - mx) * (b - my) for a, b in zip(rx, ry))
    den = math.sqrt(sum((a - mx) ** 2 for a in rx) * sum((b - my) ** 2 for b in ry))
    return num / den if den else float("nan")

"""探针主流程（滚动前瞻版）。

现实约束决定了必须用滚动前瞻来评估，而不是只拿最新一期持仓回测：

- 基金季报（Q1/Q3）**只披露前十大重仓股**，中报（Q2）与年报（Q4）才披露全量持仓。
  实测 090010 的 2026Q1 只有 15 行、合计权重 16.9%，2026Q2 有 100 行、94.3%。
  因此一年里有半年只有「前十大 + 残缺覆盖」可用，这恰恰是生产环境的常态。
- 持仓是季末快照，披露日要晚 2~3 周。用披露日之前的数据回测等于偷看未来。

故本脚本对每只基金遍历全部可得持仓快照，每份快照只在
「披露日 ≤ 日期 < 下一份快照披露日」区间内被使用，逐日比对实际净值。

用法（仓库根目录）：
    python probe/run_probe.py
    python probe/run_probe.py --refresh
    python probe/run_probe.py --only 005827,320007
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from lib import estimate, sources
from lib.estimate import Holding, Series
from lib.sample import SAMPLE, THRESHOLD_EQUITY

OUT_DIR = Path(__file__).resolve().parent / "out"
SCHEMES = ["index_only", "top10_index", "full_index", "full_cash", "zero", "ols_roll"]


# ------------------------------------------------------------------ 工具

def classify(name: str, ftype: str, covered: float | None) -> str:
    if "黄金" in name:
        return "黄金"
    if "红利" in name:
        return "红利"
    if "债券" in ftype:
        return "债券型"
    if "混合" in ftype:
        return "混合型"
    if "股票" in ftype or "指数" in ftype:
        return "股票型"
    return ftype or "未分类"


def is_feeder(covered: float | None) -> bool:
    """联接基金：持仓行是「持有目标 ETF」，权重极小，只能用指数或 ETF 代理。"""
    return covered is not None and covered < 5.0


def round_row(row: dict) -> dict:
    return {k: (round(v, 4) if isinstance(v, float) else v) for k, v in row.items()}


def write_csv(path: Path, rows: list[dict], fields: list[str] | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        path.write_text("", encoding="utf-8")
        return
    if fields is None:
        fields = []
        for row in rows:
            for key in row:
                if key not in fields:
                    fields.append(key)
    with path.open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


# ------------------------------------------------------------------ 数据装载

def load_snapshots(code: str, today: dt.date):
    """全部可得持仓快照，带披露日，按截止日升序。"""
    blocks: dict[str, dict] = {}
    for year in (today.year, today.year - 1):
        for month in (12, today.month):
            for block in sources.stock_holdings(code, year, month):
                blocks[block["quarter"]] = block
    if not blocks:
        return []

    published = sources.report_dates(code)
    snaps = []
    for quarter, block in blocks.items():
        rows = [Holding(r["code"], r["name"], r["weight"], r["secid"]) for r in block["rows"]]
        if not rows:
            continue
        day = published.get(quarter)
        snaps.append(estimate.Snapshot(
            quarter=quarter,
            cutoff=dt.date.fromisoformat(block["cutoff"]),
            disclosed=dt.date.fromisoformat(day) if day else None,
            holdings=rows,
        ))
    snaps.sort(key=lambda s: s.cutoff)
    return snaps


def load_series(secids: set[str]) -> dict[str, Series]:
    legs: dict[str, Series] = {}
    for secid in sorted(secids):
        market, _, code = secid.partition(".")
        try:
            legs[secid] = estimate.pct_change(
                sources.daily_close(sources.tx_symbol(code, market), bars=400))
        except Exception as exc:  # noqa: BLE001
            print(f"    [warn] {secid} 行情失败: {exc}")
    return legs


def index_returns(secid: str) -> Series:
    market, _, code = secid.partition(".")
    return estimate.pct_change(
        sources.daily_close(sources.tx_symbol(code, market), bars=400))


# ------------------------------------------------------------------ 单只基金

WINDOW_DAYS = 92  # 回看自然日数，对应 ISSUE-001 的「最近 3 个月」；可被 --days 覆盖


def run_proxy_fund(item: dict, universe: dict, today: dt.date) -> tuple[list[dict], list[dict], dict]:
    """黄金 / 债券这类无股票持仓的基金：用可直接对标的标的做代理预估。

    黄金联接基金实际持有的就是目标黄金 ETF，债券基金持有的是债券组合。
    它们的「重仓股 + 指数」路径不成立（没有股票），必须走这条分支，
    否则会把它们错误地判为「无法预估」——这正是 ISSUE-001 要澄清的边界。
    """
    code = item["code"]
    meta = universe.get(code, {})
    name = meta.get("name", item["name"])
    ftype = meta.get("type", "")
    kind = classify(name, ftype, None)
    print(f"\n=== {code} {name} ({ftype}) [代理路径] ===")

    beg = today - dt.timedelta(days=WINDOW_DAYS)
    nav = sources.nav_series(code)
    actual = Series({dt.date.fromisoformat(r["date"]): r["ret"] for r in nav
                     if beg.isoformat() <= r["date"] <= today.isoformat()})
    days_all = sorted(actual.by_date)

    proxy_secid = item["proxy"][0]
    pm, _, pc = proxy_secid.partition(".")
    proxy_series = estimate.pct_change(
        sources.daily_close(sources.tx_symbol(pc, pm), bars=400))
    index_series = index_returns(item["index"][0])
    ols = estimate.ols_predict(actual, proxy_series, days_all)

    errors: dict[str, list[float]] = {s: [] for s in ["proxy", "index_only", "ols_roll", "zero"]}
    detail: list[dict] = []
    for day in days_all:
        value = actual.get(day)
        if value is None:
            continue
        row = {"code": code, "name": name, "kind": kind, "date": day.isoformat(),
               "actual": round(value, 4), "quarter": "proxy", "covered": None}
        for scheme, guess in (("proxy", proxy_series.get(day)),
                              ("index_only", index_series.get(day)),
                              ("ols_roll", ols.get(day)),
                              ("zero", 0.0)):
            if guess is None:
                continue
            errors[scheme].append(guess - value)
            row[scheme] = round(guess, 4)
            row[f"err_{scheme}"] = round(guess - value, 4)
        detail.append(row)

    summary = []
    for scheme, errs in errors.items():
        stats = estimate.summarize(errs, THRESHOLD_EQUITY)
        if not stats.n:
            continue
        summary.append(round_row({
            "code": code, "name": name, "kind": kind, "scheme": scheme,
            "benchmark": item["proxy"][1], "covered": None, "feeder": False,
            **stats.row(),
        }))
        print(f"  {scheme:12s} n={stats.n:3d} MAE={stats.mae:6.3f} 中位={stats.median:6.3f} "
              f"P90={stats.p90:6.3f} 最大={stats.max_abs:7.3f} 命中={stats.hit:5.0%}")
    return summary, detail, {"code": code, "name": name, "kind": kind, "feeder": False}


def run_fund(item: dict, universe: dict, today: dt.date):
    code = item["code"]
    if item.get("proxy"):
        return run_proxy_fund(item, universe, today)

    meta = universe.get(code, {})
    name = meta.get("name", item["name"])
    ftype = meta.get("type", "")

    beg = today - dt.timedelta(days=WINDOW_DAYS)
    print(f"\n=== {code} {name} ({ftype}) ===")
    nav = sources.nav_series(code)
    actual = Series({dt.date.fromisoformat(r["date"]): r["ret"] for r in nav
                     if beg.isoformat() <= r["date"] <= today.isoformat()})
    days_all = sorted(actual.by_date)

    index_series = index_returns(item["index"][0])
    proxy_secid = item.get("proxy", item["index"])[0]
    proxy_series = index_series if proxy_secid == item["index"][0] else index_returns(proxy_secid)

    snaps = [s for s in load_snapshots(code, today) if s.disclosed]
    if not snaps:
        print("  [skip] 无已披露持仓")
        return [], [], {}

    covered_latest = snaps[-1].covered
    kind = classify(name, ftype, covered_latest)
    feeder = is_feeder(covered_latest)
    print(f"  类型 {kind}｜联接={feeder}｜快照 "
          f"{[(s.quarter, round(s.covered, 1)) for s in snaps]}")

    try:
        positions = sources.stock_position(code)
    except Exception:  # noqa: BLE001
        positions = {}

    windows = []
    for i, snap in enumerate(snaps):
        start = snap.disclosed
        end = snaps[i + 1].disclosed if i + 1 < len(snaps) else None
        windows.append((snap, start, end))

    legs = load_series({h.secid for s in snaps for h in s.holdings})
    hk = {h.secid for s in snaps for h in s.holdings if h.secid.startswith("116.")}
    fx = None
    if hk:
        try:
            fx = estimate.pct_change(sources.hkd_cny_series())
        except Exception as exc:  # noqa: BLE001
            print(f"    [warn] 汇率失败: {exc}")

    ols = estimate.ols_predict(actual, proxy_series, days_all)

    errors: dict[str, list[float]] = {s: [] for s in SCHEMES + ["proxy"]}
    detail: list[dict] = []

    for snap, start, end in windows:
        pos = positions.get(snap.cutoff)
        top10 = estimate.Snapshot(snap.quarter, snap.cutoff, snap.disclosed,
                                  snap.holdings[:10])
        for day in days_all:
            if day < start or (end and day >= end):
                continue
            value = actual.get(day)
            if value is None:
                continue
            index_value = index_series.get(day)
            row = {
                "code": code, "name": name, "kind": kind, "date": day.isoformat(),
                "actual": round(value, 4), "quarter": snap.quarter,
                "cutoff": snap.cutoff.isoformat(), "disclosed": start.isoformat(),
                "days_since_cutoff": (day - snap.cutoff).days,
                "days_since_disclosed": (day - start).days,
                "covered": round(snap.covered, 2),
            }
            for scheme in SCHEMES:
                if scheme == "zero":
                    guess = 0.0
                elif scheme == "index_only":
                    guess = index_value
                elif scheme == "ols_roll":
                    guess = ols.get(day)
                else:
                    holdings = top10.holdings if scheme == "top10_index" else snap.holdings
                    guess = estimate.predict(scheme, holdings, legs, fx, hk, day,
                                             index_value, pos)
                if guess is None:
                    continue
                errors[scheme].append(guess - value)
                row[scheme] = round(guess, 4)
                row[f"err_{scheme}"] = round(guess - value, 4)
            if feeder or item.get("proxy"):
                guess = proxy_series.get(day)
                if guess is not None:
                    errors["proxy"].append(guess - value)
                    row["proxy"] = round(guess, 4)
                    row["err_proxy"] = round(guess - value, 4)
            detail.append(row)

    by_quarter: dict[str, list[dict]] = {}
    for row in detail:
        by_quarter.setdefault(row["quarter"], []).append(row)
    for rows in by_quarter.values():
        rows.sort(key=lambda r: r["date"])
        for i, row in enumerate(rows):
            row["trading_days_since_disclosed"] = i

    summary = []
    for scheme in SCHEMES + ["proxy"]:
        stats = estimate.summarize(errors[scheme], THRESHOLD_EQUITY)
        if not stats.n:
            continue
        benchmark = (item.get("proxy", item["index"])[1] if scheme == "proxy"
                     else item["index"][1])
        summary.append(round_row({
            "code": code, "name": name, "kind": kind, "scheme": scheme,
            "benchmark": benchmark, "covered": round(covered_latest, 2),
            "feeder": feeder, **stats.row(),
        }))
        print(f"  {scheme:12s} n={stats.n:3d} MAE={stats.mae:6.3f} 中位={stats.median:6.3f} "
              f"P90={stats.p90:6.3f} 最大={stats.max_abs:7.3f} 命中={stats.hit:5.0%}")

    return summary, detail, {"code": code, "name": name, "kind": kind, "feeder": feeder}


# ------------------------------------------------------------------ 汇总分析

def type_aggregate(summary: list[dict]) -> list[dict]:
    """按类型 × 方案汇总。先算每只基金，再对基金取中位数，避免长序列主导。"""
    groups: dict[tuple[str, str], list[dict]] = {}
    for row in summary:
        groups.setdefault((row["kind"], row["scheme"]), []).append(row)

    out = []
    for (kind, scheme), rows in sorted(groups.items()):
        out.append(round_row({
            "kind": kind, "scheme": scheme, "funds": len(rows),
            "MAE中位": statistics.median(r["MAE"] for r in rows),
            "P90中位": statistics.median(r["P90"] for r in rows),
            "最大": max(r["最大"] for r in rows),
            "命中率中位": statistics.median(r["命中率"] for r in rows),
            "MAE最大": max(r["MAE"] for r in rows),
        }))
    return out


def decay_analysis(detail: list[dict]):
    """误差是否随「披露后交易日数」放大。用 top10_index（现实可用口径）。"""
    buckets = [(0, 5), (5, 10), (10, 20), (20, 40), (40, 999)]
    out = []
    for kind in sorted({r["kind"] for r in detail if "err_top10_index" in r}):
        rows = [r for r in detail if r["kind"] == kind and "err_top10_index" in r]
        for lo, hi in buckets:
            subset = [abs(r["err_top10_index"]) for r in rows
                      if r.get("trading_days_since_disclosed") is not None
                      and lo <= r["trading_days_since_disclosed"] < hi]
            if len(subset) < 5:
                continue
            out.append(round_row({
                "kind": kind, "bucket": f"{lo}-{hi if hi < 999 else '+'}",
                "n": len(subset), "MAE": sum(subset) / len(subset),
                "P90": estimate.quantile(subset, 0.9),
            }))

    per_fund = []
    for code in sorted({r["code"] for r in detail}):
        rows = [r for r in detail if r["code"] == code and "err_top10_index" in r
                and r.get("trading_days_since_disclosed") is not None]
        if len(rows) < 15:
            continue
        xs = [float(r["trading_days_since_disclosed"]) for r in rows]
        ys = [abs(r["err_top10_index"]) for r in rows]
        per_fund.append({"code": code, "name": rows[0]["name"], "kind": rows[0]["kind"],
                         "n": len(rows), "spearman": round(estimate.spearman(xs, ys), 4)})
    return out, per_fund


def pooled_summary(detail: list[dict]) -> list[dict]:
    """按类型 × 方案的整体误差分布（所有基金所有交易日合并）。"""
    out = []
    for kind in sorted({r["kind"] for r in detail}):
        rows = [r for r in detail if r["kind"] == kind]
        for scheme in SCHEMES + ["proxy"]:
            key = f"err_{scheme}"
            vals = [r[key] for r in rows if key in r]
            if len(vals) < 10:
                continue
            stats = estimate.summarize(vals, THRESHOLD_EQUITY)
            out.append(round_row({"kind": kind, "scheme": scheme, **stats.row()}))
    return out


def bond_threshold(detail: list[dict]) -> dict:
    """债券型建议阈值：判断「误差相对日波动是否还有信息量」。"""
    rows = [r for r in detail if r["kind"] == "债券型"]
    rets = [abs(r["actual"]) for r in rows]
    stats = {}
    for scheme in ("index_only", "proxy", "ols_roll", "zero"):
        vals = [r[f"err_{scheme}"] for r in rows if f"err_{scheme}" in r]
        if len(vals) >= 10:
            s = estimate.summarize(vals, 0.3)
            # 用 ASCII 键，避免派生 JSON 在 Windows 工具链里被转码成乱码
            stats[scheme] = {
                "n": s.n, "mae": round(s.mae, 4), "median": round(s.median, 4),
                "p90": round(s.p90, 4), "p95": round(s.p95, 4),
                "max_abs": round(s.max_abs, 4), "bias": round(s.bias, 4),
                "rmse": round(s.rmse, 4), "hit_at_0.3": round(s.hit, 4),
            }
    return {
        "n_days": len(rows),
        "median_abs_daily_return_pct": round(statistics.median(rets), 4) if rets else None,
        "p90_abs_daily_return_pct": round(estimate.quantile(rets, 0.9), 4) if rets else None,
        "scheme_stats": stats,
    }


def coverage_summary(detail: list[dict]) -> list[dict]:
    """覆盖度与误差的关系：季报（仅前十大）vs 中报（全量）。"""
    out = []
    for kind in sorted({r["kind"] for r in detail}):
        for quarter in sorted({r["quarter"] for r in detail if r["kind"] == kind}):
            rows = [r for r in detail if r["kind"] == kind and r["quarter"] == quarter
                    and "err_full_index" in r]
            if len(rows) < 5:
                continue
            errs = [abs(r["err_full_index"]) for r in rows]
            out.append(round_row({
                "kind": kind, "quarter": quarter, "n": len(rows),
                "covered均值": statistics.mean(r["covered"] for r in rows),
                "MAE_full_index": sum(errs) / len(errs),
                "P90_full_index": estimate.quantile(errs, 0.9),
            }))
    return out


# ------------------------------------------------------------------ 入口

def main() -> int:
    global WINDOW_DAYS

    parser = argparse.ArgumentParser()
    parser.add_argument("--refresh", action="store_true")
    parser.add_argument("--only", default="")
    parser.add_argument("--days", type=int, default=WINDOW_DAYS,
                        help="回看自然日数；默认 92（对应 ISSUE-001 的最近 3 个月）")
    args = parser.parse_args()

    # 让 run_fund / run_proxy_fund 拿到本次窗口
    WINDOW_DAYS = args.days

    if args.refresh and sources.CACHE_DIR.exists():
        import shutil
        shutil.rmtree(sources.CACHE_DIR)

    today = dt.date.today()
    universe = sources.fund_universe()
    wanted = {c.strip() for c in args.only.split(",") if c.strip()}

    summary: list[dict] = []
    detail_all: list[dict] = []
    meta_all: list[dict] = []

    for item in SAMPLE:
        if wanted and item["code"] not in wanted:
            continue
        try:
            fund_summary, detail, meta = run_fund(item, universe, today)
        except Exception as exc:  # noqa: BLE001
            print(f"  [error] {item['code']}: {exc!r}")
            continue
        summary.extend(fund_summary)
        detail_all.extend(detail)
        if meta:
            meta_all.append(meta)
            write_csv(OUT_DIR / "detail" / f"{item['code']}.csv", detail)

    fields = ["code", "name", "kind", "scheme", "benchmark", "covered", "feeder",
              "n", "MAE", "中位数", "P90", "P95", "最大", "偏差", "RMSE", "命中率"]
    write_csv(OUT_DIR / "fund_summary.csv", summary, fields)
    write_csv(OUT_DIR / "detail_all.csv", detail_all)
    write_csv(OUT_DIR / "by_type.csv", type_aggregate(summary))
    write_csv(OUT_DIR / "by_type_pooled.csv", pooled_summary(detail_all))
    write_csv(OUT_DIR / "coverage.csv", coverage_summary(detail_all))

    buckets, spearman_rows = decay_analysis(detail_all)
    write_csv(OUT_DIR / "decay_buckets.csv", buckets)
    write_csv(OUT_DIR / "decay_spearman.csv", spearman_rows)

    bond = bond_threshold(detail_all)
    (OUT_DIR / "bond_threshold.json").write_text(
        json.dumps(bond, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
    (OUT_DIR / "sample_meta.json").write_text(
        json.dumps(meta_all, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\n输出目录 {OUT_DIR}")
    print(f"  基金×方案 {len(summary)} 行，逐日明细 {len(detail_all)} 行")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

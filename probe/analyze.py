"""探针结果分析：把 detail_all.csv 加工成报告可直接引用的表格。

输出：
  - 每只基金在「现实可用方案」下的误差（用于验收标准第 1 条的数值表）
  - 覆盖度 vs 股票仓位（验证「残差该按现金还是按指数」）
  - 阈值判定：不同口径下有多少天落在 0.3 以内
  - 换手率与误差的关系（误差的真正来源）
"""

import csv
import datetime as dt
import json
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lib import estimate, sources

OUT = Path(__file__).resolve().parent / "out"


def read_csv(path):
    with path.open(encoding="utf-8-sig") as fh:
        return list(csv.DictReader(fh))


def to_float(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def best_scheme_by_kind():
    """现实可用方案：按类型选定主方案，给出每只基金的误差表。"""
    rows = read_csv(OUT / "fund_summary.csv")
    plan = {
        "股票型": ["index_only", "ols_roll", "top10_index"],
        "红利": ["full_cash", "index_only", "full_index"],
        "黄金": ["proxy", "ols_roll"],
        "债券型": ["ols_roll", "index_only", "proxy"],
        "混合型": ["top10_index", "full_cash", "full_index", "ols_roll"],
    }
    out = []
    for row in rows:
        kind, scheme = row["kind"], row["scheme"]
        if scheme not in plan.get(kind, []):
            continue
        out.append({
            "类型": kind,
            "基金": f"{row['code']} {row['name']}",
            "方案": scheme,
            "对标": row["benchmark"],
            "覆盖度%": row["covered"],
            "n": row["n"],
            "MAE": round(float(row["MAE"]), 3),
            "中位数": round(float(row["中位数"]), 3),
            "P90": round(float(row["P90"]), 3),
            "最大": round(float(row["最大"]), 3),
            "偏差": round(float(row["偏差"]), 3),
            "命中0.3": f"{float(row['命中率']) * 100:.0f}%",
            "_mae": float(row["MAE"]),
        })
    out.sort(key=lambda r: (r["类型"], r["基金"], r["_mae"]))
    for row in out:
        del row["_mae"]
    return out


def best_per_fund():
    """每只基金在全部方案里误差最小的方案，用于判断「最优能到多少」。"""
    rows = read_csv(OUT / "fund_summary.csv")
    by_fund = {}
    for row in rows:
        code = row["code"]
        mae = float(row["MAE"])
        if code not in by_fund or mae < by_fund[code]["mae"]:
            by_fund[code] = {"mae": mae, "row": row}
    out = []
    for code, item in sorted(by_fund.items()):
        row = item["row"]
        out.append({
            "类型": row["kind"], "基金": f"{code} {row['name']}",
            "最优方案": row["scheme"], "对标": row["benchmark"],
            "MAE": round(item["mae"], 3), "P90": round(float(row["P90"]), 3),
            "命中0.3": f"{float(row['命中率']) * 100:.0f}%",
        })
    return out


def coverage_vs_position():
    """覆盖度 vs 季末股票仓位：若两者接近，说明中报已披露全部股票持仓，
    则「1 − 覆盖度」这部分是债券/现金，按指数补残差会引入系统性误差。"""
    detail = read_csv(OUT / "detail_all.csv")
    out = []
    for code in sorted({r["code"] for r in detail}):
        rows = [r for r in detail if r["code"] == code and r.get("covered")]
        if not rows:
            continue
        covered = float(rows[0]["covered"])
        try:
            positions = sources.stock_position(code)
        except Exception:
            positions = {}
        cutoff = rows[0].get("cutoff")
        pos = None
        if cutoff:
            pos = positions.get(dt.date.fromisoformat(cutoff))
        out.append({
            "基金": f"{code} {rows[0]['name']}", "类型": rows[0]["kind"],
            "覆盖度%": round(covered, 2),
            "股票仓位%": round(pos, 2) if pos is not None else "—",
            "差": round(covered - pos, 2) if pos is not None else "—",
        })
    return out


def threshold_view():
    """两种验收口径：中位数 ≤ 0.3（典型日）与 P90 ≤ 0.3（绝大多数日）。"""
    pooled = read_csv(OUT / "by_type_pooled.csv")
    out = []
    for row in pooled:
        out.append({
            "类型": row["kind"], "方案": row["scheme"], "n": row["n"],
            "MAE": round(float(row["MAE"]), 3),
            "中位数": round(float(row["中位数"]), 3),
            "P90": round(float(row["P90"]), 3),
            "最大": round(float(row["最大"]), 3),
            "中位数≤0.3": "✅" if float(row["中位数"]) <= 0.3 else "❌",
            "P90≤0.3": "✅" if float(row["P90"]) <= 0.3 else "❌",
            "命中率": f"{float(row['命中率']) * 100:.0f}%",
        })
    return out


def turnover_vs_error():
    """连续两季前十大重仓股重合度 → 持仓滞后程度；与误差对照。"""
    summary = read_csv(OUT / "fund_summary.csv")
    rows = []
    for row in summary:
        if row["scheme"] not in ("top10_index", "full_cash", "index_only"):
            continue
        rows.append(row)
    codes = sorted({r["code"] for r in rows})
    out = []
    for code in codes:
        snaps = []
        for year in (2026, 2025):
            for month in (12, 10):
                try:
                    snaps.extend(sources.stock_holdings(code, year, month))
                except Exception:
                    pass
        uniq = {}
        for block in snaps:
            uniq[block["quarter"]] = block
        ordered = sorted(uniq.values(), key=lambda b: b["cutoff"])
        overlap = None
        if len(ordered) >= 2:
            prev = {r["code"] for r in ordered[-2]["rows"][:10]}
            cur = {r["code"] for r in ordered[-1]["rows"][:10]}
            overlap = len(prev & cur) / 10.0 if prev else None
        errs = {r["scheme"]: float(r["MAE"]) for r in rows if r["code"] == code}
        if not errs:
            continue
        out.append({
            "基金": f"{code} {errs and [r for r in rows if r['code']==code][0]['name']}",
            "类型": [r for r in rows if r["code"] == code][0]["kind"],
            "前后两季前十大重合": f"{overlap * 100:.0f}%" if overlap is not None else "—",
            "MAE_top10": round(errs.get("top10_index", float("nan")), 3),
            "MAE_full_cash": round(errs.get("full_cash", float("nan")), 3),
            "MAE_index_only": round(errs.get("index_only", float("nan")), 3),
        })
    return out


def main():
    outputs = {
        "table_best_scheme.csv": best_scheme_by_kind(),
        "table_best_per_fund.csv": best_per_fund(),
        "table_coverage_vs_position.csv": coverage_vs_position(),
        "table_threshold.csv": threshold_view(),
        "table_turnover.csv": turnover_vs_error(),
    }
    for name, rows in outputs.items():
        path = OUT / name
        if not rows:
            print(f"{name}: 空")
            continue
        with path.open("w", newline="", encoding="utf-8-sig") as fh:
            writer = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
            writer.writeheader()
            writer.writerows(rows)
        print(f"{name}: {len(rows)} 行")


if __name__ == "__main__":
    main()

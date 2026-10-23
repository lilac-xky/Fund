"""数据源访问层：天天基金（净值/持仓/公告）+ 腾讯行情（K 线）。

探针自带磁盘缓存，避免重复请求触发上游限流；所有请求统一带浏览器 User-Agent
与超时，失败按退避重试。此层只做取数与解析，不含任何业务判断。

实测要点（详见 docs/probe-001-report.md）：
- 东方财富 push2his 在连续请求约 20 次后开始返回 RemoteDisconnected，冷却数十秒
  才恢复，不适合探针批量取数，故改用腾讯 web.ifzq.gtimg.cn（实测 12 req/s 稳定）。
- 东财基金净值时间戳是「北京时间零点」，必须按 UTC+8 取日期，否则整体错一天。
"""

from __future__ import annotations

import datetime as dt
import hashlib
import json
import re
import sys
import time
from pathlib import Path
from typing import Any

import requests

for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except Exception:
        pass

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
HEADERS_FUND = {"User-Agent": UA, "Referer": "https://fundf10.eastmoney.com/"}
HEADERS_QUOTE = {"User-Agent": UA, "Referer": "https://gu.qq.com/"}

PROBE_DIR = Path(__file__).resolve().parent.parent
CACHE_DIR = PROBE_DIR / ".cache"
CST = dt.timezone(dt.timedelta(hours=8))

_SESSION = requests.Session()
_SESSION.headers.update({"User-Agent": UA})

_last_call = 0.0
_MIN_INTERVAL = 0.06


def _throttle() -> None:
    global _last_call
    gap = time.monotonic() - _last_call
    if gap < _MIN_INTERVAL:
        time.sleep(_MIN_INTERVAL - gap)
    _last_call = time.monotonic()


def http_get(url: str, headers: dict[str, str] | None = None, tries: int = 5,
             json_api: bool = False) -> requests.Response:
    last: Exception | None = None
    merged = dict(_SESSION.headers)
    if headers:
        merged.update(headers)
    for attempt in range(tries):
        _throttle()
        try:
            resp = _SESSION.get(url, headers=merged, timeout=25)
            if resp.status_code == 200:
                if not json_api:
                    return resp
                if resp.text.strip():
                    return resp
            last = RuntimeError(f"HTTP {resp.status_code}")
        except Exception as exc:  # noqa: BLE001
            last = exc
        time.sleep(0.7 * (attempt + 1))
    raise ConnectionError(f"多次重试仍失败: {url}") from last


def _cache_path(kind: str, key: str) -> Path:
    digest = hashlib.sha1(key.encode("utf-8")).hexdigest()[:16]
    safe = re.sub(r"[^0-9A-Za-z_.-]", "_", key)[:60]
    return CACHE_DIR / kind / f"{safe}.{digest}.json"


def _cached(kind: str, key: str, producer):
    path = _cache_path(kind, key)
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    value = producer()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")
    return value


def ts_to_date(ts_ms: int) -> dt.date:
    """东财时间戳为「北京时间零点」对应的毫秒值，必须按 UTC+8 取日期。"""
    return dt.datetime.fromtimestamp(ts_ms / 1000, CST).date()


# ------------------------------------------------------------------ 基金全集

def fund_universe() -> dict[str, dict[str, str]]:
    """基金代码 → {name, type}。type 粒度到「股票型 / 混合型-偏股 / 债券型-长债」。"""

    def produce():
        resp = http_get("https://fund.eastmoney.com/js/fundcode_search.js")
        resp.encoding = "utf-8"
        rows = json.loads(re.search(r"=\s*(\[.*\])", resp.text, re.S).group(1))
        return {row[0]: {"name": row[2], "type": row[3]} for row in rows}

    return _cached("universe", "fundcode_search", produce)


# ------------------------------------------------------------------ 净值

def nav_series(code: str) -> list[dict[str, Any]]:
    """历史净值序列，含东财给出的当日涨跌幅（百分数，已核对与官方 JZZZL 一致）。"""

    def produce():
        resp = http_get(f"https://fund.eastmoney.com/pingzhongdata/{code}.js")
        resp.encoding = "utf-8"
        match = re.search(r"var\s+Data_netWorthTrend\s*=\s*(\[.*?\]);\s*/", resp.text, re.S)
        if not match:
            raise ValueError(f"{code}: 找不到 Data_netWorthTrend")
        out = []
        for row in json.loads(match.group(1)):
            out.append({
                "date": ts_to_date(row["x"]).isoformat(),
                "nav": float(row["y"]),
                "ret": float(row.get("equityReturn") or 0.0),
            })
        return out

    return _cached("nav", code, produce)


def nav_growth_ratio(code: str, beg: dt.date, end: dt.date) -> dict[str, float]:
    """官方「净值增长率」，用于交叉校验 equityReturn（分红日两者会不一致）。"""

    def produce():
        out: dict[str, float] = {}
        page = 1
        while True:
            url = ("https://api.fund.eastmoney.com/f10/lsjz"
                   f"?fundCode={code}&pageIndex={page}&pageSize=50"
                   f"&startDate={beg.isoformat()}&endDate={end.isoformat()}")
            payload = http_get(url, headers=HEADERS_FUND).json()
            items = ((payload.get("Data") or {}).get("LSJZList")) or []
            if not items:
                break
            for item in items:
                if item.get("JZZZL") not in (None, ""):
                    out[item["FSRQ"]] = float(item["JZZZL"])
            if len(items) < 50:
                break
            page += 1
            if page > 6:
                break
        return out

    return _cached("lsjz", f"{code}:{beg}:{end}", produce)


_CN_DIGITS = {"〇": "0", "零": "0", "一": "1", "二": "2", "三": "3", "四": "4",
              "五": "5", "六": "6", "七": "7", "八": "8", "九": "9",
              "0": "0", "1": "1", "2": "2", "3": "3", "4": "4",
              "5": "5", "6": "6", "7": "7", "8": "8", "9": "9"}


def _normalize_year(text: str) -> str:
    """把「二0二六年」这类中文数字年份转成「2026年」。

    实测 100032 的定期报告标题写作「二0二六年第2季度报告」，纯阿拉伯数字
    正则匹配不到，会导致该基金所有季度都被判为「未披露」。
    """
    def convert(match):
        token = match.group(1)
        if token.isdigit():
            return token + "年"
        digits = "".join(_CN_DIGITS.get(ch, "") for ch in token)
        return (digits if len(digits) == 4 else token) + "年"

    return re.sub(r"([〇零一二三四五六七八九0-9]{4})年", convert, text)

def report_dates(code: str) -> dict[str, str]:
    """定期报告披露日：{'2026Q2': '2026-07-21', ...}。

    用于计算「重仓股披露后第 N 个交易日」，这是 ISSUE-001 验收标准第 4 条的输入。
    """

    def produce():
        url = ("https://api.fund.eastmoney.com/f10/JJGG"
               f"?callback=x&fundcode={code}&pageIndex=1&pageSize=40&type=3")
        text = http_get(url, headers=HEADERS_FUND).text
        items = re.findall(r'"TITLE":"(.*?)".*?"PUBLISHDATEDesc":"(\d{4}-\d{2}-\d{2})"', text)
        out: dict[str, str] = {}
        for title, published in items:
            normalized = _normalize_year(title)
            quarter = re.search(r"(\d{4})年第([1-4])季度报告", normalized)
            if quarter:
                out[f"{quarter.group(1)}Q{quarter.group(2)}"] = published
                continue
            year = re.search(r"(\d{4})年", normalized)
            if not year:
                continue
            if "中期报告" in normalized:
                out[f"{year.group(1)}H1"] = published
            elif "年度报告" in normalized:
                out[f"{year.group(1)}FY"] = published
        return out

    return _cached("report_dates", code, produce)


# ------------------------------------------------------------------ 持仓明细

def _strip(html: str) -> str:
    return re.sub(r"<[^>]+>", "", html).strip()


def _table_column(chunk: str, keyword: str) -> int:
    """按表头文字定位列。不同季度的表格列数不同，硬编码索引会错位。"""
    head = re.search(r"<thead>.*?</thead>", chunk, re.S)
    if not head:
        return -1
    headers = [_strip(cell) for cell in re.findall(r"<th[^>]*>(.*?)</th>", head.group(0), re.S)]
    for idx, label in enumerate(headers):
        if keyword in label:
            return idx
    return -1


def _cutoff_maps(html: str) -> dict[tuple[str, str], str]:
    """季度标签 → 截止日。块头里的截止日用于与重仓股权重对齐。"""
    out = {}
    for chunk in html.split("<div class='boxitem w790'>")[1:]:
        label = re.search(r"(\d{4})年(\d)季度", chunk)
        cutoff = re.search(r"截止至：<font class='px12'>([\d\-]+)</font>", chunk)
        if label and cutoff:
            out[(label.group(1), label.group(2))] = cutoff.group(1)
    return out


def stock_holdings(code: str, year: int, month: int) -> list[dict[str, Any]]:
    """股票持仓明细（含占比），按截止日升序。

    注意：季报（Q1/Q3）只披露前十大重仓股，中报（Q2）与年报（Q4）才披露全量持仓。
    实测 090010 的 2026Q1 只有 15 行、合计权重 16.9%，2026Q2 有 100 行、94.3%。
    """

    def produce():
        url = ("https://fundf10.eastmoney.com/FundArchivesDatas.aspx"
               f"?type=jjcc&code={code}&topline=100&year={year}&month={month}")
        resp = http_get(url, headers=HEADERS_FUND)
        resp.encoding = "utf-8"
        html = resp.text
        blocks = []
        for chunk in html.split("<div class='boxitem w790'>")[1:]:
            label = re.search(r"(\d{4})年(\d)季度", chunk)
            cutoff = re.search(r"截止至：<font class='px12'>([\d\-]+)</font>", chunk)
            if not (label and cutoff):
                continue
            wcol = _table_column(chunk, "占净值")
            rows = []
            for row_html in re.findall(r"<tr>.*?</tr>", chunk, re.S):
                cells = [_strip(c) for c in re.findall(r"<td[^>]*>(.*?)</td>", row_html, re.S)]
                secid = re.search(r"unify/r/([\d]+\.\w+)", row_html)
                if not secid or wcol < 0 or len(cells) <= wcol:
                    continue
                text = cells[wcol]
                if not text.endswith("%"):
                    continue
                rows.append({
                    "code": cells[1],
                    "name": cells[2],
                    "weight": float(text.rstrip("%")),
                    "secid": secid.group(1),
                })
            if rows:
                blocks.append({
                    "quarter": f"{label.group(1)}Q{label.group(2)}",
                    "cutoff": cutoff.group(1),
                    "rows": rows,
                })
        blocks.sort(key=lambda b: b["cutoff"])
        return blocks

    return _cached("holdings", f"{code}:{year}:{month}", produce)


def bond_holdings(code: str, year: int, month: int) -> list[dict[str, Any]]:
    """债券持仓明细（占净值比例）。债券基金无股票持仓时用。"""

    def produce():
        url = ("https://fundf10.eastmoney.com/FundArchivesDatas.aspx"
               f"?type=zqcc&code={code}&topline=100&year={year}&month={month}")
        resp = http_get(url, headers=HEADERS_FUND)
        resp.encoding = "utf-8"
        blocks = []
        for chunk in resp.text.split("<div class='boxitem w790'>")[1:]:
            label = re.search(r"(\d{4})年(\d)季度", chunk)
            cutoff = re.search(r"截止至：<font class='px12'>([\d\-]+)</font>", chunk)
            if not (label and cutoff):
                continue
            wcol = _table_column(chunk, "占净值")
            rows = []
            for row_html in re.findall(r"<tr>.*?</tr>", chunk, re.S):
                cells = [_strip(c) for c in re.findall(r"<td[^>]*>(.*?)</td>", row_html, re.S)]
                if wcol < 0 or len(cells) <= wcol or not cells[wcol].endswith("%"):
                    continue
                rows.append({
                    "code": cells[1],
                    "name": cells[2],
                    "weight": float(cells[wcol].rstrip("%")),
                })
            if rows:
                blocks.append({
                    "quarter": f"{label.group(1)}Q{label.group(2)}",
                    "cutoff": cutoff.group(1),
                    "rows": rows,
                })
        blocks.sort(key=lambda b: b["cutoff"])
        return blocks

    return _cached("bond_holdings", f"{code}:{year}:{month}", produce)


# ------------------------------------------------------------------ 行情 K 线

def tx_symbol(code: str, market: str) -> str:
    """把东财 secid 的 market 前缀换成腾讯/新浪符号。"""
    mapping = {"0": "sz", "1": "sh", "116": "hk", "100": "hk", "105": "us", "106": "us"}
    prefix = mapping.get(market, "sh")
    return f"{prefix}{code}"


def _tencent_kline(symbol: str, bars: int) -> list[list]:
    url = ("https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"
           f"?param={symbol},day,,,{bars},qfq")
    text = http_get(url, headers=HEADERS_QUOTE, json_api=True).text
    payload = json.loads(text)  # 限流时返回 HTML 验证页，交给上层回退
    block = (payload.get("data") or {}).get(symbol) or {}
    rows = block.get("qfqday") or block.get("day") or []
    return [[row[0], float(row[2])] for row in rows if len(row) >= 3]


def _sina_kline(symbol: str, bars: int) -> list[list]:
    url = ("https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/"
           f"CN_MarketData.getKLineData?symbol={symbol}&scale=240&ma=no&datalen={bars}")
    text = http_get(url, headers={"User-Agent": UA,
                                  "Referer": "https://finance.sina.com.cn/"}).text
    payload = json.loads(text)
    return [[row["day"], float(row["close"])] for row in payload]


def _hk_kline(code: str, bars: int) -> list[list]:
    """港股日线（港币计价）。

    港股必须走腾讯的 hkfqkline 专用接口——通用的 fqkline 对 hk 前缀会
    被 WAF 拦成 501；新浪的 HK_StockService 已下线（返回 Service not found）。
    """
    url = ("https://web.ifzq.gtimg.cn/appstock/app/hkfqkline/get"
           f"?param=hk{code},day,,,{bars},qfq")
    text = http_get(url, headers=HEADERS_QUOTE, json_api=True).text
    payload = json.loads(text)
    block = (payload.get("data") or {}).get(f"hk{code}") or {}
    rows = block.get("qfqday") or block.get("day") or []
    return [[row[0], float(row[2])] for row in rows if len(row) >= 3]


def daily_close(symbol: str, bars: int = 250) -> list[tuple[dt.date, float]]:
    """日线收盘价，多源自动回退。

    实测（2026-10）：东方财富 push2his 连续约 20 次后长时间拒绝连接；腾讯
    fqkline 高频访问会返回 HTML 验证页；新浪最稳定但对港股不覆盖。
    故按 腾讯前复权 → 新浪 → 腾讯港股专用 顺序回退，任一成功即返回并写缓存。

    复权口径：腾讯取 qfq（前复权）；新浪返回不复权收盘价，但相邻两日的
    比例关系一致，对「日涨跌幅」计算无影响。
    """

    def produce():
        errors = []
        providers = (
            ("tencent", _tencent_kline),
            ("sina", _sina_kline),
            ("tencent-hk", _hk_fetch),
        )
        for name, fetch in providers:
            try:
                rows = fetch(symbol, bars)
                if len(rows) >= 2:
                    return {"source": name, "rows": rows}
                errors.append(f"{name}: 空数据")
            except Exception as exc:  # noqa: BLE001
                errors.append(f"{name}: {exc!r}")
            time.sleep(0.8)
        raise ConnectionError(f"{symbol} 全部行情源失败: {'; '.join(errors)}")

    payload = _cached("kline", f"{symbol}:{bars}", produce)
    return [(dt.date.fromisoformat(a), b) for a, b in payload["rows"]]


def _hk_fetch(symbol: str, bars: int) -> list[list]:
    return _hk_kline(symbol[2:], bars)


def kline_source(symbol: str, bars: int = 250) -> str:
    """调试用：返回该标的实际使用的行情源。"""
    return _cached("kline", f"{symbol}:{bars}", lambda: {"source": "?", "rows": []})["source"]


def stock_position(code: str) -> dict[dt.date, float]:
    """季末股票占净比，来自 pingzhongdata 的 Data_assetAllocation。

    注意：Data_fundSharesPositions 是「股票仓位时序」而非重仓股权重，
    重仓股权重必须走 fundf10 的持仓明细接口。
    """

    def produce():
        resp = http_get(f"https://fund.eastmoney.com/pingzhongdata/{code}.js")
        resp.encoding = "utf-8"
        match = re.search(r"var\s+Data_assetAllocation\s*=\s*(\{.*?\});\s*(?:/\*|var )",
                          resp.text, re.S)
        if not match:
            return {"categories": [], "stock": []}
        payload = json.loads(match.group(1))
        stock = next((s for s in payload["series"] if "股票" in s["name"]), None)
        return {"categories": payload["categories"],
                "stock": stock["data"] if stock else []}

    raw = _cached("asset_allocation", code, produce)
    return {dt.date.fromisoformat(d): float(v)
            for d, v in zip(raw.get("categories", []), raw.get("stock", []))}


def hkd_cny_series() -> list[tuple[dt.date, float]]:
    """港币兑人民币日线收盘。

    东财 push2his 的 133.HKDCNH 在批量取数时被限流（实测冷却数分钟仍拒绝），
    腾讯只给实时价不给历史，故改用新浪外汇日线接口——它一次返回全历史，
    且不参与限流。港股持仓折算人民币净值时必须计入此项。
    """

    def produce():
        url = ("https://vip.stock.finance.sina.com.cn/forex/api/jsonp.php/"
               "var%20_fx=/NewForexService.getDayKLine?symbol=fx_shkdcny")
        text = http_get(url, headers={"Referer": "https://finance.sina.com.cn/"}).text
        body = text.split("=(", 1)[1].rsplit(")", 1)[0].strip().strip('"')
        out = []
        for chunk in body.split("|"):
            parts = chunk.split(",")
            if len(parts) >= 3 and parts[0]:
                # 字段顺序：日期, 开, 收, 高, 低
                out.append([parts[0], float(parts[2])])
        return out

    raw = _cached("fx", "HKDCNY:sina", produce)
    return [(dt.date.fromisoformat(a), b) for a, b in raw]

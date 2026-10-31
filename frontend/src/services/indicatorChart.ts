/**
 * 指标图表的数据装配，把日线与指标结果拼成 ECharts 的 option；纯函数无依赖，便于核对参数口径
 */

/** 一根日线，与生成的 MarketBarVO 同构 */
export interface Bar {
  /** 交易日 */
  date?: string
  /** 收盘价 */
  close?: number
  /** 开盘价 */
  open?: number
  /** 最高价 */
  high?: number
  /** 最低价 */
  low?: number
  /** 成交量 */
  volume?: number
}

/** 本次生效的指标参数，图表标题必须与计算层一致 */
export interface ChartParams {
  rsiPeriod: number
  kdjPeriod: number
  kdjKSmooth: number
  kdjDSmooth: number
  macdFast: number
  macdSlow: number
  macdSignal: number
}

/**
 * 参数摘要文字，与后端 IndicatorService.paramSummary 同口径
 *
 * @param params 指标参数
 * @returns 摘要
 */
export function paramSummary(params: ChartParams): string {
  return (
    `RSI(${params.rsiPeriod}) · ` +
    `KDJ(${params.kdjPeriod},${params.kdjKSmooth},${params.kdjDSmooth}) · ` +
    `MACD(${params.macdFast},${params.macdSlow},${params.macdSignal})`
  )
}

/**
 * 按 Wilder 平滑算 RSI 序列，样本不足处返回 null
 *
 * @param closes 收盘价序列
 * @param period 周期
 * @returns 与输入等长的序列
 */
export function rsiSeries(closes: number[], period: number): Array<number | null> {
  const out: Array<number | null> = new Array(closes.length).fill(null)
  if (period <= 0 || closes.length <= period) {
    return out
  }
  let gainSum = 0
  let lossSum = 0
  for (let index = 1; index <= period; index++) {
    const diff = (closes[index] ?? 0) - (closes[index - 1] ?? 0)
    if (diff >= 0) {
      gainSum += diff
    } else {
      lossSum -= diff
    }
  }
  let avgGain = gainSum / period
  let avgLoss = lossSum / period
  out[period] = toRsi(avgGain, avgLoss)
  for (let index = period + 1; index < closes.length; index++) {
    const diff = (closes[index] ?? 0) - (closes[index - 1] ?? 0)
    const up = diff > 0 ? diff : 0
    const down = diff < 0 ? -diff : 0
    avgGain = (avgGain * (period - 1) + up) / period
    avgLoss = (avgLoss * (period - 1) + down) / period
    out[index] = toRsi(avgGain, avgLoss)
  }
  return out
}

/**
 * 由平均涨幅与平均跌幅换算 RSI，全段无下跌按 100 处理
 *
 * @param avgGain 平均涨幅
 * @param avgLoss 平均跌幅
 * @returns RSI 值
 */
function toRsi(avgGain: number, avgLoss: number): number {
  if (avgLoss === 0) {
    return 100
  }
  return 100 - 100 / (1 + avgGain / avgLoss)
}

/** EMA 序列，首值作种子，与后端 IndicatorCalculator 同口径 */
function emaSeries(values: number[], period: number): number[] {
  const out: number[] = []
  const alpha = 2 / (period + 1)
  values.forEach((value, index) => {
    const previous = out[index - 1]
    out.push(previous === undefined ? value : previous + alpha * (value - previous))
  })
  return out
}

/**
 * MACD 三序列：DIF、DEA 与柱，柱按 (DIF−DEA)×2
 *
 * @param closes 收盘价序列
 * @param fast   快线周期
 * @param slow   慢线周期
 * @param signal 信号线周期
 * @returns 三序列
 */
export function macdSeries(
  closes: number[],
  fast: number,
  slow: number,
  signal: number,
): { dif: number[]; dea: number[]; bar: number[] } {
  const fastLine = emaSeries(closes, fast)
  const slowLine = emaSeries(closes, slow)
  const dif = fastLine.map((value, index) => value - (slowLine[index] ?? value))
  const dea = emaSeries(dif, signal)
  const bar = dif.map((value, index) => (value - (dea[index] ?? 0)) * 2)
  return { dif, dea, bar }
}

/**
 * KDJ 三序列：K、D 以 50 起递推，J 取 3K−2D；缺高低价的根跳过
 *
 * @param bars   日线序列
 * @param period RSV 周期
 * @param kSmooth K 平滑系数
 * @param dSmooth D 平滑系数
 * @returns 三序列
 */
export function kdjSeries(
  bars: Bar[],
  period: number,
  kSmooth: number,
  dSmooth: number,
): { k: Array<number | null>; d: Array<number | null>; j: Array<number | null> } {
  const count = bars.length
  const kOut: Array<number | null> = new Array(count).fill(null)
  const dOut: Array<number | null> = new Array(count).fill(null)
  const jOut: Array<number | null> = new Array(count).fill(null)
  let k = 50
  let d = 50
  for (let index = period - 1; index < count; index++) {
    let highest = -Infinity
    let lowest = Infinity
    let usable = true
    for (let cursor = index - period + 1; cursor <= index; cursor++) {
      const high = bars[cursor]?.high
      const low = bars[cursor]?.low
      if (high === undefined || low === undefined) {
        usable = false
        break
      }
      highest = Math.max(highest, high)
      lowest = Math.min(lowest, low)
    }
    const close = bars[index]?.close
    if (!usable || close === undefined) {
      continue
    }
    const range = highest - lowest
    // 区间为 0 时 RSV 取中位，避免除零把指标打到 100
    const rsv = range === 0 ? 50 : ((close - lowest) * 100) / range
    k = ((kSmooth - 1) * k + rsv) / kSmooth
    d = ((dSmooth - 1) * d + k) / dSmooth
    kOut[index] = k
    dOut[index] = d
    jOut[index] = 3 * k - 2 * d
  }
  return { k: kOut, d: dOut, j: jOut }
}

/**
 * 拼主图与三个指标副图的 option，参数与计算层一致
 *
 * @param bars   序列，净值口径时只有 close
 * @param params 指标参数
 * @param basis  输入口径，净值口径下主图用折线而不是 K 线
 * @returns ECharts option
 */
export function buildOption(
  bars: Bar[],
  params: ChartParams,
  basis: 'DAILY_BAR' | 'NET_WORTH' = 'DAILY_BAR',
): Record<string, unknown> {
  const netWorth = basis === 'NET_WORTH'
  const dates = bars.map((bar) => bar.date ?? '')
  const closes = bars.map((bar) => bar.close ?? 0)
  // ECharts 的 K 线按 [开, 收, 低, 高] 顺序，缺开高低时用收盘价兜底，避免画出空洞
  const kline = bars.map((bar) => {
    const close = bar.close ?? 0
    return [bar.open ?? close, close, bar.low ?? close, bar.high ?? close]
  })
  const volumes = bars.map((bar, index) => ({
    value: bar.volume ?? 0,
    itemStyle: { color: kline[index]?.[1]! >= (kline[index]?.[0] ?? 0) ? '#e05a5a' : '#4a9e6f' },
  }))
  const rsi = rsiSeries(closes, params.rsiPeriod)
  const { dif, dea, bar } = macdSeries(closes, params.macdFast, params.macdSlow, params.macdSignal)
  const { k, d, j } = kdjSeries(bars, params.kdjPeriod, params.kdjKSmooth, params.kdjDSmooth)
  return {
    animation: false,
    title: {
      left: 0,
      top: 0,
      textStyle: { fontSize: 12, fontWeight: 'normal' },
      // 参数必须标在图上，与计算层一致才不会误读
      text: paramSummary(params),
    },
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    legend: { top: 4, right: 0, itemWidth: 10, itemHeight: 8, textStyle: { fontSize: 11 } },
    grid: [
      { left: 48, right: 16, top: 34, height: '30%' },
      { left: 48, right: 16, top: '40%', height: '9%' },
      { left: 48, right: 16, top: '53%', height: '11%' },
      { left: 48, right: 16, top: '68%', height: '11%' },
    ],
    xAxis: [0, 1, 2, 3].map((index) => ({
      type: 'category',
      data: dates,
      gridIndex: index,
      boundaryGap: true,
      axisLine: { lineStyle: { color: '#c9ced6' } },
      axisLabel: { show: index === 3, fontSize: 10 },
      axisTick: { show: false },
    })),
    yAxis: [
      {
        scale: true,
        gridIndex: 0,
        splitLine: { lineStyle: { color: '#eef0f3' } },
        axisLabel: { fontSize: 10 },
      },
      { gridIndex: 1, splitNumber: 2, axisLabel: { show: false }, axisLine: { show: false } },
      {
        scale: true,
        gridIndex: 2,
        splitNumber: 2,
        axisLabel: { fontSize: 10 },
        splitLine: { show: false },
      },
      {
        scale: true,
        gridIndex: 3,
        splitNumber: 2,
        axisLabel: { fontSize: 10 },
        splitLine: { show: false },
      },
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1, 2, 3], start: 60, end: 100 },
      { type: 'slider', xAxisIndex: [0, 1, 2, 3], bottom: 0, height: 14, start: 60, end: 100 },
    ],
    series: [
      // 净值口径没有开高低与成交量，主图画净值折线，成交量副图留空但保留网格位置
      netWorth
        ? {
            name: '单位净值',
            type: 'line',
            data: closes,
            xAxisIndex: 0,
            yAxisIndex: 0,
            showSymbol: false,
            lineStyle: { width: 1.2 },
          }
        : { name: 'K 线', type: 'candlestick', data: kline, xAxisIndex: 0, yAxisIndex: 0 },
      netWorth
        ? { name: '净值日变动', type: 'bar', data: [], xAxisIndex: 1, yAxisIndex: 1 }
        : { name: '成交量', type: 'bar', data: volumes, xAxisIndex: 1, yAxisIndex: 1 },
      {
        name: 'MACD',
        type: 'bar',
        data: bar,
        xAxisIndex: 2,
        yAxisIndex: 2,
        itemStyle: { color: '#8a919f' },
      },
      {
        name: 'DIF',
        type: 'line',
        data: dif,
        xAxisIndex: 2,
        yAxisIndex: 2,
        showSymbol: false,
        lineStyle: { width: 1 },
      },
      {
        name: 'DEA',
        type: 'line',
        data: dea,
        xAxisIndex: 2,
        yAxisIndex: 2,
        showSymbol: false,
        lineStyle: { width: 1, type: 'dashed' },
      },
      {
        name: `RSI(${params.rsiPeriod})`,
        type: 'line',
        data: rsi,
        xAxisIndex: 3,
        yAxisIndex: 3,
        showSymbol: false,
        lineStyle: { width: 1 },
      },
      {
        name: 'K',
        type: 'line',
        data: k,
        xAxisIndex: 3,
        yAxisIndex: 3,
        showSymbol: false,
        lineStyle: { width: 1 },
      },
      {
        name: 'D',
        type: 'line',
        data: d,
        xAxisIndex: 3,
        yAxisIndex: 3,
        showSymbol: false,
        lineStyle: { width: 1 },
      },
      {
        name: 'J',
        type: 'line',
        data: j,
        xAxisIndex: 3,
        yAxisIndex: 3,
        showSymbol: false,
        lineStyle: { width: 1, type: 'dashed' },
      },
    ],
  }
}

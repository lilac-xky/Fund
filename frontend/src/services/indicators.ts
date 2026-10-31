/**
 * 技术指标的本地计算，午盘页用它按已取到的日线给出指标状态；完整口径与策略联动在后端 /indicator
 */

/** 指标参数，取自参数设置页的 INDICATOR 分组，取不到时用默认值 */
export interface IndicatorParams {
  /** RSI 周期 */
  rsiPeriod: number
  /** MACD 快线周期 */
  macdFast: number
  /** MACD 慢线周期 */
  macdSlow: number
  /** MACD 信号线周期 */
  macdSignal: number
}

/** 参数默认值，与后端 IndicatorParamKey 的内置默认一致 */
export const DEFAULT_INDICATOR_PARAMS: IndicatorParams = {
  rsiPeriod: 14,
  macdFast: 12,
  macdSlow: 26,
  macdSignal: 9,
}

/** 取移动平均的最后一个值，样本不足返回 null */
export function lastMa(closes: number[], period: number): number | null {
  if (period <= 0 || closes.length < period) {
    return null
  }
  let sum = 0
  for (let index = closes.length - period; index < closes.length; index++) {
    sum += closes[index] ?? 0
  }
  return sum / period
}

/** 按 Wilder 平滑算 RSI，样本不足返回 null */
export function rsi(closes: number[], period: number): number | null {
  if (period <= 0 || closes.length <= period) {
    return null
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
  for (let index = period + 1; index < closes.length; index++) {
    const diff = (closes[index] ?? 0) - (closes[index - 1] ?? 0)
    const up = diff > 0 ? diff : 0
    const down = diff < 0 ? -diff : 0
    avgGain = (avgGain * (period - 1) + up) / period
    avgLoss = (avgLoss * (period - 1) + down) / period
  }
  // 全段无下跌时按最强势处理，避免除零
  if (avgLoss === 0) {
    return 100
  }
  return 100 - 100 / (1 + avgGain / avgLoss)
}

/** 指数移动平均，首值作为种子 */
function ema(values: number[], period: number): number[] {
  const result: number[] = []
  const alpha = 2 / (period + 1)
  values.forEach((value, index) => {
    const previous = result[index - 1]
    result.push(previous === undefined ? value : previous + alpha * (value - previous))
  })
  return result
}

/** MACD 的 DIF / DEA / 柱，样本不足返回 null */
export function macd(
  closes: number[],
  fast: number,
  slow: number,
  signal: number,
): { dif: number; dea: number; bar: number } | null {
  if (closes.length < Math.max(fast, slow, signal) || fast <= 0 || slow <= fast || signal <= 0) {
    return null
  }
  const fastLine = ema(closes, fast)
  const slowLine = ema(closes, slow)
  const dif = fastLine.map((value, index) => value - (slowLine[index] ?? value))
  const dea = ema(dif, signal)
  const lastDif = dif[dif.length - 1]
  const lastDea = dea[dea.length - 1]
  if (lastDif === undefined || lastDea === undefined) {
    return null
  }
  return { dif: lastDif, dea: lastDea, bar: (lastDif - lastDea) * 2 }
}

/** 把日线收盘价压成缩略图坐标点，宽度与高度固定为 100×28 */
export function sparklinePoints(closes: number[], width = 100, height = 28): string {
  if (closes.length < 2) {
    return ''
  }
  const min = Math.min(...closes)
  const max = Math.max(...closes)
  const span = max - min || 1
  return closes
    .map((close, index) => {
      const x = (index / (closes.length - 1)) * width
      const y = height - ((close - min) / span) * height
      return `${x.toFixed(2)},${y.toFixed(2)}`
    })
    .join(' ')
}

/**
 * 误差序列压成折线坐标，零值落在中线，正负一眼可分
 *
 * @param values 误差序列，按日期升序
 * @param width  图宽
 * @param height 图高
 * @returns 坐标串，样本不足返回空串
 */
export function zeroBaselinePoints(values: number[], width = 100, height = 28): string {
  if (values.length < 2) {
    return ''
  }
  const maxAbs = Math.max(...values.map((value) => Math.abs(value)), 0.0001)
  const middle = height / 2
  return values
    .map((value, index) => {
      const x = (index / (values.length - 1)) * width
      const y = middle - (value / maxAbs) * (middle - 1)
      return `${x.toFixed(2)},${y.toFixed(2)}`
    })
    .join(' ')
}

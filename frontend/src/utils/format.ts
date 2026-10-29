/**
 * 页面共用的格式化：涨跌符号、数字精度、时间截断与类型配色
 */

/** 带正负号的百分比，缺值返回破折号 */
export function signedPercent(value?: number | null, digits = 2): string {
  if (value === undefined || value === null) {
    return '—'
  }
  return `${value > 0 ? '+' : ''}${Number(value).toFixed(digits)}%`
}

/** 普通数字，缺值返回破折号 */
export function plain(value?: number | null, digits = 2): string {
  if (value === undefined || value === null) {
    return '—'
  }
  return Number(value).toLocaleString('zh-CN', { maximumFractionDigits: digits })
}

/** 金额，两位小数 */
export function money(value?: number | null): string {
  if (value === undefined || value === null) {
    return '—'
  }
  return Number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/** 涨跌颜色类，涨暖红跌墨绿，零与缺值走中性 */
export function toneClass(value?: number | null): string {
  if (value === undefined || value === null || value === 0) {
    return 'tone-flat'
  }
  return value > 0 ? 'tone-up' : 'tone-down'
}

/** 涨跌内联颜色，图表与进度条用 */
export function toneColor(value?: number | null): string {
  if (value === undefined || value === null || value === 0) {
    return 'var(--t4)'
  }
  return value > 0 ? 'var(--up)' : 'var(--dn)'
}

/** 基金类型到配色变量的映射，六类各一色 */
export function categoryColor(category?: string | null): string {
  switch (category) {
    case 'STOCK':
      return 'var(--cat-stock)'
    case 'MIXED':
      return 'var(--cat-mixed)'
    case 'BOND':
      return 'var(--cat-bond)'
    case 'GOLD':
      return 'var(--cat-gold)'
    case 'DIVIDEND':
      return 'var(--cat-dividend)'
    case 'ETF':
      return 'var(--cat-etf)'
    default:
      return 'var(--cat-unknown)'
  }
}

/** 档位徽标配色，A 档可给数字，B 档只给方向，C 档不给 */
export function tierColor(tier?: string | null): string {
  switch (tier) {
    case 'A':
      return 'var(--dn)'
    case 'B':
      return 'var(--warn)'
    default:
      return 'var(--t4)'
  }
}

/** 时间截短到「月-日 时:分」 */
export function shortTime(value?: string | null): string {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').slice(5, 16)
}

/** ISO 时间转完整本地时间，失败原样返回 */
export function fullTime(value?: string | null): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const pad = (input: number) => String(input).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

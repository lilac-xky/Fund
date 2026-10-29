<template>
  <div class="app-pulse">
    <div class="app-pulse__cell">
      <div>
        <div class="app-pulse__k">今日预估</div>
        <div class="app-pulse__v app-pulse__v--big" :style="{ color: tone(estimatedChange) }">
          {{ signed(estimatedChange) }}
        </div>
      </div>
    </div>

    <div class="app-pulse__cell">
      <div>
        <div class="app-pulse__k">可预估</div>
        <div class="app-pulse__v">
          {{ estimableCount ?? 0 }}<span class="app-pulse__u"> / {{ totalCount ?? 0 }}</span>
        </div>
      </div>
    </div>

    <div class="app-pulse__cell">
      <div>
        <div class="app-pulse__k">整体冷暖</div>
        <div class="app-pulse__v">{{ marketMood ?? '—' }}</div>
      </div>
    </div>

    <div v-for="item in indexes ?? []" :key="item.name" class="app-pulse__cell">
      <div>
        <div class="app-pulse__k">{{ item.name }}</div>
        <div class="app-pulse__v app-pulse__v--sm" :style="{ color: tone(item.change) }">
          {{ signed(item.change) }}
        </div>
      </div>
    </div>

    <div v-if="marketValue != null" class="app-pulse__cell">
      <div>
        <div class="app-pulse__k">持仓市值</div>
        <div class="app-pulse__v app-pulse__v--sm">{{ marketValueText }}</div>
      </div>
    </div>

    <div class="app-pulse__cell app-pulse__cell--grow">
      <span v-if="dataAsOf" class="app-pulse__note">
        <i class="app-pulse__dot" />
        数据截止 {{ dataAsOf }}
      </span>
      <span v-if="nextNote" class="app-pulse__note app-pulse__note--dim">{{ nextNote }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 行情条：一行展示今日预估、可预估数、冷暖、指数、市值与数据截止
 */
import { computed } from 'vue'

const props = defineProps<{
  /** 今日预估涨跌幅，百分点；null 表示暂不可估 */
  estimatedChange?: number | null
  /** 可预估只数 */
  estimableCount?: number
  /** 持仓只数 */
  totalCount?: number
  /** 整体冷暖结论 */
  marketMood?: string
  /** 主要指数涨跌 */
  indexes?: Array<{ name: string; change: number }>
  /** 持仓市值 */
  marketValue?: number | null
  /** 数据截止时间 */
  dataAsOf?: string
  /** 下次生效说明 */
  nextNote?: string
}>()

/** 市值文案，按万元折算以免行情条被长数字撑开 */
const marketValueText = computed(() => {
  const value = props.marketValue
  if (value === undefined || value === null) {
    return '—'
  }
  if (Math.abs(value) >= 10000) {
    return `${(value / 10000).toFixed(2)} 万`
  }
  return value.toFixed(2)
})

/** 涨跌上色，涨红跌绿 */
function tone(value?: number | null): string {
  if (value === undefined || value === null || value === 0) {
    return 'var(--t4)'
  }
  return value > 0 ? 'var(--up)' : 'var(--dn)'
}

/** 带符号的百分比 */
function signed(value?: number | null): string {
  if (value === undefined || value === null) {
    return '—'
  }
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`
}
</script>

<style scoped>
.app-pulse {
  position: fixed;
  top: var(--topbar);
  left: 0;
  right: 0;
  z-index: 55;
  display: flex;
  align-items: center;
  height: var(--pulse);
  padding: 0 24px;
  overflow: hidden;
  border-bottom: 1px solid var(--hair);
  background: var(--pulse-bg);
  transition: background 0.3s;
}

.app-pulse__cell {
  display: flex;
  align-items: center;
  height: 36px;
  padding-right: 26px;
  margin-right: 26px;
  border-right: 1px solid var(--hair);
}

.app-pulse__cell--grow {
  flex: 1;
  justify-content: flex-end;
  gap: 16px;
  margin-right: 0;
  padding-right: 0;
  border-right: 0;
}

.app-pulse__k {
  font-size: 10px;
  font-weight: 650;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  white-space: nowrap;
  color: var(--t4);
}

.app-pulse__v {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: -0.025em;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.app-pulse__v--big {
  font-size: 20px;
}

.app-pulse__v--sm {
  font-size: 13.5px;
}

.app-pulse__u {
  font-size: 11px;
  font-weight: 400;
  color: var(--t4);
}

.app-pulse__note {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  white-space: nowrap;
  color: var(--t3);
}

.app-pulse__note--dim {
  color: var(--t4);
}

.app-pulse__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--dn);
  box-shadow: 0 0 0 3px var(--dn-soft);
}

@media (max-width: 860px) {
  .app-pulse {
    position: static;
    flex-wrap: wrap;
    height: auto;
    gap: 10px;
    padding: 10px 16px;
  }

  .app-pulse__cell {
    height: auto;
    padding-right: 16px;
    margin-right: 16px;
  }

  .app-pulse__cell--grow {
    flex: 1 1 100%;
    justify-content: flex-start;
  }
}
</style>

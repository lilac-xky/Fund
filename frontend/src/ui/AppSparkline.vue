<template>
  <svg
    class="app-spark"
    :class="{ 'app-spark--area': area }"
    :viewBox="`0 0 100 ${height}`"
    preserveAspectRatio="none"
    :style="{ color: toneColor(tone) }"
    aria-hidden="true"
  >
    <defs v-if="area && polygon">
      <linearGradient :id="gradientId" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="currentColor" stop-opacity="0.22" />
        <stop offset="100%" stop-color="currentColor" stop-opacity="0" />
      </linearGradient>
    </defs>
    <polygon v-if="area && polygon" :points="polygon" :fill="`url(#${gradientId})`" />
    <polyline v-if="points" :points="points" />
  </svg>
</template>

<script setup lang="ts">
/**
 * 走势缩略图：只用一条折线，可选面积填充，不引图表库，断网也能渲染
 */
import { computed } from 'vue'
import { toneColor } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    /** 坐标串，由 sparklinePoints 生成 */
    points: string
    /** 决定线条颜色的数值，通常是当日涨跌幅 */
    tone?: number | null
    /** 是否在折线下方填充渐变，行情条用 */
    area?: boolean
    /** 坐标高度，需与生成 points 时的高度一致 */
    height?: number
  }>(),
  { area: false, height: 28 },
)

/** 每个实例一个渐变 id，避免同页多个缩略图互相覆盖填充 */
const gradientId = computed(() => `spark-grad-${Math.random().toString(36).slice(2, 9)}`)

/**
 * 把折线坐标补成闭合多边形，用于面积填充
 *
 * @returns 多边形坐标串，无数据返回空串
 */
const polygon = computed(() => {
  const raw = props.points.trim()
  if (!raw) {
    return ''
  }
  const pairs = raw.split(' ')
  const first = pairs[0]?.split(',')[0] ?? '0'
  const last = pairs[pairs.length - 1]?.split(',')[0] ?? '100'
  // 从左下角绕过折线到右下角闭合，填充区域即折线下方
  return `${first},${props.height} ${raw} ${last},${props.height}`
})
</script>

<style scoped>
.app-spark {
  display: block;
  width: 100%;
  height: 28px;
  overflow: visible;
}

.app-spark--area {
  height: 30px;
}

.app-spark polyline {
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
}

.app-spark polygon {
  stroke: none;
}
</style>

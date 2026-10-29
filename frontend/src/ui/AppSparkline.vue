<template>
  <svg
    class="app-spark"
    :viewBox="`0 0 100 28`"
    preserveAspectRatio="none"
    :style="{ color: toneColor(tone) }"
    aria-hidden="true"
  >
    <polyline v-if="points" :points="points" />
  </svg>
</template>

<script setup lang="ts">
/**
 * 走势缩略图：只用一条折线，不引图表库，断网也能渲染
 */
import { toneColor } from '@/utils/format'

defineProps<{
  /** 坐标串，由 sparklinePoints 生成 */
  points: string
  /** 决定线条颜色的数值，通常是当日涨跌幅 */
  tone?: number | null
}>()
</script>

<style scoped>
.app-spark {
  display: block;
  width: 100%;
  height: 28px;
  overflow: visible;
}

.app-spark polyline {
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
}
</style>

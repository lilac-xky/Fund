<template>
  <div class="min-h-screen bg-[var(--canvas)]">
    <AppTopBar :active="String(route.name)" />
    <AppPulseBar />
    <div class="pt-[calc(var(--topbar)+var(--pulse))] max-[860px]:pt-[var(--topbar)]">
      <div class="mx-auto flex max-w-[560px] flex-col items-center px-6 py-24 text-center">
        <div
          class="mb-5 grid h-12 w-12 place-items-center rounded-[14px] bg-[var(--acc-soft)] text-[var(--acc)] [box-shadow:inset_0_0_0_1px_var(--acc-line)]"
        >
          <svg
            viewBox="0 0 24 24"
            class="h-5 w-5"
            fill="none"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <circle cx="12" cy="12" r="8.6" />
            <path d="M12 8v4.5M12 15.6v.2" />
          </svg>
        </div>
        <h1 class="text-[20px] font-semibold tracking-[-0.02em]">{{ title }}页尚未启用</h1>
        <p class="mt-3 text-[12.5px] leading-[1.75] text-[var(--t3)]">
          该模块随对应 issue 一起交付。当前已可用的是「总览」「持仓」「策略 /
          参数设置」「复盘」，可在顶栏切换。
        </p>
        <RouterLink
          to="/strategy"
          class="mt-6 inline-flex h-[34px] items-center rounded-[7px] border border-[var(--acc-hover)] px-[18px] text-[12.5px] font-semibold text-[var(--acc-on)] [background:linear-gradient(180deg,var(--acc-2),var(--acc))]"
        >
          前往参数设置
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 占位页：尚未实现的路由落到这里，明确说明「尚未启用」而不是白屏
 */
import AppTopBar from '@/layout/AppTopBar.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import { useRoute } from 'vue-router'

const route = useRoute()

/** 路由名对应的中文名 */
const TITLES: Record<string, string> = {
  task: '调度',
}

/** 当前路由的中文名 */
const title = TITLES[String(route.name)] ?? '该模块'
</script>

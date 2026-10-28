<template>
  <div class="pointer-events-none fixed right-5 bottom-[74px] z-[80] flex flex-col items-end gap-2">
    <TransitionGroup name="msg">
      <div
        v-for="item in visible"
        :key="item.id"
        class="pointer-events-auto flex max-w-[420px] cursor-pointer items-start gap-2.5 rounded-[10px] border px-3.5 py-2.5 text-[12.5px] leading-[1.55] [box-shadow:0_10px_28px_-14px_rgba(0,0,0,.35)] backdrop-blur-[14px]"
        :class="
          item.kind === 'error'
            ? 'border-[var(--acc-line)] bg-[var(--s1)] text-[var(--acc)]'
            : 'border-[var(--hair-2)] bg-[var(--s1)] text-[var(--t2)]'
        "
        @click="remove(item.id)"
      >
        <svg
          class="mt-[2px] h-3.5 w-3.5 flex-none"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path v-if="item.kind === 'success'" d="m5 12.5 4.5 4.5L19 7" />
          <template v-else-if="item.kind === 'error'">
            <circle cx="12" cy="12" r="9.2" />
            <path d="M12 7.5v5.2M12 16.3v.2" />
          </template>
          <template v-else>
            <circle cx="12" cy="12" r="9.2" />
            <path d="M12 11v5.4M12 7.6v.2" />
          </template>
        </svg>
        <span class="min-w-0">{{ item.text }}</span>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup lang="ts">
/**
 * 全局消息浮层：固定右下角，只显示最近三条，错误用强调色
 */
import { computed } from 'vue'
import { messages, remove } from './message'

/** 只展示最近三条，避免刷屏 */
const visible = computed(() => messages.slice(-3))
</script>

<style scoped>
.msg-enter-active,
.msg-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.msg-enter-from,
.msg-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
</style>

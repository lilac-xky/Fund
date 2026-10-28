<template>
  <div class="app-save">
    <div class="app-save__in">
      <div class="app-save__state">
        <span v-if="errorCount" class="app-save__err">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
          >
            <circle cx="12" cy="12" r="9.2" />
            <path d="M12 7.5v5.2M12 16.3v.2" />
          </svg>
          {{ errorCount }} 项校验未通过<template v-if="firstError"> · {{ firstError }}</template>
        </span>
        <span v-else class="app-save__ok">全部参数校验通过</span>

        <template v-if="dirtyCount">
          <span class="app-save__sep" />
          <span class="app-save__dirty">{{ dirtyCount }} 项已修改</span>
        </template>

        <template v-if="lastSavedAt">
          <span class="app-save__sep" />
          <span class="app-save__quiet">最后保存 {{ lastSavedAt }}</span>
        </template>
      </div>

      <div class="app-save__right">
        <slot name="extra" />
        <button type="button" class="app-save__ghost" @click="$emit('discard')">放弃修改</button>
        <button type="button" class="app-save__primary" :disabled="saving" @click="$emit('save')">
          {{ saving ? '保存中…' : '保存全部' }}
          <span class="app-save__kbd">⌘S</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 底部保存条：常驻显示校验失败数、脏值数与最后保存时间，主操作固定在右侧
 */
defineProps<{
  /** 校验失败条数 */
  errorCount?: number
  /** 第一条失败原因 */
  firstError?: string
  /** 已修改未保存项数 */
  dirtyCount?: number
  /** 最后保存时间 */
  lastSavedAt?: string
  /** 保存中 */
  saving?: boolean
}>()

defineEmits<{ (event: 'save'): void; (event: 'discard'): void }>()
</script>

<style scoped>
.app-save {
  position: fixed;
  right: 0;
  bottom: 0;
  left: var(--rail);
  z-index: 45;
  border-top: 1px solid var(--hair);
  background: var(--savebar-bg);
  backdrop-filter: blur(22px) saturate(175%);
  box-shadow: 0 -8px 24px -20px rgb(61 45 28 / 40%);
  transition:
    background 0.3s,
    left 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}

.app-save__in {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 58px;
  padding: 0 26px;
}

.app-save__state {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
  font-size: 12px;
  color: var(--t2);
}

.app-save__err {
  display: flex;
  align-items: center;
  flex: none;
  gap: 5px;
  font-weight: 550;
  white-space: nowrap;
  color: var(--acc);
}

.app-save__err svg {
  flex: none;
  width: 11.5px;
  height: 11.5px;
}

.app-save__ok {
  white-space: nowrap;
  color: var(--t4);
}

.app-save__sep {
  flex: none;
  width: 1px;
  height: 12px;
  background: var(--hair-2);
}

.app-save__dirty {
  flex: none;
  font-weight: 550;
  white-space: nowrap;
  color: var(--warn);
}

.app-save__quiet {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--t4);
}

.app-save__right {
  display: flex;
  align-items: center;
  flex: none;
  gap: 7px;
  margin-left: auto;
}

.app-save__ghost {
  height: 34px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: var(--t3);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: 0.15s;
}

.app-save__ghost:hover {
  background: var(--s2);
  color: var(--t1);
}

.app-save__primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0 18px;
  border: 1px solid var(--acc-hover);
  border-radius: 7px;
  background: linear-gradient(180deg, var(--acc-2), var(--acc));
  box-shadow:
    0 1px 0 rgb(255 255 255 / 24%) inset,
    0 6px 16px -8px var(--acc-glow);
  color: var(--acc-on);
  font: inherit;
  font-size: 12.5px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: filter 0.15s;
}

.app-save__primary:hover {
  filter: brightness(1.05);
}

.app-save__primary:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.app-save__kbd {
  margin-left: 6px;
  padding: 1px 4px;
  border: 1px solid var(--kbd-line);
  border-radius: 3.5px;
  color: var(--kbd-fg);
  font-size: 10px;
}

@media (max-width: 860px) {
  .app-save {
    left: 0;
  }

  .app-save__in {
    padding: 0 16px;
  }

  .app-save__quiet {
    display: none;
  }
}
</style>

<template>
  <div class="app-alert" :class="`app-alert--${kind}`">
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      <template v-if="kind === 'error'">
        <circle cx="12" cy="12" r="9.2" />
        <path d="M12 7.5v5.2M12 16.3v.2" />
      </template>
      <template v-else-if="kind === 'warn'">
        <path d="M12 4.5 2.8 20h18.4z" />
        <path d="M12 10v4.2M12 17.2v.2" />
      </template>
      <template v-else-if="kind === 'ok'">
        <circle cx="12" cy="12" r="9.2" />
        <path d="m7.8 12.2 3 3L16.4 9" />
      </template>
      <template v-else>
        <circle cx="12" cy="12" r="9.2" />
        <path d="M12 11v5.4M12 7.6v.2" />
      </template>
    </svg>
    <div class="app-alert__tx">
      <b v-if="title">{{ title }}</b>
      <span><slot /></span>
    </div>
    <button v-if="closable" type="button" class="app-alert__x" @click="$emit('close')">×</button>
  </div>
</template>

<script setup lang="ts">
/**
 * 提示条：错误、警告、正常与说明四种语气，用主题 token 着色
 */
withDefaults(
  defineProps<{
    /** 语气 */
    kind?: 'error' | 'warn' | 'ok' | 'info'
    /** 加粗标题，可空 */
    title?: string
    /** 是否可关闭 */
    closable?: boolean
  }>(),
  { kind: 'info', title: '', closable: false },
)

defineEmits<{ (event: 'close'): void }>()
</script>

<style scoped>
.app-alert {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-bottom: 14px;
  padding: 11px 14px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.6;
}

.app-alert svg {
  flex: none;
  width: 14px;
  height: 14px;
  margin-top: 2px;
  stroke: currentColor;
  fill: none;
}

.app-alert__tx {
  flex: 1;
  min-width: 0;
}

.app-alert__tx b {
  display: block;
  margin-bottom: 2px;
  font-weight: 650;
}

.app-alert--error {
  background: var(--acc-soft);
  box-shadow: inset 0 0 0 1px var(--acc-line);
  color: var(--acc);
}

.app-alert--warn {
  background: color-mix(in oklab, var(--warn) 12%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in oklab, var(--warn) 26%, transparent);
  color: var(--warn);
}

.app-alert--ok {
  background: var(--dn-soft);
  box-shadow: inset 0 0 0 1px color-mix(in oklab, var(--dn) 22%, transparent);
  color: var(--dn);
}

.app-alert--info {
  background: var(--s2);
  box-shadow: inset 0 0 0 1px var(--hair-2);
  color: var(--t3);
}

.app-alert__x {
  flex: none;
  border: 0;
  background: transparent;
  color: currentColor;
  font-size: 15px;
  line-height: 1;
  cursor: pointer;
  opacity: 0.7;
}

.app-alert__x:hover {
  opacity: 1;
}
</style>

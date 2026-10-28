<template>
  <button
    :type="type"
    :disabled="disabled"
    class="app-btn"
    :class="[`app-btn--${variant}`, `app-btn--${size}`]"
    @click="$emit('click', $event)"
  >
    <slot name="icon" />
    <slot />
  </button>
</template>

<script setup lang="ts">
/**
 * 按钮：三档层次（primary / default / ghost）+ 尺寸，强调色只出现在 primary
 */
withDefaults(
  defineProps<{
    /** 视觉层次 */
    variant?: 'primary' | 'default' | 'ghost'
    /** 尺寸 */
    size?: 'sm' | 'md' | 'lg'
    /** 是否禁用 */
    disabled?: boolean
    /** 原生按钮类型 */
    type?: 'button' | 'submit' | 'reset'
  }>(),
  {
    variant: 'default',
    size: 'sm',
    disabled: false,
    type: 'button',
  },
)

defineEmits<{ (event: 'click', payload: MouseEvent): void }>()
</script>

<style scoped>
.app-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  gap: 6px;
  border: 1px solid var(--hair-2);
  border-radius: 7px;
  background: var(--btn-bg);
  box-shadow: var(--sh-sm);
  color: var(--t2);
  font: inherit;
  font-size: 11.5px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  user-select: none;
  transition:
    background 0.15s,
    border-color 0.15s,
    color 0.15s,
    filter 0.15s;
}

.app-btn:hover {
  border-color: var(--hair-3);
  background: var(--btn-hover-bg);
  color: var(--t1);
}

.app-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

/* 尺寸 */
.app-btn--sm {
  height: 28px;
  padding: 0 10px;
}

.app-btn--md {
  height: 31px;
  padding: 0 13px;
  font-size: 12px;
}

.app-btn--lg {
  height: 34px;
  padding: 0 18px;
  font-size: 12.5px;
}

/* 层次 */
.app-btn--primary {
  border-color: var(--acc-hover);
  background: linear-gradient(180deg, var(--acc-2), var(--acc));
  box-shadow:
    0 1px 0 rgb(255 255 255 / 24%) inset,
    0 6px 16px -8px var(--acc-glow);
  color: var(--acc-on);
  font-size: 12.5px;
  font-weight: 600;
}

.app-btn--primary:hover {
  filter: brightness(1.05);
}

.app-btn--ghost {
  height: 34px;
  padding: 0 14px;
  border-color: transparent;
  background: transparent;
  box-shadow: none;
  color: var(--t3);
  font-size: 12px;
}

.app-btn--ghost:hover {
  border-color: transparent;
  background: var(--s2);
  color: var(--t1);
}
</style>

<template>
  <div class="app-field" :class="error ? 'app-field--error' : ''">
    <div class="app-field__lab">
      <div class="app-field__title">{{ label }}</div>
      <div v-if="description" class="app-field__desc">{{ description }}</div>
    </div>
    <div class="app-field__ctl">
      <slot />
      <span v-if="unit" class="app-field__unit">{{ unit }}</span>
    </div>
    <div v-if="error" class="app-field__err">
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
      <span>{{ error }}</span>
    </div>
    <div v-else-if="hint" class="app-field__hint">{{ hint }}</div>
  </div>
</template>

<script setup lang="ts">
/**
 * 字段：标签在左、控件在右的行式布局，错误紧贴控件下方
 */
defineProps<{
  /** 字段标签 */
  label: string
  /** 一句说明 */
  description?: string
  /** 单位，如 元 / % */
  unit?: string
  /** 错误文案，存在即进入错误态 */
  error?: string
  /** 提示文案，无错误时展示 */
  hint?: string
}>()
</script>

<style scoped>
.app-field {
  display: grid;
  grid-template-columns: 168px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
  min-width: 0;
  padding: 15px 0;
  border-bottom: 1px solid var(--hair);
}

.app-field:last-child {
  border-bottom: 0;
}

.app-field--error .app-field__lab {
  color: var(--acc);
}

.app-field__lab {
  padding-top: 8px;
  min-width: 0;
}

.app-field__title {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--t1);
}

.app-field--error .app-field__title {
  color: var(--acc);
}

.app-field__desc {
  margin-top: 3px;
  font-size: 11px;
  line-height: 1.55;
  color: var(--t4);
}

.app-field__ctl {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.app-field__unit {
  flex: none;
  font-size: 11px;
  color: var(--t4);
}

.app-field__err {
  grid-column: 2;
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.45;
  color: var(--acc);
}

.app-field__err svg {
  flex: none;
  width: 11.5px;
  height: 11.5px;
  margin-top: 1.5px;
  stroke: currentColor;
  fill: none;
}

.app-field__hint {
  grid-column: 2;
  margin-top: 6px;
  font-size: 11px;
  color: var(--t4);
}

/* 窄屏改上下堆叠 */
@media (max-width: 720px) {
  .app-field {
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
    padding: 12px 14px;
  }

  .app-field__lab {
    padding-top: 0;
  }

  .app-field__err,
  .app-field__hint {
    grid-column: 1;
  }
}
</style>

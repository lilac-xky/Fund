<template>
  <button type="button" class="app-sw" :disabled="disabled" @click="toggle">
    <span class="app-sw__track" :class="{ 'app-sw__track--on': modelValue }">
      <span class="app-sw__knob" />
    </span>
    <span v-if="label">{{ label }}</span>
  </button>
</template>

<script setup lang="ts">
/**
 * 开关：用于布尔型参数，轨道用小尺寸，避免比输入框更抢眼
 */
const props = defineProps<{
  /** 是否开启 */
  modelValue: boolean
  /** 文案 */
  label?: string
  /** 是否禁用 */
  disabled?: boolean
}>()

const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void }>()

/** 切换开关状态 */
function toggle(): void {
  if (!props.disabled) {
    emit('update:modelValue', !props.modelValue)
  }
}
</script>

<style scoped>
.app-sw {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--t2);
  font: inherit;
  font-size: 12.5px;
  cursor: pointer;
}

.app-sw:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.app-sw__track {
  position: relative;
  flex: none;
  width: 34px;
  height: 19px;
  border: 1px solid var(--hair-2);
  border-radius: 99px;
  background: var(--track-off);
  transition:
    background 0.2s,
    border-color 0.2s;
}

.app-sw__track--on {
  border-color: var(--acc-hover);
  background: var(--acc);
}

.app-sw__knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 13px;
  height: 13px;
  border-radius: 50%;
  background: var(--sw-off);
  box-shadow: 0 1px 2px rgb(61 45 28 / 26%);
  transition:
    left 0.2s,
    background 0.2s;
}

.app-sw__track--on .app-sw__knob {
  left: 17px;
  background: #fff;
}
</style>

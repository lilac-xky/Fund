<template>
  <input
    :type="type"
    :value="modelValue"
    :placeholder="placeholder"
    :readonly="readonly"
    :disabled="disabled"
    :inputmode="inputmode"
    class="app-input"
    :class="{ 'app-input--num': numeric, 'app-input--invalid': invalid }"
    @input="onInput"
    @blur="$emit('blur')"
    @keyup.enter="$emit('enter')"
  />
</template>

<script setup lang="ts">
/**
 * 输入框：统一视觉，聚焦用主题强调色描边，错误态由父级传入
 */
withDefaults(
  defineProps<{
    /** 绑定值 */
    modelValue?: string
    /** 占位符 */
    placeholder?: string
    /** 输入类型 */
    type?: 'text' | 'password' | 'number' | 'email'
    /** 是否只读 */
    readonly?: boolean
    /** 是否禁用 */
    disabled?: boolean
    /** 是否处于错误态 */
    invalid?: boolean
    /** 是否用等宽数字 */
    numeric?: boolean
    /** inputmode，移动端唤起对应键盘 */
    inputmode?: 'text' | 'numeric' | 'decimal' | 'email'
  }>(),
  {
    modelValue: '',
    placeholder: '',
    type: 'text',
    readonly: false,
    disabled: false,
    invalid: false,
    numeric: true,
    inputmode: 'text',
  },
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'blur'): void
  (event: 'enter'): void
}>()

/** 把输入值同步给父级，解析与校验交给后端 */
function onInput(event: Event): void {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}
</script>

<style scoped>
.app-input {
  flex: 1;
  min-width: 0;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--hair-2);
  border-radius: 8px;
  background: var(--inp-bg);
  box-shadow: var(--inp-shadow);
  color: var(--t1);
  font: inherit;
  font-size: 12.5px;
  outline: none;
  transition:
    border-color 0.15s,
    box-shadow 0.15s,
    background 0.15s;
}

.app-input--num {
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.app-input::placeholder {
  color: var(--t4);
}

.app-input:hover {
  border-color: var(--hair-3);
}

.app-input:focus {
  border-color: var(--acc);
  box-shadow:
    0 0 0 3.5px var(--acc-glow),
    var(--inp-shadow);
}

.app-input--invalid {
  border-color: var(--acc);
  box-shadow:
    0 0 0 3.5px var(--acc-glow),
    var(--inp-shadow);
}

.app-input:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
</style>

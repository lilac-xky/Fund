<template>
  <div class="app-block">
    <div class="app-block__k">
      {{ title }}
      <slot name="count" />
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
/**
 * 右信息栏的一个区块：标题 + 内容，只负责间距与标题样式
 */
defineProps<{
  /** 区块标题 */
  title: string
}>()
</script>

<style scoped>
.app-block {
  margin-bottom: 20px;
}

.app-block:last-child {
  margin-bottom: 0;
}

.app-block__k {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.11em;
  text-transform: uppercase;
  color: var(--t4);
}
</style>

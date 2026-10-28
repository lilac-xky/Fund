<template>
  <section class="app-group">
    <header class="app-group__head">
      <span class="app-group__chip">
        <slot name="icon" />
      </span>
      <h2 class="app-group__title">{{ title }}</h2>
      <span class="app-group__code">{{ code }}</span>
      <span v-if="meta" class="app-group__meta">
        <i v-if="metaOk" class="app-group__dot" />
        {{ meta }}
      </span>
    </header>
    <div class="app-group__body">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
/**
 * 分组卡：标题栏 + 图标 + 编码徽标 + 右侧状态，内部字段单列铺开
 */
defineProps<{
  /** 分组标题 */
  title: string
  /** 分组编码，如 DIVIDEND */
  code: string
  /** 右上角状态文字 */
  meta?: string
  /** 状态点是否为正常色 */
  metaOk?: boolean
}>()
</script>

<style scoped>
.app-group {
  margin-bottom: 14px;
  overflow: hidden;
  border: 1px solid var(--hair);
  border-radius: 15px;
  background: var(--group-bg);
  box-shadow: var(--group-hi), var(--group-sh);
  transition:
    background 0.3s,
    border-color 0.3s;
}

.app-group__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 22px;
  border-bottom: 1px solid var(--hair);
  background: linear-gradient(180deg, var(--s2), transparent);
}

.app-group__chip {
  display: grid;
  place-items: center;
  flex: none;
  width: 25px;
  height: 25px;
  border: 1px solid var(--hair-2);
  border-radius: 7px;
  background: var(--s3);
  color: var(--t2);
}

.app-group__chip :deep(svg) {
  width: 13px;
  height: 13px;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.app-group__title {
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: -0.008em;
}

.app-group__code {
  padding: 1.5px 5px;
  border: 1px solid var(--hair);
  border-radius: 4px;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.09em;
  color: var(--t4);
}

.app-group__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  font-size: 11px;
  color: var(--t3);
  text-align: right;
}

.app-group__dot {
  flex: none;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--dn);
  box-shadow: 0 0 0 3px var(--dn-soft);
}

.app-group__body {
  padding: 2px 22px 8px;
}
</style>

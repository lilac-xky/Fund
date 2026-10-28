<template>
  <aside class="app-rail">
    <nav class="app-rail__nav">
      <div class="app-rail__lbl">{{ sectionLabel ?? '分组' }}</div>
      <a
        v-for="item in items"
        :key="item.key"
        href="javascript:void(0)"
        class="app-nav"
        :class="{ 'app-nav--on': active === item.key }"
        @click="$emit('select', item.key)"
      >
        <span class="app-nav__ico" v-html="item.icon" />
        <span class="app-nav__txt">{{ item.label }}</span>
        <span v-if="item.count !== undefined" class="app-nav__n">{{ item.count }}</span>
      </a>
    </nav>

    <div class="app-rail__foot">
      <button type="button" class="app-rail__btn" @click="$emit('footer')">
        <span class="app-rail__btnico" v-html="footerIcon ?? ''" />
        <span>{{ footerLabel }}</span>
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
/**
 * 侧栏：当前模块的分组锚点，可折叠成仅图标，底部放当前页操作入口
 */
defineProps<{
  /** 分组标题 */
  sectionLabel?: string
  /** 锚点项 */
  items: Array<{ key: string; label: string; icon: string; count?: number }>
  /** 当前激活项 */
  active: string
  /** 底部入口 */
  footerLabel?: string
  /** 底部入口图标 */
  footerIcon?: string
}>()

defineEmits<{ (event: 'select', key: string): void; (event: 'footer'): void }>()
</script>

<style scoped>
.app-rail {
  position: fixed;
  top: calc(var(--topbar) + var(--pulse));
  bottom: 0;
  left: 0;
  z-index: 50;
  display: flex;
  flex-direction: column;
  width: var(--rail);
  overflow: hidden;
  border-right: 1px solid var(--hair);
  background: var(--rail-bg);
  transition:
    width 0.24s cubic-bezier(0.4, 0, 0.2, 1),
    background 0.3s;
}

.app-rail__nav {
  flex: 1;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 14px 11px;
}

.app-rail__lbl {
  padding: 12px 10px 7px;
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.11em;
  text-transform: uppercase;
  white-space: nowrap;
  color: var(--t4);
  transition: opacity 0.14s;
}

.app-rail__lbl:first-child {
  padding-top: 0;
}

.app-nav {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  padding: 10px 12px;
  border: 1px solid var(--hair);
  border-radius: 10px;
  background: var(--s1);
  box-shadow: var(--sh-sm);
  font-size: 12.5px;
  white-space: nowrap;
  color: var(--t2);
  text-decoration: none;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s,
    color 0.15s;
}

.app-nav:hover {
  border-color: var(--hair-2);
  background: var(--s2);
  color: var(--t1);
}

.app-nav--on {
  border-color: var(--acc-line);
  background: var(--acc-soft);
  color: var(--acc);
  font-weight: 550;
}

.app-nav__ico {
  display: block;
  flex: none;
  width: 16px;
  height: 16px;
  opacity: 0.85;
}

.app-nav__ico :deep(svg) {
  width: 16px;
  height: 16px;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.app-nav__txt {
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-nav__n {
  margin-left: auto;
  flex: none;
  font-size: 10.5px;
  color: var(--t4);
}

.app-nav--on .app-nav__n {
  color: var(--acc);
  opacity: 0.75;
}

.app-rail__foot {
  flex: none;
  padding: 10px 11px 12px;
  border-top: 1px solid var(--hair);
}

.app-rail__btn {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 30px;
  padding: 0 9px;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: var(--t3);
  font: inherit;
  font-size: 11.5px;
  white-space: nowrap;
  cursor: pointer;
  transition: 0.15s;
}

.app-rail__btn:hover {
  background: var(--s3);
  color: var(--t1);
}

.app-rail__btnico {
  display: block;
  flex: none;
  width: 14px;
  height: 14px;
}

.app-rail__btnico :deep(svg) {
  width: 14px;
  height: 14px;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.75;
  stroke-linecap: round;
  stroke-linejoin: round;
}

/* ── 折叠态：只剩图标 ── */
[data-rail='collapsed'] .app-rail__nav {
  padding: 14px 8px;
}

[data-rail='collapsed'] .app-rail__lbl {
  height: 0;
  padding: 0;
  margin: 0;
  overflow: hidden;
  opacity: 0;
}

[data-rail='collapsed'] .app-nav {
  justify-content: center;
  gap: 0;
  padding: 9px 0;
}

[data-rail='collapsed'] .app-nav__txt,
[data-rail='collapsed'] .app-nav__n {
  display: none;
}

[data-rail='collapsed'] .app-rail__foot {
  padding: 10px 8px 12px;
}

[data-rail='collapsed'] .app-rail__btn {
  justify-content: center;
  padding: 0;
}

[data-rail='collapsed'] .app-rail__btn span:last-child {
  display: none;
}

/* ── 移动端转抽屉 ── */
@media (max-width: 860px) {
  .app-rail {
    top: var(--topbar);
    width: 240px;
    transform: translateX(-100%);
    transition: transform 0.26s cubic-bezier(0.4, 0, 0.2, 1);
  }

  [data-rail='open'] .app-rail {
    transform: translateX(0);
    box-shadow: 16px 0 40px -20px rgb(0 0 0 / 50%);
  }
}
</style>

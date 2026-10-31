<template>
  <header class="app-top">
    <button type="button" class="app-top__burger" title="菜单" @click="toggleDrawer">
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.75"
        stroke-linecap="round"
      >
        <path d="M4 6h16M4 12h16M4 18h16" />
      </svg>
    </button>

    <div class="app-top__brand">
      <span class="app-top__glyph">
        <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="1.9">
          <circle cx="12" cy="12" r="8.6" />
          <path d="m15.2 8.8-2 4.4-4.4 2 2-4.4z" fill="#fff" stroke="none" />
        </svg>
      </span>
      <div>
        <div class="app-top__name">基策罗盘</div>
        <div class="app-top__en">FUND COMPASS</div>
      </div>
    </div>

    <nav class="app-top__nav">
      <RouterLink
        v-for="item in navItems"
        :key="item.key"
        :to="`/${item.key}`"
        class="app-tab"
        :class="{ 'app-tab--on': active === item.key }"
      >
        {{ item.label }}
      </RouterLink>
    </nav>

    <div class="app-top__right">
      <span class="app-top__status">
        <i class="app-top__dot" />
        <slot name="status">已就绪</slot>
      </span>

      <button type="button" class="app-icobtn" title="折叠侧栏" @click="toggleRail">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.75"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M15 6l-6 6 6 6" />
        </svg>
      </button>

      <button
        type="button"
        class="app-icobtn"
        :title="isDark ? '切换到浅色' : '切换到深色'"
        @click="toggleTheme"
      >
        <svg
          v-if="isDark"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.75"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <circle cx="12" cy="12" r="4.2" />
          <path
            d="M12 2.5v2.2M12 19.3v2.2M4.2 4.2l1.6 1.6M18.2 18.2l1.6 1.6M2.5 12h2.2M19.3 12h2.2M4.2 19.8l1.6-1.6M18.2 5.8l1.6-1.6"
          />
        </svg>
        <svg
          v-else
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.75"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M20.5 14.4A8.6 8.6 0 0 1 9.6 3.5 8.6 8.6 0 1 0 20.5 14.4z" />
        </svg>
      </button>

      <button v-if="authRequired" type="button" class="app-icobtn" title="登出" @click="onLogout">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.75"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M15 17.5V19a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v1.5" />
          <path d="M10 12h10M17 8.5 20.5 12 17 15.5" />
        </svg>
      </button>

      <span class="app-avatar">我</span>
    </div>
  </header>
</template>

<script setup lang="ts">
/**
 * 顶栏：品牌 + 全局导航 + 折叠 / 深浅色 / 登出 / 头像，固定在视口顶部
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useTheme } from '@/composables/useTheme'
import { useAuthStore } from '@/stores/auth'

const { isDark, toggleTheme, toggleRail, toggleDrawer } = useTheme()
const router = useRouter()
const auth = useAuthStore()

/** 登录开关打开时才显示登出按钮 */
const authRequired = computed(() => auth.status.required !== false)

/** 登出后回登录页 */
async function onLogout(): Promise<void> {
  await auth.logout()
  await router.replace({ name: 'login' })
}

/** 全局导航项，模块级入口，具体页面内的分组交给侧栏 */
const navItems = [
  { key: 'overview', label: '总览' },
  { key: 'holding', label: '持仓' },
  { key: 'strategy', label: '策略' },
  { key: 'indicator', label: '指标' },
  { key: 'review', label: '复盘' },
  { key: 'task', label: '调度' },
]

defineProps<{
  /** 当前激活的导航项 */
  active: string
}>()
</script>

<style scoped>
.app-top {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  height: var(--topbar);
  padding: 0 20px;
  border-bottom: 1px solid var(--hair);
  background: var(--topbar-bg);
  backdrop-filter: blur(22px) saturate(175%);
  transition: background 0.3s;
}

.app-top__burger {
  display: none;
  flex: none;
  place-items: center;
  width: 28px;
  height: 28px;
  margin-right: 8px;
  border: 1px solid var(--hair);
  border-radius: 8px;
  background: var(--s1);
  box-shadow: var(--sh-sm);
  color: var(--t3);
  cursor: pointer;
}

.app-top__burger svg {
  width: 14px;
  height: 14px;
}

.app-top__brand {
  display: flex;
  align-items: center;
  flex: none;
  gap: 10px;
  width: 212px;
}

.app-top__glyph {
  display: grid;
  place-items: center;
  flex: none;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  background: linear-gradient(150deg, #d9764c, #a8502a);
  box-shadow:
    0 0 0 1px rgb(255 255 255 / 10%) inset,
    0 5px 16px -6px rgb(180 80 43 / 80%);
}

.app-top__glyph svg {
  width: 15px;
  height: 15px;
}

.app-top__name {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.005em;
  white-space: nowrap;
}

.app-top__en {
  font-size: 9.5px;
  letter-spacing: 0.11em;
  white-space: nowrap;
  color: var(--t4);
}

.app-top__nav {
  display: flex;
  flex: none;
  gap: 2px;
}

.app-tab {
  position: relative;
  padding: 7px 13px;
  border-radius: 7px;
  font-size: 13px;
  white-space: nowrap;
  color: var(--t3);
  text-decoration: none;
  transition:
    color 0.15s,
    background 0.15s;
}

.app-tab:hover {
  background: var(--s3);
  color: var(--t1);
}

.app-tab--on {
  color: var(--t1);
  font-weight: 550;
}

.app-tab--on::after {
  content: '';
  position: absolute;
  right: 13px;
  bottom: -18px;
  left: 13px;
  height: 1.5px;
  background: linear-gradient(90deg, transparent, var(--acc), transparent);
}

.app-top__right {
  display: flex;
  align-items: center;
  flex: none;
  gap: 8px;
  margin-left: auto;
}

.app-top__status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--hair);
  border-radius: 99px;
  background: var(--s1);
  box-shadow: var(--sh-sm);
  font-size: 11.5px;
  color: var(--t2);
  white-space: nowrap;
}

.app-top__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--dn);
  box-shadow: 0 0 0 3px var(--dn-soft);
}

.app-icobtn {
  display: grid;
  place-items: center;
  flex: none;
  width: 28px;
  height: 28px;
  border: 1px solid var(--hair);
  border-radius: 8px;
  background: var(--s1);
  box-shadow: var(--sh-sm);
  color: var(--t3);
  cursor: pointer;
  transition: 0.15s;
}

.app-icobtn:hover {
  border-color: var(--hair-2);
  background: var(--btn-hover-bg);
  color: var(--t1);
}

.app-icobtn:active {
  transform: scale(0.94);
}

.app-icobtn svg {
  width: 14px;
  height: 14px;
}

.app-avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(150deg, #d9764c, #a8502a);
  box-shadow: var(--sh-sm);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

@media (max-width: 860px) {
  .app-top__burger {
    display: grid;
  }

  .app-top__nav {
    display: none;
  }

  .app-top__brand {
    width: auto;
  }

  .app-top__status {
    display: none;
  }
}
</style>

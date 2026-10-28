/**
 * 主题状态：深浅色切换与侧栏折叠，两个偏好都落到 localStorage 并同步到根元素
 */
import { computed, ref, watch } from 'vue'

/** 主题模式 */
export type ThemeMode = 'light' | 'dark'

/** 侧栏状态 */
export type RailMode = 'expanded' | 'collapsed' | 'open'

const THEME_KEY = 'fund-theme'
const RAIL_KEY = 'fund-rail'

/** 读取本地偏好，没有则按浅色 */
function readTheme(): ThemeMode {
  const saved = localStorage.getItem(THEME_KEY)
  return saved === 'dark' ? 'dark' : 'light'
}

/** 读取侧栏偏好，默认展开 */
function readRail(): RailMode {
  return localStorage.getItem(RAIL_KEY) === 'collapsed' ? 'collapsed' : 'expanded'
}

/** 当前主题 */
const theme = ref<ThemeMode>(readTheme())

/** 当前侧栏状态 */
const rail = ref<RailMode>(readRail())

/** 是否为深色，供图标与文案使用 */
const isDark = computed(() => theme.value === 'dark')

/** 把主题写到根元素，CSS 变量随之切换 */
function applyTheme(mode: ThemeMode): void {
  document.documentElement.dataset.theme = mode
}

/** 把侧栏状态写到根元素，CSS 变量随之切换 */
function applyRail(mode: RailMode): void {
  document.documentElement.dataset.rail = mode
}

applyTheme(theme.value)
applyRail(rail.value)

watch(theme, (value) => {
  applyTheme(value)
  localStorage.setItem(THEME_KEY, value)
})

watch(rail, (value) => {
  applyRail(value)
  // 抽屉态是临时状态，不写进偏好，避免下次进页面侧栏仍然展开
  if (value !== 'open') {
    localStorage.setItem(RAIL_KEY, value === 'collapsed' ? 'collapsed' : 'expanded')
  }
})

/**
 * 主题与布局偏好
 */
export function useTheme() {
  return {
    theme,
    rail,
    isDark,
    /**
     * 切换深浅色
     */
    toggleTheme: () => {
      theme.value = theme.value === 'dark' ? 'light' : 'dark'
    },
    /**
     * 桌面端展开 / 折叠侧栏
     */
    toggleRail: () => {
      rail.value = rail.value === 'collapsed' ? 'expanded' : 'collapsed'
    },
    /**
     * 移动端打开 / 关闭抽屉
     */
    toggleDrawer: () => {
      rail.value = rail.value === 'open' ? 'collapsed' : 'open'
    },
    /**
     * 关闭抽屉
     */
    closeDrawer: () => {
      if (rail.value === 'open') {
        rail.value = 'collapsed'
      }
    },
  }
}

/**
 * 路由表：登录页独立，业务页面在进入前统一校验会话，未登录先跳登录页
 */
import { createRouter, createWebHistory } from 'vue-router'
import SettingView from '@/views/SettingView.vue'
import HoldingView from '@/views/HoldingView.vue'
import LoginView from '@/views/LoginView.vue'
import OverviewView from '@/views/OverviewView.vue'
import ReviewView from '@/views/ReviewView.vue'
import IndicatorView from '@/views/IndicatorView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/overview' },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/overview', name: 'overview', component: OverviewView },
    { path: '/strategy', name: 'strategy', component: SettingView },
    { path: '/holding', name: 'holding', component: HoldingView },
    { path: '/review', name: 'review', component: ReviewView },
    { path: '/indicator', name: 'indicator', component: IndicatorView },
    { path: '/task', name: 'task', component: PlaceholderView },
    { path: '/:pathMatch(.*)*', redirect: '/overview' },
  ],
})

router.beforeEach(async (to) => {
  // 登录页自己不需要鉴权，否则会无限重定向
  if (to.name === 'login') {
    return true
  }
  const auth = useAuthStore()
  // 首次进入时先查一次会话，之后复用已取到的状态
  if (auth.checking || auth.status.required === undefined) {
    await auth.refresh()
  }
  if (auth.authenticated) {
    return true
  }
  return { name: 'login', query: { redirect: to.fullPath } }
})

export default router

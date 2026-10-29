/**
 * 路由表：总览、复盘、参数设置与持仓录入已具备完整实现，其余模块给占位页避免导航断链
 */
import { createRouter, createWebHistory } from 'vue-router'
import SettingView from '@/views/SettingView.vue'
import HoldingView from '@/views/HoldingView.vue'
import OverviewView from '@/views/OverviewView.vue'
import ReviewView from '@/views/ReviewView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/overview' },
    { path: '/overview', name: 'overview', component: OverviewView },
    { path: '/strategy', name: 'strategy', component: SettingView },
    { path: '/holding', name: 'holding', component: HoldingView },
    { path: '/review', name: 'review', component: ReviewView },
    { path: '/task', name: 'task', component: PlaceholderView },
    { path: '/:pathMatch(.*)*', redirect: '/overview' },
  ],
})

export default router

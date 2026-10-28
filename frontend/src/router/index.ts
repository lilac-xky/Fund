/**
 * 路由表：当前只有参数设置页具备完整实现，其余模块给占位页避免导航断链
 */
import { createRouter, createWebHistory } from 'vue-router'
import SettingView from '@/views/SettingView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/strategy' },
    { path: '/strategy', name: 'strategy', component: SettingView },
    { path: '/overview', name: 'overview', component: PlaceholderView },
    { path: '/holding', name: 'holding', component: PlaceholderView },
    { path: '/review', name: 'review', component: PlaceholderView },
    { path: '/task', name: 'task', component: PlaceholderView },
    { path: '/:pathMatch(.*)*', redirect: '/strategy' },
  ],
})

export default router

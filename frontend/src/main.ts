/**
 * 应用入口：挂载路由与状态管理，并在生产环境注册断网外壳
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import './styles/theme.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')

// 只在生产注册：开发期注册会缓存住 Vite 的热更新资源
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register(`${import.meta.env.BASE_URL}sw.js`).catch(() => {
      // 注册失败只是少了离线外壳，页面本身照常可用
    })
  })
}

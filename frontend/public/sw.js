/**
 * 断网外壳：缓存构建产物与页面导航请求，断网刷新时不白屏
 */
const CACHE_NAME = 'fund-shell-v1'

/** 只需要在安装时预缓存的入口，其余静态资源在首次访问时按需缓存 */
const PRECACHE = ['/', '/index.html']

/** 只缓存同源的 GET，且不碰 /api，接口结果由页面自己的 localStorage 快照承接 */
function cacheable(request) {
  const url = new URL(request.url)
  return request.method === 'GET' && url.origin === self.location.origin && !url.pathname.startsWith('/api')
}

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) => cache.addAll(PRECACHE))
      .catch(() => undefined)
      .then(() => self.skipWaiting()),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const { request } = event
  if (!cacheable(request)) {
    return
  }
  // 导航请求走「网络优先、失败回退缓存」，保证有网时拿到最新页面
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone()
          caches.open(CACHE_NAME).then((cache) => cache.put('/index.html', copy))
          return response
        })
        .catch(() => caches.match('/index.html').then((cached) => cached ?? Response.error())),
    )
    return
  }
  // 静态资源走「缓存优先」，Vite 产物带内容哈希，更新时文件名会变
  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) {
        return cached
      }
      return fetch(request).then((response) => {
        if (response.ok) {
          const copy = response.clone()
          caches.open(CACHE_NAME).then((cache) => cache.put(request, copy))
        }
        return response
      })
    }),
  )
})

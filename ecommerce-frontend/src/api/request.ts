import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// ===== 静默刷新状态（多个请求同时 401 时只发一次刷新） =====
let isRefreshing = false
let refreshWaiters: ((token: string) => void)[] = []

// Request interceptor — attach token
request.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// Response interceptor — unify business errors & handle 401 with silent refresh
request.interceptors.response.use(
  (response) => {
    // 后端业务错误约定：HTTP 200 + body.code != 200（如用户名已存在、密码错误）
    const res = response.data
    if (res && typeof res === 'object' && 'code' in res && res.code !== 200) {
      const err = new Error(res.message || '请求失败') as any
      err.code = res.code
      err.response = { data: res }
      return Promise.reject(err)
    }
    return response
  },
  async (error) => {
    const authStore = useAuthStore()
    const status = error.response?.status

    // 非 401 错误：直接带中文消息 reject
    if (status !== 401) {
      const err = new Error(error.response?.data?.message || error.message || '网络错误') as any
      err.code = status
      err.response = error.response
      return Promise.reject(err)
    }

    // 401 处理：排除登录/刷新接口自身（避免死循环）
    const url: string = error.config?.url || ''
    if (url.includes('/auth/refresh') || url.includes('/auth/login')) {
      authStore.logout()
      router.push('/login')
      return Promise.reject(error)
    }

    // 没有 refreshToken：直接登出
    if (!authStore.refreshToken) {
      authStore.logout()
      router.push('/login')
      return Promise.reject(error)
    }

    // 已有刷新在途：排队等待新 token 后重放
    if (isRefreshing) {
      return new Promise((resolve) => {
        refreshWaiters.push((newToken) => {
          error.config.headers.Authorization = `Bearer ${newToken}`
          resolve(request(error.config))
        })
      })
    }

    // 发起刷新（并发锁：只允许一个刷新请求）
    isRefreshing = true
    try {
      const res = await request.post('/auth/refresh', { refreshToken: authStore.refreshToken })
      const data = res.data.data
      authStore.persistToken(data)

      // 唤醒所有排队请求
      refreshWaiters.forEach((w) => w(data.accessToken))
      refreshWaiters = []

      // 用新 token 重放当前请求
      error.config.headers.Authorization = `Bearer ${data.accessToken}`
      return request(error.config)
    } catch (refreshError) {
      // 刷新也失败（refresh token 过期/无效）：登出
      refreshWaiters = []
      authStore.logout()
      router.push('/login')
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  },
)

export default request

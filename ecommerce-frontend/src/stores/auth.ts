import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, getUserInfo } from '@/api/auth'
import type { LoginParams, LoginVO, UserInfoVO } from '@/api/auth'

const TOKEN_KEY = 'ecommerce_token'
const REFRESH_TOKEN_KEY = 'ecommerce_refresh_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const refreshToken = ref<string>(localStorage.getItem(REFRESH_TOKEN_KEY) || '')
  const userInfo = ref<UserInfoVO | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  function persistToken(loginVO: LoginVO) {
    token.value = loginVO.accessToken
    refreshToken.value = loginVO.refreshToken
    localStorage.setItem(TOKEN_KEY, loginVO.accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, loginVO.refreshToken)
  }

  function clearToken() {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  }

  async function loginAction(params: LoginParams): Promise<LoginVO> {
    const result = await loginApi(params)
    persistToken(result)
    // 登录成功后拉取用户信息（失败不阻塞登录流程）
    fetchUserInfo().catch(() => {})
    return result
  }

  async function fetchUserInfo() {
    const info = await getUserInfo()
    userInfo.value = info
  }

  function logout() {
    clearToken()
  }

  return {
    token,
    refreshToken,
    userInfo,
    isLoggedIn,
    login: loginAction,
    fetchUserInfo,
    persistToken,
    logout,
  }
})

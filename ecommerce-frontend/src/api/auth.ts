import request from './request'

export interface LoginParams {
  username: string
  password: string
}

export interface LoginVO {
  accessToken: string
  refreshToken: string
  expiresIn: number
  loginCount: number
}

export interface UserInfoVO {
  id: number
  username: string
  phone: string
  email: string | null
  avatar: string | null
  status: number
  roles: string[]
  createdAt: string
  loginCount: number
}

export interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

export interface RegisterParams {
  username: string
  password: string
  phone: string
}

export async function login(params: LoginParams): Promise<LoginVO> {
  const res = await request.post<ApiResult<LoginVO>>('/auth/login', params)
  return res.data.data
}

export async function register(params: RegisterParams): Promise<LoginVO> {
  const res = await request.post<ApiResult<LoginVO>>('/auth/register', params)
  return res.data.data
}

export async function getUserInfo(): Promise<UserInfoVO> {
  const res = await request.get<ApiResult<UserInfoVO>>('/auth/me')
  return res.data.data
}

/** 用刷新令牌换取新的 accessToken + refreshToken */
export async function refreshTokenApi(refreshToken: string): Promise<LoginVO> {
  const res = await request.post<ApiResult<LoginVO>>('/auth/refresh', { refreshToken })
  return res.data.data
}

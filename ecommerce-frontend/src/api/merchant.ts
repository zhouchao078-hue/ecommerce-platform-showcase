import request from './request'

export interface MerchantStatus {
  userId: number
  status: number
  statusDesc: string
}

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export async function getMerchantStatus(): Promise<MerchantStatus> {
  const res = await request.get<ApiResult<MerchantStatus>>('/merchant/status')
  return res.data.data
}

export async function applyMerchant(data: { shopName: string; description: string }): Promise<void> {
  await request.post('/merchant/apply', data)
}

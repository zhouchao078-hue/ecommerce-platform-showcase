import request from './request'
import type { ApiResult } from './auth'

export interface AfterSale {
  id: number
  afterSaleNo: string
  orderId: number
  orderItemId: number
  userId: number
  merchantId: number
  type: number
  reason: string
  description?: string
  amount: string | number
  status: number
  rejectReason?: string
  returnCompany?: string
  returnTrackingNo?: string
  returnedAt?: string
  receivedAt?: string
  appliedAt: string
  handledAt?: string
}

/** 售后状态文案，页面直接用 */
export const AFTER_SALE_STATUS: Record<number, string> = {
  0: '待商家处理',
  1: '待买家寄回',
  2: '商家已拒绝',
  3: '退款完成',
  4: '已撤销',
  5: '待商家收货',
}

export async function applyAfterSale(body: {
  orderItemId: number
  type: number
  reason: string
  description?: string
  amount?: number
}): Promise<AfterSale> {
  const res = await request.post<ApiResult<AfterSale>>('/after-sales', body)
  return res.data.data
}

export async function listMyAfterSales(): Promise<AfterSale[]> {
  const res = await request.get<ApiResult<AfterSale[]>>('/after-sales')
  return res.data.data
}

export async function cancelAfterSale(id: number): Promise<void> {
  await request.put(`/after-sales/${id}/cancel`)
}

export async function submitAfterSaleReturn(
  id: number,
  body: { returnCompany: string; returnTrackingNo: string },
): Promise<void> {
  await request.put(`/after-sales/${id}/return`, body)
}

interface PageResult<T> {
  records: T[]
  total: number
}

export async function listMerchantAfterSales(params: {
  status?: number | null
  pageNum?: number
  pageSize?: number
}): Promise<PageResult<AfterSale>> {
  const res = await request.get<ApiResult<PageResult<AfterSale>>>('/merchant/after-sales', { params })
  return res.data.data
}

export async function approveAfterSale(id: number): Promise<void> {
  await request.put(`/merchant/after-sales/${id}/approve`)
}

export async function rejectAfterSale(id: number, reason: string): Promise<void> {
  await request.put(`/merchant/after-sales/${id}/reject`, { reason })
}

export async function receiveAfterSaleReturn(id: number): Promise<void> {
  await request.put(`/merchant/after-sales/${id}/receive`)
}

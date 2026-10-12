import request from './request'
import type { ApiResult } from './auth'

export interface MerchantOrder {
  id: number
  orderNo: string
  userId: number
  totalAmount: string | number
  status: number
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  remark?: string
  createTime: string
}

interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

/** 我店铺的订单（支持状态筛选、分页） */
export async function listMerchantOrders(params: {
  status?: number | null
  pageNum?: number
  pageSize?: number
}): Promise<PageResult<MerchantOrder>> {
  const res = await request.get<ApiResult<PageResult<MerchantOrder>>>('/merchant/orders', { params })
  return res.data.data
}

/** 发货：待发货 → 待收货 */
export async function shipOrder(id: number): Promise<void> {
  await request.put(`/merchant/orders/${id}/ship`)
}

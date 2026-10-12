import request from './request'
import type { ApiResult } from './auth'

export interface MerchantRecentOrder {
  id: number
  orderNo: string
  buyerName: string
  totalAmount: string | number
  status: number
  createTime: string
}

export interface MerchantDashboard {
  totalSales: string | number
  todaySales: string | number
  productTotal: number
  productOnSale: number
  totalOrders: number
  pendingShip: number
  pendingReceive: number
  completedOrders: number
  pendingAfterSales: number
  returnWaitingReceipt: number
  followerCount: number
  recentOrders: MerchantRecentOrder[]
}

export async function getMerchantDashboard(): Promise<MerchantDashboard> {
  const res = await request.get<ApiResult<MerchantDashboard>>('/merchant/dashboard')
  return res.data.data
}

import request from './request'
import type { ApiResult } from './auth'

export interface PaymentVO {
  paymentNo: string
  orderId: number
  amount: string | number
  status: number
  statusDesc: string
  paidAt?: string
}

/** 创建支付单（同一订单未支付的会复用） */
export async function createPayment(orderId: number, channel = 1): Promise<PaymentVO> {
  const res = await request.post<ApiResult<PaymentVO>>('/payments', { orderId, channel })
  return res.data.data
}

/** 模拟支付成功（真实项目里这一步是第三方回调） */
export async function payPayment(paymentNo: string): Promise<PaymentVO> {
  const res = await request.post<ApiResult<PaymentVO>>(`/payments/${paymentNo}/pay`)
  return res.data.data
}

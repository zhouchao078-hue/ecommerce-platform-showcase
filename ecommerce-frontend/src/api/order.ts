import request from './request'

export interface OrderDetailVO {
  id: number
  orderNo: string
  totalAmount: number
  status: number
  statusDesc: string
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  remark: string | null
  createTime: string
  items: OrderItemVO[]
  subOrders?: SubOrderVO[]
  parentOrderId?: number | null
  merchantId?: number | null
  orderType?: 'NORMAL' | 'PARENT' | 'CHILD' | 'SECKILL'
}

export interface SubOrderVO {
  id: number
  orderNo: string
  merchantId: number
  totalAmount: number
  status: number
  statusDesc: string
  items: OrderItemVO[]
}

export interface OrderItemVO {
  id: number
  productId: number
  productName: string
  skuId?: number | null
  skuName?: string | null
  productPrice: number
  productImage: string | null
  quantity: number
  subtotal: number
}

interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

export async function getOrderDetail(id: number): Promise<OrderDetailVO> {
  const res = await request.get<ApiResult<OrderDetailVO>>('/orders/' + id)
  return res.data.data
}

export async function getSeckillOrderDetail(orderNo: string): Promise<OrderDetailVO> {
  const res = await request.get<ApiResult<OrderDetailVO>>('/orders/seckill/' + encodeURIComponent(orderNo))
  return res.data.data
}

export async function cancelOrder(id: number): Promise<void> {
  await request.put('/orders/' + id + '/cancel')
}

/** 模拟支付（0→1） */
export async function payOrder(id: number): Promise<void> {
  await request.post('/orders/' + id + '/pay')
}

/** 确认收货（2→3） */
export async function confirmOrder(id: number): Promise<void> {
  await request.put('/orders/' + id + '/confirm')
}

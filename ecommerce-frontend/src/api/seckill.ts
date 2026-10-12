import request from './request'

export interface SeckillProduct {
  id: number
  productId: number
  name: string
  description: string
  images: string
  originalPrice: number
  seckillPrice: number
  stock: number
  startTime: string
  endTime: string
}

export interface CreateSeckillActivity {
  productId: number
  seckillPrice: number
  stock: number
  startTime: string
  endTime: string
}

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export async function listSeckillProducts(): Promise<SeckillProduct[]> {
  const response = await request.get<ApiResult<SeckillProduct[]>>('/seckill/products')
  return response.data.data || []
}

export async function createSeckillActivity(data: CreateSeckillActivity): Promise<void> {
  await request.post('/seckill/products', data)
}

export async function purchaseSeckillProduct(id: number): Promise<{ orderNo: string }> {
  const response = await request.post<ApiResult<{ orderNo: string }>>(`/seckill/${id}/purchase`)
  return response.data.data
}

import request from './request'
import type { ApiResult } from './auth'
import type { Product } from './product'

export interface Shop {
  id: number
  shopName: string
  description?: string
  productCount: number
  followerCount: number
  following: boolean
}

/** 商家主页信息（公开接口，未登录也能看） */
export async function getShop(id: number): Promise<Shop> {
  const res = await request.get<ApiResult<Shop>>('/shops/' + id)
  return res.data.data
}

/** 商家在售商品列表 */
export async function getShopProducts(id: number, pageNum = 1, pageSize = 12): Promise<Product[]> {
  const res = await request.get<ApiResult<Product[]>>(`/shops/${id}/products`, {
    params: { pageNum, pageSize },
  })
  return res.data.data
}

export async function followMerchant(id: number): Promise<void> {
  await request.post(`/follows/merchant/${id}`)
}

export async function unfollowMerchant(id: number): Promise<void> {
  await request.delete(`/follows/merchant/${id}`)
}

export async function followProduct(id: number): Promise<void> {
  await request.post(`/follows/product/${id}`)
}

export async function unfollowProduct(id: number): Promise<void> {
  await request.delete(`/follows/product/${id}`)
}

/** 我关注的店铺 */
export async function getFollowedShops(): Promise<Shop[]> {
  const res = await request.get<ApiResult<Shop[]>>('/follows/merchants')
  return res.data.data
}

/** 我关注的商品（已下架的不返回） */
export async function getFollowedProducts(): Promise<Product[]> {
  const res = await request.get<ApiResult<Product[]>>('/follows/products')
  return res.data.data
}

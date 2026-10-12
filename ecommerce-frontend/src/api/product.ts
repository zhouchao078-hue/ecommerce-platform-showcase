import request from './request'

// ======================== Types ========================

export interface Product {
  id: number
  merchantId: number
  categoryId: number | null
  name: string
  description: string
  price: number
  stock: number
  images: string
  status: number
  createTime: string
  updateTime: string
  skus?: ProductSku[]
}

export interface ProductCard {
  id: number
  name: string
  price: number
  stock: number
  image: string | null
  merchantId: number
  categoryId: number | null
  description: string | null
}

export interface ProductSku {
  id?: number
  productId?: number
  skuName: string
  specJson?: string
  price: number
  stock: number
  image?: string
  isDefault?: number
  status?: number
}

/** 上传商品图片到 MinIO，并返回可保存到商品记录的图片地址 */
export async function uploadProductImage(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await request.post<ApiResult<{ url: string }>>('/files/images/upload', formData)
  return res.data.data.url
}

export async function searchProducts(params: {
  keyword: string
  pageNum: number
  pageSize: number
}): Promise<Product[]> {
  const res = await request.get<ApiResult<Product[]>>('/products/search', { params })
  return res.data.data
}

export interface Category {
  id: number
  name: string
  parentId: number | null
  sortOrder: number
  merchantId: number
  createTime: string
  updateTime: string
}

export interface CartVO {
  id: number
  userId: number
  productId: number
  skuId: number
  skuName?: string
  quantity: number
  productName: string
  productPrice: number
  productImage: string
  createTime: string
  updateTime: string
}

interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

// ======================== Products ========================

export async function getProducts(params: {
  categoryId?: number | null
  pageNum: number
  pageSize: number
}): Promise<Product[]> {
  const res = await request.get<ApiResult<Product[]>>('/products/public', { params })
  return res.data.data
}

// ======================== 商家商品管理 ========================

/** 当前商家的商品列表（需商家身份） */
export async function listMerchantProducts(params: {
  categoryId?: number | null
  pageNum: number
  pageSize: number
}): Promise<Product[]> {
  const res = await request.get<ApiResult<Product[]>>('/products', { params })
  return res.data.data
}

/** 新增商品（status 默认下架，由后端设置） */
export async function addProduct(data: {
  name: string
  price: number
  stock: number
  description?: string
  images?: string
  categoryId?: number | null
  skus?: ProductSku[]
}): Promise<void> {
  await request.post('/products', data)
}

/** 编辑商品 */
export async function updateProduct(id: number, data: {
  name?: string
  price?: number
  stock?: number
  description?: string
  images?: string
  categoryId?: number | null
  skus?: ProductSku[]
}): Promise<void> {
  await request.put('/products/' + id, data)
}

/** 删除商品 */
export async function deleteProduct(id: number): Promise<void> {
  await request.delete('/products/' + id)
}

/** 商品上下架（0=下架 1=上架） */
export async function updateProductStatus(id: number, status: 0 | 1): Promise<void> {
  await request.put('/products/' + id + '/status', { status })
}

/** 当前商家的分类列表（用于下拉选择） */
export async function listMerchantCategories(): Promise<Category[]> {
  const res = await request.get<ApiResult<Category[]>>('/categories')
  return res.data.data
}

// ======================== Categories ========================

export async function getCategories(): Promise<Category[]> {
  const res = await request.get<ApiResult<Category[]>>('/categories/public')
  return res.data.data
}

// ======================== Cart ========================

export async function addToCart(data: {
  productId: number
  skuId?: number
  quantity?: number
}): Promise<CartVO> {
  const res = await request.post<ApiResult<CartVO>>('/cart', {
    productId: data.productId,
    skuId: data.skuId,
    quantity: data.quantity ?? 1,
  })
  return res.data.data
}

export async function getCart(pageNum = 1, pageSize = 100): Promise<CartVO[]> {
  const res = await request.get<ApiResult<{ records: CartVO[] }>>('/cart', {
    params: { pageNum, pageSize }
  })
  return res.data.data.records
}

export async function updateCartQuantity(id: number, quantity: number): Promise<void> {
  await request.put(`/cart/${id}?quantity=${quantity}`)
}

export async function deleteCartItem(id: number): Promise<void> {
  await request.delete(`/cart/${id}`)
}


export interface CreateOrderDTO {
  cartItemIds: number[]
  addressId: number
  remark?: string
}

export async function createOrder(data: CreateOrderDTO): Promise<void> {
  await request.post<ApiResult<any>>("/orders", data)
}
export async function getProductById(id: number): Promise<Product> {
  const res = await request.get<ApiResult<Product>>('/products/' + id)
  return res.data.data
}

import request from './request'

export interface Address {
  id: number
  userId: number
  receiverName: string
  phone: string
  province: string
  city: string
  district: string
  detailAddress: string
  isDefault: number
  createTime: string
  updateTime: string
}

interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

export async function getAddressList(): Promise<Address[]> {
  const res = await request.get<ApiResult<Address[]>>('/user/address/list')
  return res.data.data
}

export interface AddAddressDTO {
  receiverName: string
  phone: string
  province: string
  city: string
  district: string
  detailAddress: string
}

export async function addAddress(data: AddAddressDTO): Promise<void> {
  await request.post("/user/address", data)
}

export async function deleteAddress(id: number): Promise<void> {
  await request.delete("/user/address/" + id)
}
export async function setDefaultAddress(id: number): Promise<void> {
  await request.put('/user/address/' + id + '/default')
}

export async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  await request.put("/auth/change-password", { oldPassword, newPassword })
}

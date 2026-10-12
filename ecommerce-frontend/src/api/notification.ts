import request from './request'
import type { ApiResult } from './auth'

export interface NotificationItem {
  id: number
  type: number
  title: string
  content?: string
  refType?: number
  refId?: number
  isRead: number
  createTime: string
}

export async function listNotifications(limit = 50): Promise<NotificationItem[]> {
  const res = await request.get<ApiResult<NotificationItem[]>>('/notifications', { params: { limit } })
  return res.data.data
}

export async function unreadCount(): Promise<number> {
  const res = await request.get<ApiResult<{ count: number }>>('/notifications/unread-count')
  return res.data.data?.count ?? 0
}

export async function markRead(id: number): Promise<void> {
  await request.put(`/notifications/${id}/read`)
}

export async function markAllRead(): Promise<void> {
  await request.put('/notifications/read-all')
}

/** 通知点进去跳哪里：以后端给的 refType/refId 决定，而不是存 URL（路由改了不会变死链） */
export function targetOf(n: NotificationItem): string | null {
  if (n.refType === 1 && n.refId) return `/orders/${n.refId}`
  if (n.refType === 2 && n.refId) return `/products/${n.refId}`
  if (n.refType === 3 && n.refId) return `/shops/${n.refId}`
  return null
}

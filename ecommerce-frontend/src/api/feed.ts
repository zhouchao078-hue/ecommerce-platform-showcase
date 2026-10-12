import request from './request'

export interface FeedPost {
  id: number
  userId: number
  username: string
  content: string
  createTime: string
}

interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

/** 发布动态 */
export async function publishPost(content: string): Promise<void> {
  await request.post('/feed', { content })
}

/** 广场动态列表（分页，按时间倒序） */
export async function getFeed(pageNum = 1, pageSize = 20): Promise<FeedPost[]> {
  const res = await request.get<ApiResult<{ records: FeedPost[] }>>('/feed', {
    params: { pageNum, pageSize },
  })
  return res.data.data.records
}

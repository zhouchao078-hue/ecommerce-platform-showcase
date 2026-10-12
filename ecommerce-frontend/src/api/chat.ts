import request from './request'

export interface ChatTraceItem {
  id: number
  message: string
  intent: string
  routeAgent: string
  steps: string
  reply: string
  durationMs: number
  createTime: string
}

interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

export interface TraceStep {
  round: number
  calls: { tool: string; args: string; result: string; ok: boolean }[]
}

/** 解析轨迹中的 steps JSON */
export function parseSteps(raw?: string): TraceStep[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

/** 查询当前用户的智能体思考轨迹（倒序） */
export async function getChatTraces(limit = 20): Promise<ChatTraceItem[]> {
  const res = await request.get<ApiResult<ChatTraceItem[]>>('/chat/trace', {
    params: { limit },
  })
  return res.data.data
}

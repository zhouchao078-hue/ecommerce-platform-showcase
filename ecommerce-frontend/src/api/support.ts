import { fetchEventSource } from '@microsoft/fetch-event-source'
import request from './request'

interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

export interface SupportTicket {
  ticketNo: string
  subject: string
  summary: string | null
  category: string
  priority: string
  status: number
  statusDesc: string
  lastMessageAt: string | null
  createTime: string
}

export interface SupportMessage {
  id: number
  senderType: 'USER' | 'AGENT' | 'MERCHANT' | 'HUMAN' | 'SYSTEM'
  messageType: string
  content: string
  visibility: string
  createTime: string
}

export interface SupportTicketDetail {
  ticket: SupportTicket
  messages: SupportMessage[]
}

export interface SupportTicketPage {
  records: SupportTicket[]
  total: number
  size: number
  current: number
  pages: number
}

export interface AgentRunAccepted {
  runId: number
  runNo: string
  ticketNo: string
  status: string
  reused: boolean
}

export interface AgentRun {
  runId: number
  runNo: string
  ticketNo: string
  status: string
  cancelRequested: boolean
  lastCompletedStep: string | null
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
  durationMs: number | null
}

export interface AgentRunEvent {
  eventId?: number
  eventType?: string
  runId: number
  runNo: string
  status: string
  lastCompletedStep: string | null
  workerId: string | null
  leaseExpiresAt: string | null
  lastHeartbeatAt: string | null
  errorMessage: string | null
  occurredAt: string
}

export async function listSupportTickets(pageNum = 1, pageSize = 20): Promise<SupportTicketPage> {
  const res = await request.get<ApiResult<SupportTicketPage>>('/support/tickets', {
    params: { pageNum, pageSize },
  })
  return res.data.data
}

export async function getSupportTicket(ticketNo: string): Promise<SupportTicketDetail> {
  const res = await request.get<ApiResult<SupportTicketDetail>>(
    `/support/tickets/${encodeURIComponent(ticketNo)}`,
  )
  return res.data.data
}

export async function createSupportTicket(data: {
  subject: string
  content: string
  externalTicketId: string
  externalMessageId: string
}): Promise<AgentRunAccepted> {
  const res = await request.post<ApiResult<AgentRunAccepted>>('/ticket-agent/tickets', data)
  return res.data.data
}

export async function sendSupportMessage(ticketNo: string, content: string): Promise<AgentRunAccepted> {
  const res = await request.post<ApiResult<AgentRunAccepted>>(
    `/ticket-agent/tickets/${encodeURIComponent(ticketNo)}/messages`,
    {
    content,
    externalMessageId: crypto.randomUUID(),
    },
  )
  return res.data.data
}

export async function getAgentRun(runId: number): Promise<AgentRun> {
  const res = await request.get<ApiResult<AgentRun>>(`/ticket-agent/runs/${runId}`)
  return res.data.data
}

export async function cancelAgentRun(runId: number): Promise<AgentRun> {
  const res = await request.post<ApiResult<AgentRun>>(`/ticket-agent/runs/${runId}/cancel`)
  return res.data.data
}

export async function streamAgentRun(
  runId: number,
  token: string,
  lastEventId: number,
  onEvent: (event: AgentRunEvent) => void,
  signal: AbortSignal,
): Promise<void> {
  const suffix = lastEventId > 0 ? `?lastEventId=${lastEventId}` : ''
  await fetchEventSource(`/api/ticket-agent/runs/${runId}/events${suffix}`, {
    headers: {
      Accept: 'text/event-stream',
      Authorization: `Bearer ${token}`,
    },
    signal,
    openWhenHidden: true,
    onmessage(message) {
      if (message.event === 'run.status' && message.data) {
        const event = JSON.parse(message.data) as AgentRunEvent
        if (message.id) {
          event.eventId = Number(message.id)
        }
        onEvent(event)
      }
    },
    onclose() {
      throw new Error('ticket agent event stream closed')
    },
  })
}

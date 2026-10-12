<template>
  <AppLayout>
    <div class="detail-page">
      <button type="button" class="back-link" @click="router.push('/support/tickets')">
        <el-icon><ArrowLeft /></el-icon>
        返回我的工单
      </button>

      <div v-if="loading" class="detail-loading">
        <el-skeleton :rows="7" animated />
      </div>

      <template v-else-if="detail">
        <header class="ticket-header">
          <div>
            <p class="ticket-no">{{ detail.ticket.ticketNo }}</p>
            <h1>{{ detail.ticket.subject || '客服工单' }}</h1>
          </div>
          <el-tag :type="statusType(detail.ticket.status)" effect="plain">
            {{ detail.ticket.statusDesc }}
          </el-tag>
        </header>

        <section class="ticket-info">
          <span>{{ categoryLabel(detail.ticket.category) }}</span>
          <span>创建于 {{ formatTime(detail.ticket.createTime) }}</span>
        </section>

        <section v-if="processing" class="run-status">
          <span>{{ runStatusText }}</span>
          <el-button type="danger" plain size="small" @click="cancelActiveRun">取消生成</el-button>
        </section>

        <section class="message-list">
          <article
            v-for="message in detail.messages"
            :key="message.id"
            class="message-item"
            :class="message.senderType === 'USER' ? 'message-user' : 'message-agent'"
          >
            <div class="message-avatar">{{ senderLabel(message.senderType).slice(0, 1) }}</div>
            <div class="message-content">
              <div class="message-meta">
                <strong>{{ senderLabel(message.senderType) }}</strong>
                <span>{{ formatTime(message.createTime) }}</span>
              </div>
              <p>{{ message.content }}</p>
            </div>
          </article>
        </section>

        <section class="reply-box">
          <el-input
            v-model="replyContent"
            type="textarea"
            :rows="4"
            maxlength="5000"
            show-word-limit
            resize="none"
            placeholder="补充订单号、问题描述或需要客服继续处理的内容"
          />
          <div class="reply-actions">
            <span>提交后会继续进入智能客服分析。</span>
            <el-button
              type="primary"
              :loading="sending"
              :disabled="!replyContent.trim() || sending"
              @click="submitReply"
            >
              发送并继续处理
            </el-button>
          </div>
        </section>
      </template>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import AppLayout from '@/layouts/AppLayout.vue'
import {
  cancelAgentRun,
  getAgentRun,
  getSupportTicket,
  sendSupportMessage,
  streamAgentRun,
  type AgentRunEvent,
  type SupportTicketDetail,
} from '@/api/support'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(true)
const sending = ref(false)
const replyContent = ref('')
const detail = ref<SupportTicketDetail | null>(null)
const activeRunId = ref<number | null>(null)
const runStatus = ref('')
let pollTimer: ReturnType<typeof setTimeout> | null = null
let pollAttempt = 0
let streamAbort: AbortController | null = null
let streamRetryTimer: ReturnType<typeof setTimeout> | null = null
let streamRetryAttempt = 0
let streamCursor = 0

const processing = computed(() => activeRunId.value !== null)
const runStatusText = computed(() => {
  const labels: Record<string, string> = {
    PENDING: '智能客服正在准备上下文',
    RUNNING: '智能客服正在生成处理方案',
    CANCEL_REQUESTED: '正在取消生成',
  }
  return labels[runStatus.value] || '智能客服正在处理'
})

const categoryMap: Record<string, string> = {
  general: '其他问题',
  order: '订单问题',
  logistics: '物流问题',
  after_sale: '售后问题',
}

function categoryLabel(category: string) {
  return categoryMap[category] || category || '其他问题'
}

function statusType(status: number) {
  if (status >= 70) return 'success'
  if (status === 60) return 'danger'
  if (status >= 40) return 'warning'
  return 'info'
}

function senderLabel(senderType: string) {
  return {
    USER: '我',
    AGENT: '客服助手',
    MERCHANT: '商家客服',
    HUMAN: '人工客服',
    SYSTEM: '系统',
  }[senderType] || senderType
}

function formatTime(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

async function fetchDetail() {
  loading.value = true
  try {
    detail.value = await getSupportTicket(String(route.params.ticketNo))
  } catch (error: any) {
    ElMessage.error(error?.message || '工单详情加载失败')
  } finally {
    loading.value = false
  }
}

async function submitReply() {
  const content = replyContent.value.trim()
  if (!content || !detail.value) return
  sending.value = true
  try {
    const run = await sendSupportMessage(detail.value.ticket.ticketNo, content)
    replyContent.value = ''
    detail.value = await getSupportTicket(detail.value.ticket.ticketNo)
    startRunMonitoring(run.runId)
  } catch (error: any) {
    ElMessage.error(error?.message || '消息发送失败')
    sending.value = false
  }
}

async function cancelActiveRun() {
  if (!activeRunId.value) return
  try {
    const run = await cancelAgentRun(activeRunId.value)
    runStatus.value = run.status
    schedulePoll(activeRunId.value)
  } catch (error: any) {
    ElMessage.error(error?.message || '取消失败')
  }
}

function startRunMonitoring(runId: number) {
  activeRunId.value = runId
  runStatus.value = 'PENDING'
  sending.value = true
  pollAttempt = 0
  streamCursor = 0
  streamRetryAttempt = 0
  connectRunStream(runId)
}

function connectRunStream(runId: number) {
  streamAbort?.abort()
  const controller = new AbortController()
  streamAbort = controller
  streamAgentRun(
    runId,
    authStore.token || '',
    streamCursor,
    event => {
      streamRetryAttempt = 0
      if (event.eventId) {
        streamCursor = event.eventId
      }
      handleRunEvent(runId, event)
    },
    controller.signal,
  )
    .then(() => retryRunStream(runId, controller))
    .catch(() => retryRunStream(runId, controller))
}

function retryRunStream(runId: number, controller: AbortController) {
  if (controller.signal.aborted || activeRunId.value !== runId) {
    return
  }
  if (streamRetryAttempt >= 3) {
    schedulePoll(runId)
    return
  }
  const delay = Math.min(5000, Math.round(600 * Math.pow(1.6, streamRetryAttempt)))
  streamRetryAttempt += 1
  if (streamRetryTimer) clearTimeout(streamRetryTimer)
  streamRetryTimer = setTimeout(() => connectRunStream(runId), delay)
}

function schedulePoll(runId: number) {
  if (pollTimer) clearTimeout(pollTimer)
  const delay = Math.min(3000, Math.round(700 * Math.pow(1.35, pollAttempt)))
  const jitter = Math.floor(Math.random() * 250)
  pollAttempt += 1
  pollTimer = setTimeout(() => pollRun(runId), delay + jitter)
}

async function pollRun(runId: number) {
  if (activeRunId.value !== runId) return
  try {
    const run = await getAgentRun(runId)
    if (handleRunStatus(run.status, run.errorMessage)) {
      return
    }
    schedulePoll(runId)
  } catch (error: any) {
    activeRunId.value = null
    sending.value = false
    ElMessage.error(error?.message || '运行状态查询失败')
  }
}

function handleRunEvent(runId: number, event: AgentRunEvent) {
  if (activeRunId.value !== runId) return
  if (handleRunStatus(event.status, event.errorMessage)) {
    streamAbort?.abort()
  }
}

function handleRunStatus(status: string, errorMessage: string | null): boolean {
  runStatus.value = status
  if (!['SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT'].includes(status)) {
    return false
  }
  activeRunId.value = null
  sending.value = false
  if (status === 'SUCCEEDED') {
    ElMessage.success('智能客服已生成新的处理回复')
  } else if (status === 'CANCELLED') {
    ElMessage.info('已取消本次生成')
  } else {
    ElMessage.error(errorMessage || '智能客服处理失败')
  }
  fetchDetail().catch(() => {})
  return true
}

onMounted(async () => {
  await fetchDetail()
  const runId = Number(route.query.runId)
  if (Number.isFinite(runId) && runId > 0) {
    startRunMonitoring(runId)
  }
})

onUnmounted(() => {
  if (pollTimer) clearTimeout(pollTimer)
  if (streamRetryTimer) clearTimeout(streamRetryTimer)
  streamAbort?.abort()
})
</script>

<style scoped>
.detail-page {
  width: 100%;
  max-width: 820px;
  margin: 0 auto;
  padding: 36px 24px 88px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 24px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 14px;
  cursor: pointer;
}

.back-link:hover {
  color: var(--color-text-primary);
}

.detail-loading,
.ticket-header,
.ticket-info,
.message-list {
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
}

.run-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 12px;
  padding: 14px 20px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
  color: var(--color-text-secondary);
  font-size: 13px;
}

.detail-loading {
  padding: 24px;
}

.ticket-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 24px;
}

.ticket-header h1 {
  margin: 6px 0 0;
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.ticket-no {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
}

.ticket-info {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 24px;
  margin-top: 12px;
  padding: 14px 24px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.message-list {
  display: grid;
  gap: 0;
  margin-top: 20px;
  padding: 6px 24px;
}

.message-item {
  display: flex;
  gap: 14px;
  padding: 20px 0;
  border-bottom: 1px solid var(--color-border-subtle);
}

.message-item:last-child {
  border-bottom: 0;
}

.message-avatar {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--color-accent-dim);
  color: var(--color-accent-dark);
  font-size: 13px;
  font-weight: 600;
}

.message-user .message-avatar {
  background: var(--color-bg-inverted);
  color: var(--color-bg-overlay);
}

.message-content {
  min-width: 0;
  flex: 1;
}

.message-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.message-meta strong {
  color: var(--color-text-primary);
  font-size: 13px;
}

.message-content p {
  margin: 9px 0 0;
  color: var(--color-text-secondary);
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.reply-box {
  margin-top: 20px;
  padding: 20px 24px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
}

.reply-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
}

.reply-actions span {
  color: var(--color-text-muted);
  font-size: 12px;
}

@media (max-width: 640px) {
  .reply-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>

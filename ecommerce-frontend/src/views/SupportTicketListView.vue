<template>
  <AppLayout>
    <div class="support-page">
      <div class="support-header">
        <div>
          <h2 class="page-title">我的工单</h2>
          <p class="page-subtitle">查看客服处理进度和智能体回复</p>
        </div>
        <el-button :loading="loading" @click="fetchTickets">刷新</el-button>
      </div>

      <div v-if="loading" class="support-loading">
        <el-skeleton :rows="5" animated />
      </div>
      <el-empty v-else-if="tickets.length === 0" description="暂无服务工单" />

      <div v-else class="ticket-list">
        <button
          v-for="ticket in tickets"
          :key="ticket.ticketNo"
          type="button"
          class="ticket-card"
          @click="openTicket(ticket)"
        >
          <div class="ticket-main">
            <div class="ticket-title-row">
              <strong>{{ ticket.subject || '客服工单' }}</strong>
              <el-tag :type="statusType(ticket.status)" effect="plain" size="small">
                {{ ticket.statusDesc }}
              </el-tag>
            </div>
            <p class="ticket-summary">{{ ticket.summary || '客服正在整理处理进展。' }}</p>
            <div class="ticket-meta">
              <span>{{ categoryLabel(ticket.category) }}</span>
              <span>{{ ticket.ticketNo }}</span>
              <span>{{ formatTime(ticket.lastMessageAt || ticket.createTime) }}</span>
            </div>
          </div>
          <el-icon class="ticket-arrow"><ArrowRight /></el-icon>
        </button>
      </div>

      <el-pagination
        v-if="total > pageSize"
        v-model:current-page="pageNum"
        class="support-pagination"
        layout="prev, pager, next"
        :page-size="pageSize"
        :total="total"
        @current-change="fetchTickets"
      />
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { listSupportTickets, type SupportTicket } from '@/api/support'

const router = useRouter()
const loading = ref(false)
const tickets = ref<SupportTicket[]>([])
const pageNum = ref(1)
const pageSize = 20
const total = ref(0)

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
  if (status === 50) return 'warning'
  if (status === 40) return 'warning'
  return 'info'
}

function formatTime(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

function openTicket(ticket: SupportTicket) {
  router.push(`/support/tickets/${encodeURIComponent(ticket.ticketNo)}`)
}

async function fetchTickets() {
  loading.value = true
  try {
    const page = await listSupportTickets(pageNum.value, pageSize)
    tickets.value = page.records || []
    total.value = Number(page.total || 0)
  } catch (error: any) {
    ElMessage.error(error?.message || '工单加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(fetchTickets)
</script>

<style scoped>
.support-page {
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 24px 88px;
}

.support-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 28px;
}

.page-title {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-text-primary);
  margin: 0;
}

.page-subtitle {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.support-loading {
  padding: 20px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
}

.ticket-list {
  display: grid;
  gap: 12px;
}

.ticket-card {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 22px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.ticket-card:hover {
  border-color: var(--color-border);
  box-shadow: var(--shadow-card);
  transform: translateY(-1px);
}

.ticket-main {
  min-width: 0;
  flex: 1;
}

.ticket-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: var(--color-text-primary);
}

.ticket-summary {
  margin: 9px 0 10px;
  color: var(--color-text-secondary);
  font-size: 14px;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ticket-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.ticket-arrow {
  color: var(--color-text-muted);
  flex: 0 0 auto;
}

.support-pagination {
  margin-top: 24px;
  justify-content: center;
}

@media (max-width: 640px) {
  .support-header {
    align-items: center;
  }

  .ticket-summary {
    white-space: normal;
  }
}
</style>

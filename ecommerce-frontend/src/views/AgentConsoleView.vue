<template>
  <AppLayout>
    <div class="ac-page">
      <div class="ac-header">
        <h2 class="ac-title">智能体思考控制台</h2>
        <div class="ac-header-right">
          <span class="ac-hint">查看智能体每次对话的决策过程：意图分类 → 路由 → 工具调用</span>
          <el-button size="small" :loading="loading" @click="fetchTraces" class="ac-refresh">刷新</el-button>
        </div>
      </div>

      <div v-if="loading && traces.length === 0" class="ac-loading">加载中...</div>
      <el-empty v-else-if="traces.length === 0" description="还没有对话记录，去和智能体聊聊吧" />

      <div v-else class="ac-list">
        <div v-for="t in traces" :key="t.id" class="ac-card">
          <!-- 头部：消息 + 元信息 -->
          <div class="ac-msg-row">
            <span class="ac-user-tag">用户</span>
            <span class="ac-msg">{{ t.message }}</span>
          </div>
          <div class="ac-meta">
            <el-tag size="small" :type="intentType(t.intent)">{{ intentLabel(t.intent) }}</el-tag>
            <el-tag size="small" type="info">{{ t.routeAgent || '未路由' }}</el-tag>
            <span class="ac-time">{{ t.createTime }} · {{ t.durationMs }}ms</span>
          </div>

          <!-- 工具调用链 -->
          <div v-if="stepsOf(t).length" class="ac-steps">
            <div v-for="(step, si) in stepsOf(t)" :key="si" class="ac-step">
              <div class="ac-step-title">第 {{ step.round }} 轮工具调用</div>
              <div v-for="(call, ci) in step.calls" :key="ci" class="ac-call" :class="{ fail: !call.ok }">
                <div class="ac-call-head">
                  <span class="ac-tool">{{ call.tool }}</span>
                  <span class="ac-badge" :class="call.ok ? 'ok' : 'fail'">{{ call.ok ? '成功' : '失败' }}</span>
                </div>
                <div v-if="call.args && call.args !== '{}'" class="ac-call-args">参数: {{ call.args }}</div>
                <div class="ac-call-result">结果: {{ call.result }}</div>
              </div>
            </div>
          </div>
          <div v-else class="ac-no-tool">（本回合未调用工具）</div>

          <!-- 回复 -->
          <div class="ac-reply-row">
            <span class="ac-agent-tag">{{ t.routeAgent || 'Agent' }}</span>
            <span class="ac-reply">{{ t.reply }}</span>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import AppLayout from '@/layouts/AppLayout.vue'
import { getChatTraces, parseSteps } from '@/api/chat'
import type { ChatTraceItem, TraceStep } from '@/api/chat'

const loading = ref(false)
const traces = ref<ChatTraceItem[]>([])
const stepsCache = new Map<number, TraceStep[]>()

async function fetchTraces() {
  loading.value = true
  try {
    traces.value = await getChatTraces(30)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '轨迹加载失败')
  } finally {
    loading.value = false
  }
}

function stepsOf(t: ChatTraceItem): TraceStep[] {
  if (!stepsCache.has(t.id)) {
    stepsCache.set(t.id, parseSteps(t.steps))
  }
  return stepsCache.get(t.id) || []
}

const intentLabel = (i: string) => ({ order: '订单', product: '商品', cart: '购物车', chat: '闲聊', unknown: '未知' } as any)[i] || i || '未分类'
const intentType = (i: string) => ({ order: 'warning', product: 'success', cart: 'danger', chat: 'info' } as any)[i] || 'info'

onMounted(fetchTraces)
</script>

<style scoped>
.ac-page { width: 100%; max-width: 900px; margin: 0 auto; padding: 40px 24px 88px; }
.ac-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 16px; gap: 12px; }
.ac-title { font-family: var(--font-display); font-size: clamp(1.5rem, 3vw, 2.25rem); font-weight: 400; line-height: 1.5; color: var(--color-text-primary); margin: 0; }
.ac-header-right { text-align: right; }
.ac-hint { display: block; font-size: 12px; color: var(--color-text-secondary); margin-bottom: 6px; }
.ac-refresh { --el-button-bg-color: var(--color-accent); --el-button-border-color: var(--color-accent); }
.ac-loading { text-align: center; padding: 60px 0; color: var(--color-text-secondary); }
.ac-list { display: flex; flex-direction: column; gap: 14px; }
.ac-card { background: var(--color-bg-raised); border: 1px solid var(--color-border-subtle); border-radius: var(--radius-lg); padding: 20px 24px; }
.ac-msg-row { display: flex; gap: 8px; align-items: flex-start; margin-bottom: 6px; }
.ac-user-tag { background: var(--color-accent); color: #fff; font-size: 11px; padding: 2px 8px; border-radius: 10px; flex-shrink: 0; margin-top: 2px; }
.ac-msg { font-size: 14px; color: var(--color-text-primary); line-height: 1.5; }
.ac-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
.ac-time { font-size: 11px; color: var(--color-text-muted); }
.ac-steps { border-left: 2px solid var(--color-border); margin: 8px 0 8px 6px; padding-left: 12px; }
.ac-step { margin-bottom: 10px; }
.ac-step-title { font-size: 12px; color: var(--color-accent); font-weight: 600; margin-bottom: 4px; }
.ac-call { background: var(--color-bg-raised); border: 1px solid var(--color-border-subtle); border-radius: 8px; padding: 6px 10px; margin-bottom: 6px; font-size: 12px; }
.ac-call.fail { border-color: #f5c6c0; background: #fef5f4; }
.ac-call-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 2px; }
.ac-tool { font-family: 'Courier New', monospace; font-weight: 700; color: var(--color-text-primary); }
.ac-badge { font-size: 10px; padding: 1px 6px; border-radius: 8px; }
.ac-badge.ok { background: #e3f4e6; color: var(--color-success); }
.ac-badge.fail { background: #fde8e5; color: var(--color-error); }
.ac-call-args, .ac-call-result { color: var(--color-text-secondary); word-break: break-all; line-height: 1.5; }
.ac-no-tool { font-size: 12px; color: var(--color-text-muted); margin: 6px 0; }
.ac-reply-row { display: flex; gap: 8px; align-items: flex-start; border-top: 1px dashed var(--color-border-subtle); padding-top: 10px; }
.ac-agent-tag { background: #e3e0da; color: #5a534a; font-size: 11px; padding: 2px 8px; border-radius: 10px; flex-shrink: 0; margin-top: 2px; }
.ac-reply { font-size: 13px; color: var(--color-text-secondary); line-height: 1.6; }
</style>

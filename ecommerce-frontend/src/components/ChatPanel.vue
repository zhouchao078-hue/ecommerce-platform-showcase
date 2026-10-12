<template>
  <div class="chat-wrapper">
    <!-- 触发按钮 -->
    <button class="chat-trigger" :class="{ open: isOpen }" @click="toggle">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
      </svg>
      <span class="trigger-dot" />
    </button>

    <!-- 聊天面板 -->
    <div class="chat-panel" :class="{ open: isOpen }">
      <!-- 头部 -->
      <div class="panel-header">
        <span class="panel-title">智能助手</span>
        <button class="close-btn" @click="isOpen = false">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      <!-- 消息列表 -->
      <div class="messages" ref="messagesRef">
        <div v-for="(msg, i) in messages" :key="i" class="msg" :class="msg.role">
          <div class="msg-agent" v-if="msg.agent">{{ msg.agent }}</div>
          <div class="msg-content">{{ msg.content }}</div>
          <div v-if="msg.products?.length" class="chat-products">
            <button
              v-for="product in msg.products"
              :key="product.id"
              class="chat-product"
              type="button"
              @click="openProduct(product.id)"
            >
              <img :src="product.image || '/favicon.ico'" :alt="product.name" />
              <span class="chat-product-info">
                <strong>{{ product.name }}</strong>
                <span class="chat-product-meta">¥{{ product.price }} · 库存 {{ product.stock }}</span>
              </span>
            </button>
          </div>
          <div class="msg-time">{{ msg.time }}</div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <input
          v-model="input"
          class="msg-input"
          placeholder="输入消息..."
          @keyup.enter="send"
          :disabled="loading"
        />
        <button class="send-btn" :disabled="!input.trim() || loading" @click="send">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="22" y1="2" x2="11" y2="13" /><polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'
import { useAuthStore } from '@/stores/auth'
import type { ProductCard } from '@/api/product'

const authStore = useAuthStore()
const router = useRouter()
const isOpen = ref(false)
const input = ref('')
const loading = ref(false)
const messagesRef = ref<HTMLElement | null>(null)

interface Message {
  role: 'user' | 'assistant'
  content: string
  time: string
  agent?: string
  products?: ProductCard[]
}
const messages = ref<Message[]>([
  { role: 'assistant', content: '你好！我是智能助手，有什么可以帮你的吗？', time: '刚刚' }
])

// 本地历史：按用户隔离，刷新页面后聊天记录不丢失
const storageKey = computed(() => 'ecommerce_chat_history_' + (authStore.userInfo?.id ?? 'guest'))

function loadHistory() {
  try {
    const raw = localStorage.getItem(storageKey.value)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed) && parsed.length > 0) {
        messages.value = parsed
      }
    }
  } catch { /* 历史损坏则忽略 */ }
}

function saveHistory() {
  try {
    localStorage.setItem(storageKey.value, JSON.stringify(messages.value))
  } catch { /* 存储失败不影响聊天 */ }
}

// 允许其它组件打开面板（例如首页 hero 的「问问助手」按钮），
// 沿用项目里已有的 window 事件约定（cart-updated 也是这个模式）。
function openPanel() { isOpen.value = true }

onMounted(() => {
  loadHistory()
  window.addEventListener('open-chat', openPanel)
})
onBeforeUnmount(() => window.removeEventListener('open-chat', openPanel))

watch(messages, saveHistory, { deep: true })

function toggle() { isOpen.value = !isOpen.value }

// 消息列表滚动到底部（新消息进来时调用）
async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function nowTime(): string {
  const now = new Date()
  return now.getHours().toString().padStart(2, '0') + ':' + now.getMinutes().toString().padStart(2, '0')
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return
  const time = nowTime()
  messages.value.push({ role: 'user', content: text, time })
  input.value = ''
  loading.value = true
  scrollToBottom()
  try {
    const res = await request.post('/chat', { message: text })
    const reply = res.data.data.reply
    const agent = res.data.data.agent || ''
    const products = Array.isArray(res.data.data.products) ? res.data.data.products : []
    messages.value.push({ role: 'assistant', content: reply, agent, products, time })
  } catch {
    messages.value.push({ role: 'assistant', content: '抱歉，我暂时无法回复。', time })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function openProduct(id: number) {
  router.push(`/products/${id}`)
}
</script>

<style scoped>
.chat-wrapper { position: fixed; right: 0; top: 0; height: 100vh; z-index: 1000; display: flex; align-items: center; pointer-events: none; }

.chat-trigger { pointer-events: auto; position: absolute; right: 0; top: 50%; transform: translateY(-50%); z-index: 10; width: 40px; height: 100px; border-radius: 8px 0 0 8px; border: none; background: rgba(146, 118, 91, 0.9); color: #fff; cursor: pointer; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; transition: all 0.3s ease; backdrop-filter: blur(8px); }
.chat-trigger:hover { background: var(--color-accent); width: 44px; }
.chat-trigger.open { right: 360px; }
.trigger-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--color-success); animation: pulse 2s infinite; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.3; } }

.chat-panel { pointer-events: auto; position: absolute; right: -380px; top: 50%; transform: translateY(-50%); width: 360px; height: 520px; background: rgba(255, 255, 255, 0.35); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border: 1px solid rgba(255, 255, 255, 0.5); border-radius: 16px 0 0 16px; box-shadow: inset 0 20px 30px rgba(255, 255, 255, 0.3), 0 10px 30px rgba(0, 0, 0, 0.08); display: flex; flex-direction: column; transition: right 0.35s cubic-bezier(0.4, 0, 0.2, 1); overflow: hidden; }
.chat-panel.open { right: 0; }

.panel-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; border-bottom: 1px solid rgba(200, 195, 185, 0.4); }
.panel-title { font-family: 'Inter', serif; font-size: 15px; color: var(--color-text-primary); font-weight: 600; }
.close-btn { background: none; border: none; cursor: pointer; color: var(--color-text-secondary); padding: 4px; border-radius: 4px; transition: color 0.2s; }
.close-btn:hover { color: var(--color-text-primary); }

.messages { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.msg { max-width: 85%; display: flex; flex-direction: column; }
.msg.user { align-self: flex-end; }
.msg.assistant { align-self: flex-start; }
.msg-content { padding: 10px 14px; border-radius: 12px; font-size: 13px; line-height: 1.5; color: var(--color-text-primary); }
.msg-agent { font-size: 10px; color: var(--color-accent); margin-bottom: 2px; padding-left: 4px; font-weight: 600; }
.msg.user .msg-content { background: rgba(146, 118, 91, 0.85); color: #fff; border-bottom-right-radius: 4px; }
.msg.assistant .msg-content { background: rgba(255, 255, 255, 0.5); border: 1px solid rgba(200, 195, 185, 0.3); border-bottom-left-radius: 4px; }
.msg-time { font-size: 11px; color: var(--color-text-secondary); margin-top: 4px; padding: 0 4px; }
.msg.user .msg-time { text-align: right; }

.chat-products { display: grid; gap: 8px; margin-top: 8px; }
.chat-product { display: flex; align-items: center; gap: 10px; width: 100%; padding: 7px; border: 1px solid rgba(200, 195, 185, 0.38); border-radius: 8px; background: rgba(255, 255, 255, 0.62); color: var(--color-text-primary); text-align: left; cursor: pointer; }
.chat-product:hover { border-color: var(--color-accent); background: rgba(255, 255, 255, 0.92); }
.chat-product img { width: 48px; height: 48px; flex: 0 0 48px; border-radius: 6px; object-fit: cover; background: rgba(200, 195, 185, 0.2); }
.chat-product-info { min-width: 0; display: grid; gap: 4px; }
.chat-product-info strong { overflow: hidden; color: var(--color-text-primary); font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.chat-product-meta { color: var(--color-text-secondary); font-size: 12px; }

.input-area { display: flex; align-items: center; gap: 8px; padding: 12px 16px; border-top: 1px solid rgba(200, 195, 185, 0.4); }
.msg-input { flex: 1; border: none; background: rgba(255, 255, 255, 0.5); border-radius: 8px; padding: 10px 14px; font-size: 13px; outline: none; font-family: 'Inter', sans-serif; color: var(--color-text-primary); }
.msg-input::placeholder { color: var(--color-text-muted); }
.send-btn { width: 36px; height: 36px; border-radius: 50%; border: none; background: var(--color-accent); color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: background 0.2s; flex-shrink: 0; }
.send-btn:hover { background: var(--color-accent-darker); }
.send-btn:disabled { opacity: 0.4; cursor: not-allowed; }
</style>

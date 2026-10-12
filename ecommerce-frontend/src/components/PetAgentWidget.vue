<template>
  <div class="pet-widget">
    <section v-if="open" class="pet-panel" aria-label="桌宠聊天">
      <header class="pet-header">
        <div class="pet-heading">
          <span class="pet-name">{{ currentPet.name }}</span>
          <span class="pet-subtitle">一直在这儿陪你</span>
        </div>
        <div class="pet-controls">
          <button class="icon-button" title="换一个形象" aria-label="换一个形象" @click="nextPet">↻</button>
          <button class="icon-button" title="关闭" aria-label="关闭" @click="open = false">×</button>
        </div>
      </header>
      <div ref="messagesEl" class="pet-messages">
        <div v-for="(message, index) in messages" :key="index" class="bubble-row" :class="message.role">
          <div class="bubble">{{ message.content }}</div>
        </div>
        <div v-if="loading" class="bubble-row assistant"><div class="bubble typing">正在想怎么回答…</div></div>
      </div>
      <form class="pet-input" @submit.prevent="send">
        <input v-model="input" :disabled="loading" maxlength="500" placeholder="和小伙伴聊两句" aria-label="输入消息" />
        <button type="submit" :disabled="loading || !input.trim()" title="发送" aria-label="发送">➤</button>
      </form>
    </section>

    <button class="pet-trigger" :aria-label="`和${currentPet.name}聊天`" :title="`和${currentPet.name}聊天`" @click="open = !open">
      <svg viewBox="0 0 64 64" role="img" aria-hidden="true">
        <circle cx="32" cy="30" r="23" :fill="currentPet.color" stroke="var(--color-text-primary)" stroke-width="2.5" />
        <circle cx="24" cy="27" r="2" fill="var(--color-text-primary)" />
        <circle cx="40" cy="27" r="2" fill="var(--color-text-primary)" />
        <path d="M28 36q4 4 8 0" fill="none" stroke="var(--color-text-primary)" stroke-width="2.5" stroke-linecap="round" />
        <g v-if="petIndex === 0" stroke="var(--color-text-primary)" stroke-width="2">
        <!-- 内联 SVG 的 presentation attribute 用不了 CSS 变量，
             这里写与 tokens.css 一致的字面色值（下同） -->
        <rect x="22" y="39" width="20" height="13" rx="3" fill="#e8c9b8" />
        <circle cx="32" cy="45" r="4" fill="#c96442" />
        </g>
        <g v-else-if="petIndex === 1">
        <path d="m24 43 16 0-8-12z" fill="#e8c9b8" stroke="var(--color-text-primary)" stroke-width="2" />
        <path d="m28 40 8 0-4-6z" fill="#d97757" />
        </g>
        <g v-else-if="petIndex === 2">
        <path d="m22 43 20 0-3 12h-5v-6h-4v6h-5z" fill="#4f7a5e" stroke="var(--color-text-primary)" stroke-width="2" />
        </g>
        <g v-else>
        <rect x="43" y="39" width="13" height="12" rx="3" fill="#f5f3ec" stroke="var(--color-text-primary)" stroke-width="2" />
          <circle cx="47" cy="44" r="1.5" fill="white" /><circle cx="52" cy="44" r="1.5" fill="white" />
        </g>
      </svg>
      <span class="online-dot" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import request from '@/api/request'
import { useAuthStore } from '@/stores/auth'

const pets = [
      { name: '相机伙伴', color: '#d97757' },
      { name: '美食伙伴', color: '#e8c9b8' },
      { name: '穿搭伙伴', color: '#4f7a5e' },
      { name: '机器人伙伴', color: '#b4522f' },
]
const auth = useAuthStore()
const petIndex = ref(Number(localStorage.getItem('ecommerce_pet_avatar') || 0) % pets.length)
const currentPet = computed(() => pets[petIndex.value])
const open = ref(false)
const input = ref('')
const loading = ref(false)
const messagesEl = ref<HTMLElement | null>(null)
interface Message { role: 'user' | 'assistant'; content: string }
const messages = ref<Message[]>([{ role: 'assistant', content: '嗨，我在这儿呢。今天想聊点什么？' }])
const storageKey = computed(() => `ecommerce_pet_history_${auth.userInfo?.id ?? 'guest'}`)

function loadMessages() {
  try {
    const value = JSON.parse(localStorage.getItem(storageKey.value) || '[]')
    if (Array.isArray(value) && value.length) messages.value = value
  } catch { /* 忽略损坏的本地记录 */ }
}
watch(storageKey, loadMessages, { immediate: true })
watch(messages, value => {
  try { localStorage.setItem(storageKey.value, JSON.stringify(value.slice(-40))) } catch { /* 本地存储不可用时继续聊天 */ }
}, { deep: true })

function nextPet() {
  petIndex.value = (petIndex.value + 1) % pets.length
  localStorage.setItem('ecommerce_pet_avatar', String(petIndex.value))
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  loading.value = true
  try {
    const response = await request.post('/chat/pet', { message: text }, { timeout: 120000 })
    messages.value.push({ role: 'assistant', content: response.data.data.reply })
  } catch (error: any) {
    messages.value.push({ role: 'assistant', content: error?.message || '刚才没连上，等会儿再跟我说吧。' })
  } finally {
    loading.value = false
    await nextTick()
    if (messagesEl.value) messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  }
}
</script>

<style scoped>
.pet-widget { position: fixed; z-index: 1100; right: 24px; bottom: 24px; display: flex; align-items: flex-end; flex-direction: column; gap: 12px; pointer-events: none; }
.pet-trigger { pointer-events: auto; position: relative; width: 64px; height: 64px; padding: 0; border: 2px solid var(--color-bg-overlay); border-radius: 50%; background: var(--color-bg-overlay); box-shadow: 0 5px 20px rgba(20, 20, 19, 0.15); cursor: pointer; animation: bob 3s ease-in-out infinite; }
.pet-trigger svg { width: 100%; height: 100%; }
.online-dot { position: absolute; right: 1px; bottom: 3px; width: 11px; height: 11px; border: 2px solid white; border-radius: 50%; background: var(--color-success); }
.pet-panel { pointer-events: auto; width: min(350px, calc(100vw - 32px)); height: min(440px, calc(100dvh - 120px)); display: flex; flex-direction: column; background: var(--color-bg-overlay); border: 1px solid var(--color-border-subtle); border-radius: var(--radius-lg); box-shadow: var(--shadow-elevated); overflow: hidden; }
.pet-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; border-bottom: 1px solid var(--color-border-subtle); }
.pet-heading { display: grid; gap: 3px; }
.pet-name { color: var(--color-text-primary); font-size: 14px; font-weight: 600; }
.pet-subtitle { color: var(--color-text-secondary); font-size: 12px; }
.pet-controls { display: flex; gap: 5px; }
.icon-button { width: 30px; height: 30px; border: 0; background: transparent; color: var(--color-text-secondary); font-size: 22px; cursor: pointer; }
.pet-messages { flex: 1; overflow-y: auto; padding: 14px; display: flex; flex-direction: column; gap: 10px; }
.bubble-row { display: flex; }
.bubble-row.user { justify-content: flex-end; }
.bubble { max-width: 84%; padding: 9px 12px; border-radius: 8px; background: var(--color-bg-raised); color: var(--color-text-primary); font-size: 13px; line-height: 1.55; overflow-wrap: anywhere; white-space: pre-wrap; }
.user .bubble { background: var(--color-success); color: white; }
.typing { color: var(--color-text-secondary); }
.pet-input { display: flex; gap: 8px; padding: 12px; border-top: 1px solid var(--color-border-subtle); }
.pet-input input { min-width: 0; flex: 1; height: 38px; padding: 0 11px; border: 1px solid var(--color-border-subtle); border-radius: 6px; outline-color: var(--color-success); font: inherit; font-size: 13px; }
.pet-input button { width: 40px; border: 0; border-radius: 6px; background: var(--color-success); color: #fff; cursor: pointer; }
.pet-input button:disabled { opacity: .45; cursor: default; }
@keyframes bob { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-4px); } }
@media (max-width: 640px) { .pet-widget { right: 14px; bottom: 14px; } .pet-trigger { width: 56px; height: 56px; } }
@media (prefers-reduced-motion: reduce) { .pet-trigger { animation: none; } }
</style>

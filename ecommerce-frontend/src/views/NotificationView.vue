<template>
  <AppLayout>
    <div class="nt container">
      <header class="nt__head">
        <h1 class="nt__title">通知</h1>
        <el-button v-if="hasUnread" size="small" @click="onReadAll">全部标为已读</el-button>
      </header>

      <div v-if="loading" class="hint">加载中…</div>
      <p v-else-if="items.length === 0" class="hint">
        暂时没有通知。订单发货、退款到账、关注的商品降价时会在这里提醒你。
      </p>

      <div v-else class="list">
        <article
          v-for="n in items"
          :key="n.id"
          class="item"
          :class="{ 'item--unread': n.isRead === 0, clickable: !!targetOf(n) }"
          @click="onOpen(n)"
        >
          <div class="item__dot" aria-hidden="true" />
          <div class="item__main">
            <p class="item__title">{{ n.title }}</p>
            <p v-if="n.content" class="item__content">{{ n.content }}</p>
            <p class="item__time">{{ n.createTime }}</p>
          </div>
        </article>
      </div>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { listNotifications, markRead, markAllRead, targetOf, type NotificationItem } from '@/api/notification'

const router = useRouter()
const items = ref<NotificationItem[]>([])
const loading = ref(true)

const hasUnread = computed(() => items.value.some((n) => n.isRead === 0))

onMounted(async () => {
  try {
    items.value = await listNotifications()
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
})

async function onOpen(n: NotificationItem) {
  if (n.isRead === 0) {
    try {
      await markRead(n.id)
      n.isRead = 1
      // 顶栏红点跟着更新
      window.dispatchEvent(new Event('notify-updated'))
    } catch { /* 标记失败不影响跳转 */ }
  }
  const to = targetOf(n)
  if (to) router.push(to)
}

async function onReadAll() {
  try {
    await markAllRead()
    items.value.forEach((n) => { n.isRead = 1 })
    window.dispatchEvent(new Event('notify-updated'))
  } catch { /* 忽略 */ }
}
</script>

<style scoped>
.container { width: 100%; max-width: 900px; margin: 0 auto; padding-inline: 24px; }
.nt { padding-block: 40px 88px; }

.nt__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}
.nt__title {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-text-primary);
}

.hint { padding: 64px 0; text-align: center; font-size: 15px; line-height: 1.7; color: var(--color-text-secondary); }

.list { display: flex; flex-direction: column; gap: 10px; }
.item {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 18px 20px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
  transition: border-color 0.15s ease;
}
.item.clickable { cursor: pointer; }
.item.clickable:hover { border-color: var(--color-border-strong); }

/* 未读：左侧一个小圆点 + 稍亮的底色，不用加粗整段文字（会有压迫感） */
.item__dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  margin-top: 8px;
  border-radius: var(--radius-full);
  background: transparent;
}
.item--unread { background: var(--color-bg-overlay); }
.item--unread .item__dot { background: var(--color-accent); }

.item__main { flex: 1; min-width: 0; }
.item__title { font-size: 15px; color: var(--color-text-primary); margin-bottom: 4px; }
.item__content { font-size: 14px; line-height: 1.6; color: var(--color-text-secondary); }
.item__time { font-size: 12px; color: var(--color-text-muted); margin-top: 8px; }

@media (max-width: 640px) {
  .container { padding-inline: 16px; }
}
</style>

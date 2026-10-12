<template>
  <AppLayout>
    <div class="mo container">
      <header class="mo__head">
        <h1 class="mo__title">商家订单</h1>
        <router-link to="/merchant/products" class="mo__link">商品管理 →</router-link>
      </header>

      <div class="tabs" role="tablist">
        <button
          v-for="t in tabs"
          :key="String(t.value)"
          type="button"
          role="tab"
          :aria-selected="status === t.value"
          @click="onTab(t.value)"
        >
          {{ t.label }}
        </button>
      </div>

      <div v-if="loading" class="hint">加载中…</div>

      <p v-else-if="orders.length === 0" class="hint">
        该状态下暂无订单。买家下单后，这里会出现需要你处理的订单。
      </p>

      <div v-else class="order-list">
        <article v-for="o in orders" :key="o.id" class="order">
          <div class="order__head">
            <span class="order__no">{{ o.orderNo }}</span>
            <span class="order__status">{{ statusText(o.status) }}</span>
          </div>

          <div class="order__body">
            <p class="order__buyer">{{ o.receiverName }}　{{ o.receiverPhone }}</p>
            <p class="order__addr">{{ o.receiverAddress }}</p>
            <p v-if="o.remark" class="order__remark">买家备注：{{ o.remark }}</p>
          </div>

          <div class="order__foot">
            <span class="order__amount">¥{{ Number(o.totalAmount).toFixed(2) }}</span>
            <span class="order__time">{{ o.createTime }}</span>
            <button
              v-if="o.status === 1"
              class="ship-btn"
              type="button"
              :disabled="shippingId === o.id"
              @click="onShip(o)"
            >
              {{ shippingId === o.id ? '处理中…' : '发货' }}
            </button>
          </div>
        </article>
      </div>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '@/layouts/AppLayout.vue'
import { listMerchantOrders, shipOrder } from '@/api/merchantOrder'
import type { MerchantOrder } from '@/api/merchantOrder'

const tabs = [
  { label: '全部', value: null as number | null },
  { label: '待发货', value: 1 },
  { label: '待收货', value: 2 },
  { label: '已完成', value: 3 },
]

const status = ref<number | null>(null)
const orders = ref<MerchantOrder[]>([])
const loading = ref(true)
const shippingId = ref<number | null>(null)

const STATUS_TEXT: Record<number, string> = {
  0: '待付款',
  1: '待发货',
  2: '待收货',
  3: '已完成',
  4: '已取消',
}
function statusText(s: number) {
  return STATUS_TEXT[s] ?? '未知'
}

async function fetchOrders() {
  loading.value = true
  try {
    const page = await listMerchantOrders({ status: status.value, pageNum: 1, pageSize: 20 })
    orders.value = page.records || []
  } catch {
    orders.value = []
  } finally {
    loading.value = false
  }
}

function onTab(v: number | null) {
  status.value = v
  fetchOrders()
}

async function onShip(order: MerchantOrder) {
  try {
    await ElMessageBox.confirm(
      `确认为订单 ${order.orderNo} 发货吗？发货后买家会看到物流状态更新。`,
      '确认发货',
    )
  } catch {
    return // 用户取消
  }

  shippingId.value = order.id
  try {
    await shipOrder(order.id)
    ElMessage.success('已发货')
    // 发货后状态变了，重新拉一次列表（当前筛选是「待发货」时会自动移出）
    await fetchOrders()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '发货失败')
  } finally {
    shippingId.value = null
  }
}

onMounted(fetchOrders)
</script>

<style scoped>
.container { width: 100%; max-width: 900px; margin: 0 auto; padding-inline: 24px; }
.mo { padding-block: 40px 88px; }

.mo__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}
.mo__title {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-text-primary);
}
.mo__link {
  font-size: 14px;
  color: var(--color-accent-dark);
  text-decoration: none;
}
.mo__link:hover { text-decoration: underline; }

/* 状态筛选：和订单列表、首页品类保持同一套胶囊 chip */
.tabs { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 28px; }
.tabs button {
  display: inline-flex;
  align-items: center;
  min-height: 38px;
  padding: 0 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: transparent;
  color: var(--color-text-secondary);
  font-family: var(--font-body);
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}
.tabs button:hover { border-color: var(--color-border-strong); color: var(--color-text-primary); }
.tabs button[aria-selected='true'] {
  background: var(--color-bg-inverted);
  border-color: var(--color-bg-inverted);
  color: var(--color-bg-overlay);
}

.hint {
  padding: 64px 0;
  text-align: center;
  font-size: 15px;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.order-list { display: flex; flex-direction: column; gap: 12px; }

.order {
  padding: 20px 24px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
}

.order__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.order__no { font-size: 13px; color: var(--color-text-muted); }
.order__status { font-size: 14px; color: var(--color-accent-dark); font-weight: 500; }

.order__body { display: flex; flex-direction: column; gap: 4px; margin-bottom: 16px; }
.order__buyer { font-size: 15px; color: var(--color-text-primary); }
.order__addr { font-size: 14px; line-height: 1.6; color: var(--color-text-secondary); }
.order__remark { font-size: 13px; color: var(--color-text-muted); }

.order__foot {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border-subtle);
}
.order__amount {
  font-family: var(--font-display);
  font-size: 20px;
  color: var(--color-accent-dark);
}
.order__time { flex: 1; font-size: 12px; color: var(--color-text-muted); }

.ship-btn {
  flex-shrink: 0;
  min-height: 38px;
  padding: 0 22px;
  border: none;
  border-radius: var(--radius-full);
  background: var(--color-accent);
  color: #fff;
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.15s ease;
}
.ship-btn:hover:not(:disabled) { background: var(--color-accent-dark); }
.ship-btn:disabled { opacity: 0.6; cursor: progress; }

@media (max-width: 640px) {
  .container { padding-inline: 16px; }
  .order__foot { flex-wrap: wrap; }
}
</style>

<template>
  <AppLayout>
    <div class="ma container">
      <header class="ma__head">
        <h1 class="ma__title">售后处理</h1>
        <div class="ma__links">
          <router-link to="/merchant/orders">订单管理</router-link>
          <router-link to="/merchant/products">商品管理</router-link>
        </div>
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
      <p v-else-if="items.length === 0" class="hint">
        该状态下暂无售后申请。买家申请后会出现在这里，需要你同意或拒绝。
      </p>

      <div v-else class="list">
        <article v-for="a in items" :key="a.id" class="item">
          <div class="item__head">
            <span class="item__no">{{ a.afterSaleNo }}</span>
            <span class="item__status">
              {{ a.type === 2 ? '退货退款' : '仅退款' }} · {{ AFTER_SALE_STATUS[a.status] || '未知' }}
            </span>
          </div>

          <div class="item__body">
            <p class="item__reason">申请原因：{{ a.reason }}</p>
            <p v-if="a.description" class="item__desc">{{ a.description }}</p>
            <p v-if="a.rejectReason" class="item__reject">拒绝理由：{{ a.rejectReason }}</p>
            <p v-if="a.returnCompany" class="item__return">
              退货物流：{{ a.returnCompany }} {{ a.returnTrackingNo }}
            </p>
          </div>

          <div class="item__foot">
            <span class="item__amount">退款 ¥{{ Number(a.amount).toFixed(2) }}</span>
            <span class="item__time">{{ a.appliedAt }}</span>
            <template v-if="a.status === 0">
              <button class="btn btn--ghost" type="button" :disabled="acting" @click="onReject(a)">拒绝</button>
              <button class="btn btn--primary" type="button" :disabled="acting" @click="onApprove(a)">
                {{ a.type === 2 ? '同意退货' : '同意退款' }}
              </button>
            </template>
            <template v-else-if="a.status === 5">
              <button class="btn btn--primary" type="button" :disabled="acting" @click="onReceive(a)">
                确认收货并退款
              </button>
            </template>
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
import {
  listMerchantAfterSales, approveAfterSale, rejectAfterSale, receiveAfterSaleReturn,
  AFTER_SALE_STATUS, type AfterSale,
} from '@/api/afterSale'

const tabs = [
  { label: '待处理', value: 0 },
  { label: '待寄回', value: 1 },
  { label: '待收货', value: 5 },
  { label: '已完成', value: 3 },
  { label: '已拒绝', value: 2 },
  { label: '全部', value: null as number | null },
]

const status = ref<number | null>(0)
const items = ref<AfterSale[]>([])
const loading = ref(true)
const acting = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const page = await listMerchantAfterSales({ status: status.value, pageNum: 1, pageSize: 20 })
    items.value = page.records || []
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

function onTab(v: number | null) {
  status.value = v
  fetchList()
}

async function onApprove(a: AfterSale) {
  const isReturn = a.type === 2
  try {
    await ElMessageBox.confirm(
      isReturn
        ? `确认同意退货吗？买家寄回并由你确认收货后，才会退款 ¥${Number(a.amount).toFixed(2)}。`
        : `确认同意这笔售后并退款 ¥${Number(a.amount).toFixed(2)} 吗？同意后款项会立即退回买家。`,
      isReturn ? '同意退货' : '同意退款',
    )
  } catch { return }

  acting.value = true
  try {
    await approveAfterSale(a.id)
    ElMessage.success(isReturn ? '已同意退货，等待买家寄回' : '已同意，退款完成')
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '操作失败')
  } finally {
    acting.value = false
  }
}

async function onReceive(a: AfterSale) {
  try {
    await ElMessageBox.confirm(
      `确认已收到退货吗？确认后将退款 ¥${Number(a.amount).toFixed(2)} 并回补商品库存。`,
      '确认收货',
    )
  } catch { return }

  acting.value = true
  try {
    await receiveAfterSaleReturn(a.id)
    ElMessage.success('已确认收货，退款并回补库存完成')
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '操作失败')
  } finally {
    acting.value = false
  }
}

async function onReject(a: AfterSale) {
  let reason = ''
  try {
    const r = await ElMessageBox.prompt('请填写拒绝理由，买家会看到', '拒绝售后', {
      inputPlaceholder: '例如：商品已使用，不符合退款条件',
      inputValidator: (v) => (v && v.trim() ? true : '请填写拒绝理由'),
    })
    reason = r.value
  } catch { return }

  acting.value = true
  try {
    await rejectAfterSale(a.id, reason)
    ElMessage.success('已拒绝')
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '操作失败')
  } finally {
    acting.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.container { width: 100%; max-width: 900px; margin: 0 auto; padding-inline: 24px; }
.ma { padding-block: 40px 88px; }

.ma__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}
.ma__title {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-text-primary);
}
.ma__links { display: flex; gap: 16px; font-size: 14px; }
.ma__links a { color: var(--color-accent-dark); text-decoration: none; }
.ma__links a:hover { text-decoration: underline; }

.tabs { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 28px; }
.tabs button {
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

.hint { padding: 64px 0; text-align: center; font-size: 15px; line-height: 1.7; color: var(--color-text-secondary); }

.list { display: flex; flex-direction: column; gap: 12px; }
.item {
  padding: 20px 24px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
}
.item__head { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.item__no { font-size: 13px; color: var(--color-text-muted); }
.item__status { font-size: 14px; color: var(--color-accent-dark); font-weight: 500; }

.item__body { display: flex; flex-direction: column; gap: 4px; margin-bottom: 16px; }
.item__reason { font-size: 15px; color: var(--color-text-primary); }
.item__desc { font-size: 14px; line-height: 1.6; color: var(--color-text-secondary); }
.item__reject { font-size: 13px; color: var(--color-error); }
.item__return { font-size: 13px; color: var(--color-accent-dark); }

.item__foot {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border-subtle);
}
.item__amount { font-family: var(--font-display); font-size: 20px; color: var(--color-accent-dark); }
.item__time { flex: 1; font-size: 12px; color: var(--color-text-muted); }

.btn {
  min-height: 38px;
  padding: 0 20px;
  border-radius: var(--radius-full);
  font-family: var(--font-body);
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}
.btn:disabled { opacity: 0.6; cursor: progress; }
.btn--primary { border: none; background: var(--color-accent); color: #fff; }
.btn--primary:hover:not(:disabled) { background: var(--color-accent-dark); }
.btn--ghost { border: 1px solid var(--color-border); background: transparent; color: var(--color-text-secondary); }
.btn--ghost:hover:not(:disabled) { border-color: var(--color-error); color: var(--color-error); }

@media (max-width: 640px) {
  .container { padding-inline: 16px; }
  .item__foot { flex-wrap: wrap; }
}
</style>

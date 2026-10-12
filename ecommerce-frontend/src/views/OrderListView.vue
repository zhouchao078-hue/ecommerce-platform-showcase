<template>
  <AppLayout>
    <div class="orders-page">
      <h2 class="page-title">我的订单</h2>
      <div class="filter-tabs">
        <span v-for="(t, idx) in tabs" :key="idx" :class="{ active: activeTab === t.value }" @click="activeTab = t.value; fetchOrders()">{{ t.label }}</span>
      </div>
      <div v-if="loading" class="loading"><SkeletonOrderCard v-for="n in 3" :key="n" /></div>
      <el-empty v-else-if="orders.length === 0" description="暂无订单">
        <el-button type="primary" @click="router.push('/')">去购物</el-button>
      </el-empty>
      <div v-else class="order-list">
        <div v-for="o in orders" :key="`${o.orderType}:${o.id}`" class="order-card" :class="{ clickable: o.orderType !== 'SECKILL' }" @click="openOrder(o)">
          <div class="order-header">
            <span class="order-no">订单号：</span><span class="order-no">{{ o.orderNo }}</span>
            <span class="order-badges">
              <span v-if="o.orderType === 'SECKILL'" class="order-type">秒杀</span>
              <span v-else-if="o.orderType === 'PARENT'" class="order-store">多店 {{ o.subOrderCount || 0 }} 单</span>
              <span class="order-status">{{ statusMap[o.status] || '未知状态' }}</span>
            </span>
          </div>
          <div class="order-body">
            <p class="order-receiver">{{ o.orderType === 'SECKILL' ? (o.productName || '秒杀商品') : `${o.receiverName || ''} ${o.receiverPhone || ''}` }}</p>
            <p class="order-amount">¥{{ Number(o.totalAmount).toFixed(2) }}</p>
          </div>
          <div class="order-footer">{{ o.createTime }}</div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue"
import { useRouter } from "vue-router"
import AppLayout from "@/layouts/AppLayout.vue"
import request from "@/api/request"

interface OrderItem {
  id: number
  orderNo: string
  totalAmount: string
  status: number
  receiverName: string
  receiverPhone: string
  createTime: string
  orderType: 'NORMAL' | 'PARENT' | 'SECKILL'
  productName?: string
  subOrderCount?: number
}

const router = useRouter()
const loading = ref(true)
const orders = ref<OrderItem[]>([])
const activeTab = ref<number | null>(null)
const tabs = [{label:'全部',value:null},{label:'待付款',value:0},{label:'待发货',value:1},{label:'待收货',value:2},{label:'已完成',value:3},{label:'已取消',value:4}]
const statusMap: Record<number, string> = { 0: "待付款", 1: "待发货", 2: "待收货", 3: "已完成", 4: "已取消" }

function openOrder(order: OrderItem) {
  if (order.orderType === 'SECKILL') {
    router.push({ path: '/orders/' + encodeURIComponent(order.orderNo), query: { type: 'SECKILL' } })
  } else {
    router.push('/orders/' + order.id)
  }
}

async function fetchOrders() {
  loading.value = true
  try {
    const res = await request.get("/orders", { params: { pageNum: 1, pageSize: 20, status: activeTab.value } })
    orders.value = res.data.data.records || []
  } catch {
    orders.value = []
  } finally {
    loading.value = false
  }
}

onMounted(fetchOrders)
</script>

<style scoped>
.orders-page {
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 24px 88px;
}

.page-title {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-text-primary);
  margin-bottom: 24px;
}

/* 筛选：胶囊 chip，和首页品类筛选保持一致 */
.filter-tabs { display: flex; gap: 8px; margin-bottom: 28px; flex-wrap: wrap; }
.filter-tabs span {
  display: inline-flex;
  align-items: center;
  min-height: 38px;
  padding: 0 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  font-size: 14px;
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}
.filter-tabs span:hover { border-color: var(--color-border-strong); color: var(--color-text-primary); }
.filter-tabs span.active {
  background: var(--color-bg-inverted);
  color: var(--color-bg-overlay);
  border-color: var(--color-bg-inverted);
}

.loading { text-align: center; padding: 80px 0; color: var(--color-text-secondary); }

.order-card {
  padding: 20px 24px;
  margin-bottom: 12px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.order-card:hover {
  border-color: var(--color-border);
  box-shadow: var(--shadow-card);
}

.order-header { display: flex; justify-content: space-between; margin-bottom: 12px; }
.order-no { font-size: 13px; color: var(--color-text-muted); }
.order-status { font-size: 14px; color: var(--color-accent-dark); font-weight: 500; }
.order-badges { display: flex; align-items: center; gap: 8px; }
.order-type {
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  background: var(--color-error);
  color: #fff;
  font-size: 11px;
}
.order-store {
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  background: var(--color-accent-dim);
  color: var(--color-accent-dark);
  font-size: 11px;
}
.order-receiver { font-size: 14px; color: var(--color-text-secondary); margin: 0 0 6px; }
.order-amount { font-family: var(--font-display); font-size: 20px; color: var(--color-accent-dark); margin: 0; }
.order-footer { font-size: 12px; color: var(--color-text-muted); margin-top: 12px; }
</style>

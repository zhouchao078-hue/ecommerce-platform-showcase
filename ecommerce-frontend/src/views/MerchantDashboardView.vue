<template>
  <AppLayout>
    <div v-loading="loading" class="md container">
      <header class="md__head">
        <div>
          <p class="md__eyebrow">Merchant overview</p>
          <h1 class="md__title">商家看板</h1>
        </div>
        <div class="md__links">
          <router-link to="/merchant/products">商品管理</router-link>
          <router-link to="/merchant/orders">订单管理</router-link>
          <router-link to="/merchant/after-sales">售后处理</router-link>
        </div>
      </header>

      <template v-if="dashboard">
        <section class="stat-grid">
          <article class="stat-card stat-card--primary">
            <span>累计销售额</span>
            <strong>¥{{ money(dashboard.totalSales) }}</strong>
            <small>已付款订单口径</small>
          </article>
          <article class="stat-card">
            <span>今日销售额</span>
            <strong>¥{{ money(dashboard.todaySales) }}</strong>
            <small>按订单创建日期统计</small>
          </article>
          <article class="stat-card">
            <span>累计订单</span>
            <strong>{{ dashboard.totalOrders }}</strong>
            <small>含待发货、待收货、已完成</small>
          </article>
          <article class="stat-card">
            <span>关注人数</span>
            <strong>{{ dashboard.followerCount }}</strong>
            <small>关注店铺的用户</small>
          </article>
        </section>

        <section class="work-grid">
          <article class="work-block">
            <div class="work-block__head">
              <h2>待办事项</h2>
              <router-link to="/merchant/orders">查看订单</router-link>
            </div>
            <dl class="metric-list">
              <div>
                <dt>待发货</dt>
                <dd :class="{ urgent: dashboard.pendingShip > 0 }">{{ dashboard.pendingShip }}</dd>
              </div>
              <div>
                <dt>待收货</dt>
                <dd>{{ dashboard.pendingReceive }}</dd>
              </div>
              <div>
                <dt>待处理售后</dt>
                <dd :class="{ urgent: dashboard.pendingAfterSales > 0 }">{{ dashboard.pendingAfterSales }}</dd>
              </div>
              <div>
                <dt>退货待收货</dt>
                <dd :class="{ urgent: dashboard.returnWaitingReceipt > 0 }">{{ dashboard.returnWaitingReceipt }}</dd>
              </div>
            </dl>
          </article>

          <article class="work-block">
            <div class="work-block__head">
              <h2>商品概况</h2>
              <router-link to="/merchant/products">管理商品</router-link>
            </div>
            <dl class="metric-list">
              <div>
                <dt>商品总数</dt>
                <dd>{{ dashboard.productTotal }}</dd>
              </div>
              <div>
                <dt>在售商品</dt>
                <dd>{{ dashboard.productOnSale }}</dd>
              </div>
              <div>
                <dt>已完成订单</dt>
                <dd>{{ dashboard.completedOrders }}</dd>
              </div>
              <div>
                <dt>未上架商品</dt>
                <dd>{{ dashboard.productTotal - dashboard.productOnSale }}</dd>
              </div>
            </dl>
          </article>
        </section>

        <section class="recent">
          <div class="work-block__head">
            <h2>最近订单</h2>
            <router-link to="/merchant/orders">全部订单</router-link>
          </div>
          <p v-if="dashboard.recentOrders.length === 0" class="empty">暂无订单</p>
          <div v-else class="recent-list">
            <article v-for="order in dashboard.recentOrders" :key="order.id" class="recent-row">
              <div class="recent-row__main">
                <span class="recent-row__no">{{ order.orderNo }}</span>
                <span class="recent-row__buyer">{{ order.buyerName }}</span>
              </div>
              <span class="recent-row__time">{{ order.createTime }}</span>
              <span class="recent-row__status">{{ statusText(order.status) }}</span>
              <strong class="recent-row__amount">¥{{ money(order.totalAmount) }}</strong>
            </article>
          </div>
        </section>
      </template>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppLayout from '@/layouts/AppLayout.vue'
import { getMerchantDashboard, type MerchantDashboard } from '@/api/merchantDashboard'

const loading = ref(true)
const dashboard = ref<MerchantDashboard | null>(null)

const STATUS_TEXT: Record<number, string> = {
  0: '待付款',
  1: '待发货',
  2: '待收货',
  3: '已完成',
  4: '已取消',
}

function money(value: string | number): string {
  return Number(value ?? 0).toFixed(2)
}

function statusText(status: number): string {
  return STATUS_TEXT[status] ?? '未知'
}

onMounted(async () => {
  try {
    dashboard.value = await getMerchantDashboard()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '看板加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.container { width: 100%; max-width: 1080px; margin: 0 auto; padding-inline: 24px; }
.md { padding-block: 40px 88px; min-height: 60vh; }
.md__head { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 28px; }
.md__eyebrow { margin: 0 0 6px; color: var(--color-accent); font-size: 11px; letter-spacing: 0.14em; text-transform: uppercase; }
.md__title { margin: 0; font-family: var(--font-display); font-size: clamp(1.5rem, 3vw, 2.25rem); font-weight: 400; color: var(--color-text-primary); }
.md__links { display: flex; flex-wrap: wrap; gap: 16px; font-size: 13px; }
.md__links a, .work-block__head a { color: var(--color-accent-dark); text-decoration: none; }
.md__links a:hover, .work-block__head a:hover { text-decoration: underline; }

.stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 16px; }
.stat-card { display: flex; min-width: 0; flex-direction: column; gap: 6px; padding: 20px; border: 1px solid var(--color-border-subtle); border-radius: var(--radius-lg); background: var(--color-bg-raised); }
.stat-card--primary { background: var(--color-bg-inverted); border-color: var(--color-bg-inverted); }
.stat-card span { font-size: 12px; color: var(--color-text-muted); }
.stat-card strong { font-family: var(--font-display); font-size: 28px; font-weight: 400; color: var(--color-accent-dark); white-space: nowrap; }
.stat-card small { font-size: 11px; color: var(--color-text-muted); }
.stat-card--primary span, .stat-card--primary small { color: rgba(253, 252, 248, 0.58); }
.stat-card--primary strong { color: var(--color-bg-overlay); }

.work-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-bottom: 16px; }
.work-block, .recent { padding: 22px 24px; border: 1px solid var(--color-border-subtle); border-radius: var(--radius-lg); background: var(--color-bg-raised); }
.work-block__head { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.work-block__head h2 { margin: 0; font-size: 16px; font-weight: 600; color: var(--color-text-primary); }
.work-block__head a { font-size: 13px; }
.metric-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 20px; margin: 0; }
.metric-list div { min-width: 0; }
.metric-list dt { margin-bottom: 5px; font-size: 12px; color: var(--color-text-muted); }
.metric-list dd { margin: 0; font-family: var(--font-display); font-size: 24px; color: var(--color-text-primary); }
.metric-list dd.urgent { color: var(--color-error); }

.recent-list { display: flex; flex-direction: column; }
.recent-row { display: grid; grid-template-columns: minmax(0, 1fr) 150px 70px 100px; align-items: center; gap: 12px; padding: 13px 0; border-top: 1px solid var(--color-border-subtle); }
.recent-row__main { min-width: 0; }
.recent-row__no { display: block; overflow: hidden; color: var(--color-text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.recent-row__buyer, .recent-row__time { color: var(--color-text-muted); font-size: 12px; }
.recent-row__status { color: var(--color-accent-dark); font-size: 12px; }
.recent-row__amount { color: var(--color-accent-dark); font-family: var(--font-display); font-size: 16px; text-align: right; }
.empty { padding: 32px 0; text-align: center; color: var(--color-text-secondary); font-size: 13px; }

@media (max-width: 900px) {
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .work-grid { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .container { padding-inline: 16px; }
  .md__head { align-items: flex-start; flex-direction: column; }
  .stat-grid { grid-template-columns: 1fr; }
  .recent-row { grid-template-columns: 1fr auto; }
  .recent-row__time, .recent-row__status { display: none; }
}
</style>

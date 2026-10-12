<template>
  <AppLayout>
    <div class="od-page" v-if="order">
      <h2 class="od-title">订单详情</h2>
      <div class="od-card">
        <div class="od-header">
          <span>订单号：{{ order.orderNo }}</span>
          <span class="od-header-right">
            <span v-if="order.orderType === 'SECKILL'" class="od-type">秒杀</span>
            <span class="od-status">{{ order.statusDesc }}</span>
          </span>
        </div>
        <p v-if="order.receiverName || order.receiverPhone" class="od-receiver">{{ order.receiverName }} {{ order.receiverPhone }}</p>
        <p v-if="order.receiverAddress" class="od-addr">{{ order.receiverAddress }}</p>
        <p class="od-remark" v-if="order.remark">备注：{{ order.remark }}</p>
      </div>
      
      <div class="od-progress" v-if="order.status !== 4">
        <div class="od-steps">
          <div v-for="(step, i) in odSteps" :key="i" class="od-step" :class="{ done: i <= stepIndex, active: i === stepIndex }">
            <span class="od-dot">{{ i < stepIndex ? '✔' : '●' }}</span>
            <span class="od-step-label">{{ step }}</span>
          </div>
        </div>
      </div>
      <div class="od-cancelled" v-if="order.status === 4">订单已取消</div>

      <h3>{{ order.orderType === 'PARENT' ? '商家子订单' : '商品明细' }}</h3>
      <section v-for="group in displayGroups" :key="group.key" class="od-group">
        <div v-if="group.subOrder" class="od-suborder-head">
          <div>
            <span class="od-suborder-label">店铺订单</span>
            <span class="od-suborder-no">{{ group.subOrder.orderNo }}</span>
          </div>
          <span class="od-suborder-status">{{ group.subOrder.statusDesc }}</span>
        </div>

        <div v-for="item in group.items" :key="item.id" class="od-item">
          <div class="od-item-info">
            <img v-if="item.productImage" :src="item.productImage" class="od-item-img" alt="" loading="lazy" />
            <span class="od-item-name">
              {{ item.productName }}
              <small v-if="item.skuName">{{ item.skuName }}</small>
              x{{ item.quantity }}
            </span>
            <span v-if="afterSaleText(item.id)" class="od-item-as">{{ afterSaleText(item.id) }}</span>
          </div>
          <span class="od-item-price">¥{{ Number(item.subtotal).toFixed(2) }}</span>
          <button
            v-if="canApplyAfterSale(item.id, group.subOrder?.status ?? order.status)"
            class="od-item-as-btn"
            type="button"
            @click="openAfterSale(item, group.subOrder?.status ?? order.status)"
          >申请售后</button>
          <button
            v-if="returnShipmentFor(item.id)"
            class="od-item-as-btn"
            type="button"
            @click="openReturnShipment(returnShipmentFor(item.id))"
          >填写退货物流</button>
        </div>

        <div v-if="group.subOrder" class="od-suborder-foot">
          <span>本店商品小计</span>
          <span class="od-suborder-amount">¥{{ Number(group.subOrder.totalAmount).toFixed(2) }}</span>
          <el-button
            v-if="group.subOrder.status === 2"
            type="success"
            size="small"
            :loading="acting"
            @click="onConfirmSubOrder(group.subOrder.id)"
          >确认收货</el-button>
        </div>
      </section>
      <div class="od-total">
        <span>合计</span>
        <span class="od-total-price">¥{{ Number(order.totalAmount).toFixed(2) }}</span>
      </div>
      <el-button @click="router.push('/orders')" class="back-btn">返回订单列表</el-button>
      <el-button v-if="order.orderType !== 'SECKILL' && order.status === 0" type="primary" class="pay-btn" :loading="acting" @click="onPay">立即支付</el-button>
      <el-button v-if="order.orderType !== 'SECKILL' && order.status === 0" type="warning" class="cancel-btn" :loading="acting" @click="onCancel">取消订单</el-button>
      <el-button v-if="order.orderType !== 'SECKILL' && order.orderType !== 'PARENT' && order.status === 2" type="success" class="confirm-btn" :loading="acting" @click="onConfirm">确认收货</el-button>
    </div>

    <div v-else class="loading">加载中...</div>

    <!-- 模拟收银台：真实项目里这一步会跳转到支付平台 -->
    <el-dialog v-model="payDialog" title="收银台" width="min(92vw, 420px)">
      <div class="cashier">
        <p class="cashier__label">应付金额</p>
        <p class="cashier__amount">¥{{ Number(payment?.amount ?? 0).toFixed(2) }}</p>
        <p class="cashier__no">支付单号 {{ payment?.paymentNo }}</p>
        <div class="cashier__channel">
          <span class="cashier__channel-name">模拟支付</span>
          <span class="cashier__channel-tip">演示环境，点确认即视为支付成功</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="payDialog = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="onConfirmPay">确认支付</el-button>
      </template>
    </el-dialog>

    <!-- 申请售后 -->
    <el-dialog v-model="asDialog" title="申请售后" width="min(92vw, 460px)">
      <div class="as-form">
        <p class="as-form__product">{{ asForm.productName }}</p>
        <p class="as-form__tip">
          {{ asForm.type === 2
            ? '退货退款：商家同意后需要寄回商品，商家确认收货后退款。'
            : '仅退款：不需要寄回商品，商家同意后款项原路退回。' }}
          最多可退 ¥{{ asForm.maxAmount.toFixed(2) }}。
        </p>

        <label class="as-form__label">售后类型</label>
        <el-radio-group v-model="asForm.type">
          <el-radio-button :label="1">仅退款</el-radio-button>
          <el-radio-button :label="2" :disabled="asForm.childStatus === 1">退货退款</el-radio-button>
        </el-radio-group>

        <label class="as-form__label">申请原因</label>
        <el-input v-model="asForm.reason" maxlength="100" placeholder="例如：不想要了 / 拍错了" />

        <label class="as-form__label">退款金额</label>
        <el-input-number
          v-model="asForm.amount"
          :min="0.01"
          :max="asForm.maxAmount"
          :precision="2"
          :step="1"
          style="width: 100%"
        />

        <label class="as-form__label">补充说明（选填）</label>
        <el-input v-model="asForm.description" type="textarea" :rows="3" maxlength="200" show-word-limit />
      </div>
      <template #footer>
        <el-button @click="asDialog = false">取消</el-button>
        <el-button type="primary" :loading="asSubmitting" @click="submitAfterSale">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 退货退款：买家寄回后填写物流 -->
    <el-dialog v-model="returnDialog" title="填写退货物流" width="min(92vw, 440px)">
      <div class="as-form">
        <p class="as-form__product">{{ returnForm.productName }}</p>
        <p class="as-form__tip">请如实填写退货物流，商家确认收货后退款会自动原路退回。</p>

        <label class="as-form__label">物流公司</label>
        <el-input v-model="returnForm.returnCompany" maxlength="50" placeholder="例如：顺丰速运" />

        <label class="as-form__label">物流单号</label>
        <el-input v-model="returnForm.returnTrackingNo" maxlength="100" placeholder="请输入物流单号" />
      </div>
      <template #footer>
        <el-button @click="returnDialog = false">取消</el-button>
        <el-button type="primary" :loading="returnSubmitting" @click="submitReturnShipment">提交物流</el-button>
      </template>
    </el-dialog>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { getOrderDetail, getSeckillOrderDetail, cancelOrder, confirmOrder } from '@/api/order'
import type { OrderDetailVO, SubOrderVO } from '@/api/order'
import { createPayment, payPayment } from '@/api/payment'
import type { PaymentVO } from '@/api/payment'
import {
  applyAfterSale, listMyAfterSales, submitAfterSaleReturn, AFTER_SALE_STATUS,
} from '@/api/afterSale'
import type { AfterSale } from '@/api/afterSale'

const route = useRoute()
const router = useRouter()
const order = ref<OrderDetailVO | null>(null)
const acting = ref(false)
const payDialog = ref(false)
const paying = ref(false)
const payment = ref<PaymentVO | null>(null)

// ── 售后：按订单明细展示状态、按件申请 ──
const myAfterSales = ref<AfterSale[]>([])
const asDialog = ref(false)
const asSubmitting = ref(false)
const asForm = ref({
  orderItemId: 0,
  productName: '',
  maxAmount: 0,
  childStatus: 0,
  type: 1,
  reason: '',
  description: '',
  amount: 0,
})
const returnDialog = ref(false)
const returnSubmitting = ref(false)
const returnForm = ref({ id: 0, productName: '', returnCompany: '', returnTrackingNo: '' })

const asMap = computed(() => {
  const m: Record<number, AfterSale> = {}
  myAfterSales.value.forEach((a) => {
    if (!m[a.orderItemId]) m[a.orderItemId] = a
  })
  return m
})

const displayGroups = computed(() => {
  const subOrders = order.value?.subOrders ?? []
  if (subOrders.length > 0) {
    return subOrders.map((subOrder) => ({
      key: `sub-${subOrder.id}`,
      subOrder,
      items: subOrder.items ?? [],
    }))
  }
  return [{ key: 'main', subOrder: null as SubOrderVO | null, items: order.value?.items ?? [] }]
})

function afterSaleText(itemId: number) {
  const a = asMap.value[itemId]
  return a ? (AFTER_SALE_STATUS[a.status] ?? '') : ''
}

// 已付款的订单才能申请；同一条明细只有在「被拒绝」或「已撤销」后才能再申请
function canApplyAfterSale(itemId: number, status?: number) {
  const st = status ?? order.value?.status
  if (st == null || ![1, 2, 3].includes(st)) return false
  const a = asMap.value[itemId]
  return !a || a.status === 2 || a.status === 4
}

function returnShipmentFor(itemId: number) {
  const a = asMap.value[itemId]
  return a?.type === 2 && a.status === 1 ? a : null
}

function openAfterSale(item: any, status = order.value?.status ?? 0) {
  asForm.value = {
    orderItemId: item.id,
    productName: item.productName,
    maxAmount: Number(item.subtotal),
    childStatus: status,
    type: 1,
    reason: '',
    description: '',
    amount: Number(item.subtotal),
  }
  asDialog.value = true
}

async function submitAfterSale() {
  if (!asForm.value.reason.trim()) {
    ElMessage.warning('请填写申请原因')
    return
  }
  asSubmitting.value = true
  try {
    await applyAfterSale({
      orderItemId: asForm.value.orderItemId,
      type: asForm.value.type,
      reason: asForm.value.reason.trim(),
      description: asForm.value.description.trim() || undefined,
      amount: asForm.value.amount,
    })
    asDialog.value = false
    ElMessage.success('已提交，等待商家处理')
    await loadAfterSales()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '申请失败')
  } finally {
    asSubmitting.value = false
  }
}

function openReturnShipment(a: AfterSale | null) {
  if (!a) return
  returnForm.value = {
    id: a.id,
    productName: order.value?.items.find((item) => item.id === a.orderItemId)?.productName || '退货商品',
    returnCompany: '',
    returnTrackingNo: '',
  }
  returnDialog.value = true
}

async function submitReturnShipment() {
  const company = returnForm.value.returnCompany.trim()
  const trackingNo = returnForm.value.returnTrackingNo.trim()
  if (!company || !trackingNo) {
    ElMessage.warning('请填写物流公司和物流单号')
    return
  }
  returnSubmitting.value = true
  try {
    await submitAfterSaleReturn(returnForm.value.id, {
      returnCompany: company,
      returnTrackingNo: trackingNo,
    })
    returnDialog.value = false
    ElMessage.success('退货物流已提交，等待商家确认收货')
    await loadAfterSales()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '提交失败')
  } finally {
    returnSubmitting.value = false
  }
}

async function loadAfterSales() {
  try {
    myAfterSales.value = await listMyAfterSales()
  } catch { /* 售后列表失败不影响订单详情展示 */ }
}

onMounted(async () => {
  try {
    order.value = route.query.type === 'SECKILL'
      ? await getSeckillOrderDetail(String(route.params.id))
      : await getOrderDetail(Number(route.params.id))
  } catch {
    ElMessage.error('订单加载失败')
  }
    // 顺带拉一次我的售后，用来在明细行上标注「退款完成 / 待商家处理」等状态
    loadAfterSales()
})

const odSteps = ['待付款', '待发货', '待收货', '已完成']
const stepIndex = computed(() => Math.min(order.value?.status ?? 0, 3))

async function onCancel() {
  if (!order.value) return
  try {
    await ElMessageBox.confirm('确定要取消该订单吗？', '提示')
    acting.value = true
    await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
    order.value = await getOrderDetail(order.value.id)
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.response?.data?.message || e?.message || '操作失败')
    }
  } finally {
    acting.value = false
  }
}

// 第一步：创建支付单，弹出收银台
async function onPay() {
  if (!order.value) return
  acting.value = true
  try {
    payment.value = await createPayment(order.value.id)
    payDialog.value = true
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '发起支付失败')
  } finally {
    acting.value = false
  }
}

// 第二步：模拟第三方回调。真实项目里这一步由支付平台异步通知触发，
// 后端做了幂等，重复点也不会重复处理。
async function onConfirmPay() {
  const orderId = order.value?.id
  if (!payment.value || !orderId) return
  paying.value = true
  try {
    await payPayment(payment.value.paymentNo)
    payDialog.value = false
    ElMessage.success('支付成功')
    order.value = await getOrderDetail(orderId)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '支付失败')
  } finally {
    paying.value = false
  }
}

async function onConfirm() {
  if (!order.value) return
  try {
    await ElMessageBox.confirm('确认已收到商品？', '提示')
    acting.value = true
    await confirmOrder(order.value.id)
    ElMessage.success('交易完成')
    order.value = await getOrderDetail(order.value.id)
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.response?.data?.message || e?.message || '操作失败')
    }
  } finally {
    acting.value = false
  }
}

async function onConfirmSubOrder(orderId: number) {
  try {
    await ElMessageBox.confirm('确认已收到该店铺商品？', '提示')
    acting.value = true
    await confirmOrder(orderId)
    ElMessage.success('该店铺订单已确认收货')
    if (order.value) {
      order.value = await getOrderDetail(order.value.id)
    }
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.response?.data?.message || e?.message || '操作失败')
    }
  } finally {
    acting.value = false
  }
}
</script>

<style scoped>
.od-page {
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 24px 88px;
}
.loading { text-align: center; padding: 80px 0; color: var(--color-text-secondary); }
.od-title {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-text-primary);
  margin-bottom: 24px;
}
.od-card {
  padding: 24px;
  margin-bottom: 16px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
}
.od-header { display: flex; justify-content: space-between; font-size: 13px; color: var(--color-text-secondary); margin-bottom: 8px; }
.od-header-right { display: flex; align-items: center; gap: 8px; }
.od-type { padding: 3px 6px; border-radius: 4px; background: var(--color-error); color: #fff; font-size: 10px; }
.od-status { color: var(--color-accent); font-weight: 600; }
.od-receiver { font-size: 14px; color: var(--color-text-primary); margin: 4px 0; }
.od-addr { font-size: 13px; color: var(--color-text-secondary); margin: 0; }
.od-remark { font-size: 13px; color: var(--color-text-secondary); margin: 8px 0 0; }
h3 { font-size: 15px; color: var(--color-text-primary); margin: 20px 0 12px; }
.od-group {
  margin-bottom: 14px;
  padding: 16px 18px 12px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
}
.od-suborder-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border-subtle);
}
.od-suborder-label { margin-right: 8px; font-size: 12px; color: var(--color-text-muted); }
.od-suborder-no { font-size: 13px; color: var(--color-text-primary); }
.od-suborder-status { font-size: 13px; color: var(--color-accent-dark); }
.od-suborder-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.od-suborder-amount {
  font-family: var(--font-display);
  font-size: 20px;
  color: var(--color-accent-dark);
}
.od-item { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--color-border-subtle); font-size: 13px; color: var(--color-text-primary); }
.od-item-info { display: flex; align-items: center; gap: 10px; min-width: 0; }
.od-item-img { width: 40px; height: 40px; border-radius: 8px; object-fit: cover; flex-shrink: 0; }
.od-item-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.od-item-name small { margin-left: 6px; color: var(--color-text-muted); font-size: 11px; }
.od-item-price { font-family: var(--font-display); color: var(--color-accent-dark); }
.od-total { display: flex; justify-content: space-between; padding: 12px 0; font-size: 15px; font-weight: 600; }
.od-total-price { font-family: var(--font-display); font-size: 24px; color: var(--color-accent-dark); }
.back-btn { width: 100%; margin-top: 16px; border-radius: var(--radius-full); }
.pay-btn { width: 100%; height: 48px; margin-top: 12px; border-radius: var(--radius-full); }
.cancel-btn { width: 100%; height: 48px; margin-top: 12px; border-radius: var(--radius-full); }
.confirm-btn { width: 100%; height: 48px; margin-top: 12px; border-radius: var(--radius-full); }
.od-progress { margin: 16px 0; }
.od-steps { display: flex; justify-content: space-between; position: relative; padding: 0 8px; }
.od-step { text-align: center; flex: 1; position: relative; }
.od-dot { font-size: 18px; display: block; margin-bottom: 4px; }
.od-step.done .od-dot { color: var(--color-accent); }
.od-step:not(.done) .od-dot { color: var(--color-border); }
.od-step.active .od-dot { color: var(--color-accent); }
.od-step-label { font-size: 11px; color: var(--color-text-secondary); }
.od-step.done .od-step-label { color: var(--color-accent); }
.od-cancelled { text-align: center; padding: 16px; color: var(--color-text-secondary); border: 1px dashed var(--color-border); border-radius: 8px; margin-bottom: 16px; }

/* 收银台 */
.cashier { text-align: center; padding: 8px 0 4px; }
.cashier__label { font-size: 13px; color: var(--color-text-muted); margin-bottom: 6px; }
.cashier__amount {
  font-family: var(--font-display);
  font-size: 38px;
  color: var(--color-accent-dark);
  margin-bottom: 8px;
  font-variant-numeric: tabular-nums;
}
.cashier__no { font-size: 12px; color: var(--color-text-muted); margin-bottom: 20px; }
.cashier__channel {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border: 1.5px solid var(--color-accent);
  border-radius: var(--radius-lg);
  background: var(--color-accent-dim);
  text-align: left;
}
.cashier__channel-name { font-size: 15px; color: var(--color-text-primary); }
.cashier__channel-tip { font-size: 12px; color: var(--color-text-secondary); }

/* 明细行上的售后标记与入口 */
.od-item-as {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  background: var(--color-accent-dim);
  color: var(--color-accent-warm, var(--color-accent-dark));
  font-size: 11px;
  white-space: nowrap;
}
.od-item-as-btn {
  flex-shrink: 0;
  margin-left: 12px;
  padding: 4px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: transparent;
  color: var(--color-text-secondary);
  font-family: var(--font-body);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}
.od-item-as-btn:hover { border-color: var(--color-accent); color: var(--color-accent-dark); }

/* 售后申请表单 */
.as-form { display: flex; flex-direction: column; gap: 8px; }
.as-form__product {
  font-size: 16px;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}
.as-form__tip {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}
.as-form__label {
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>

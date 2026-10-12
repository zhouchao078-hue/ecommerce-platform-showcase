<template>
  <AppLayout>
    <div class="sk-page">
      <div class="sk-heading">
        <div>
          <p class="sk-eyebrow">LIMITED OFFERS</p>
          <h1 class="sk-title">限时秒杀</h1>
          <p class="sk-subtitle">精选商品限量开抢</p>
        </div>
        <el-button class="sk-refresh" :loading="loading" @click="fetchActivities">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>

      <section class="sk-create" aria-labelledby="create-title">
        <div class="sk-section-heading">
          <div>
            <h2 id="create-title">创建秒杀活动</h2>
            <p>为你管理的商品设置价格、库存和活动时间</p>
          </div>
          <el-button
            v-if="!isMerchantFormOpen"
            class="sk-primary"
            :loading="merchantProductsLoading"
            @click="openCreateForm"
          >
            <el-icon><Plus /></el-icon>
            添加秒杀
          </el-button>
        </div>

        <el-form
          v-if="isMerchantFormOpen"
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="sk-form"
          @submit.prevent
        >
          <el-form-item label="商品" prop="productId" class="sk-product-field">
            <el-select v-model="form.productId" placeholder="选择你管理的商品" filterable>
              <el-option
                v-for="product in merchantProducts"
                :key="product.id"
                :label="`${product.name} · ¥${Number(product.price).toFixed(2)}`"
                :value="product.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="秒杀价格（元）" prop="seckillPrice">
            <el-input-number v-model="form.seckillPrice" :min="0.01" :precision="2" :step="1" controls-position="right" />
          </el-form-item>
          <el-form-item label="活动库存" prop="stock">
            <el-input-number v-model="form.stock" :min="1" :precision="0" :step="1" controls-position="right" />
          </el-form-item>
          <el-form-item label="开始时间" prop="startTime">
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              placeholder="选择开始时间"
            />
          </el-form-item>
          <el-form-item label="结束时间" prop="endTime">
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              placeholder="选择结束时间"
            />
          </el-form-item>
          <div class="sk-form-actions">
            <el-button @click="isMerchantFormOpen = false">取消</el-button>
            <el-button class="sk-primary" :loading="saving" @click="submitActivity">创建活动</el-button>
          </div>
        </el-form>
        <el-empty
          v-else-if="merchantProductsLoaded && merchantProducts.length === 0"
          description="暂无可用商品，请先在商品管理中添加商品"
          :image-size="72"
        />
      </section>

      <section class="sk-activity-section" aria-labelledby="activity-title">
        <div class="sk-list-heading">
          <h2 id="activity-title">正在进行的活动</h2>
          <span>{{ activities.length }} 件商品</span>
        </div>
        <div v-if="loading" class="sk-loading">正在加载活动...</div>
        <el-empty v-else-if="activities.length === 0" description="当前没有秒杀活动" :image-size="90" />
        <div v-else class="sk-grid">
          <article v-for="item in activities" :key="item.id" class="sk-card">
            <div class="sk-image-wrap">
              <img v-if="firstImage(item.images)" :src="firstImage(item.images)" :alt="item.name" />
              <div v-else class="sk-image-fallback">{{ item.name?.slice(0, 1) || '秒' }}</div>
              <span class="sk-tag">限时优惠</span>
            </div>
            <div class="sk-card-body">
              <h3>{{ item.name }}</h3>
              <p class="sk-description">{{ item.description || '精选商品限量秒杀' }}</p>
              <div class="sk-price-line">
                <strong>¥{{ Number(item.seckillPrice).toFixed(2) }}</strong>
                <del>¥{{ Number(item.originalPrice).toFixed(2) }}</del>
              </div>
              <div class="sk-card-meta">
                <span>剩余 {{ item.stock }} 件</span>
                <span>{{ activityTime(item) }}</span>
              </div>
              <el-button
                class="sk-buy"
                :disabled="item.stock <= 0 || isEnded(item)"
                :loading="purchasingId === item.id"
                @click="purchase(item)"
              >
                {{ item.stock <= 0 ? '已售罄' : isEnded(item) ? '活动结束' : '立即抢购' }}
              </el-button>
            </div>
          </article>
        </div>
      </section>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { listMerchantProducts } from '@/api/product'
import type { Product } from '@/api/product'
import { firstProductImage } from '@/utils/productImages'
import {
  createSeckillActivity,
  listSeckillProducts,
  purchaseSeckillProduct,
} from '@/api/seckill'
import type { SeckillProduct } from '@/api/seckill'

const activities = ref<SeckillProduct[]>([])
const merchantProducts = ref<Product[]>([])
const loading = ref(false)
const saving = ref(false)
const purchasingId = ref<number | null>(null)
const merchantProductsLoading = ref(false)
const merchantProductsLoaded = ref(false)
const isMerchantFormOpen = ref(false)
const formRef = ref<FormInstance>()

const emptyForm = () => ({
  productId: undefined as number | undefined,
  seckillPrice: 0.01,
  stock: 1,
  startTime: '',
  endTime: '',
})
const form = reactive(emptyForm())

const rules: FormRules = {
  productId: [{ required: true, message: '请选择商品', trigger: 'change' }],
  seckillPrice: [{ required: true, message: '请输入秒杀价格', trigger: 'change' }],
  stock: [{ required: true, message: '请输入活动库存', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}

async function fetchActivities() {
  loading.value = true
  try {
    activities.value = await listSeckillProducts()
  } catch (error: any) {
    ElMessage.error(error?.message || '秒杀活动加载失败')
  } finally {
    loading.value = false
  }
}

async function openCreateForm() {
  isMerchantFormOpen.value = true
  if (merchantProductsLoaded.value) return
  merchantProductsLoading.value = true
  try {
    merchantProducts.value = await listMerchantProducts({ pageNum: 1, pageSize: 100 })
    merchantProductsLoaded.value = true
  } catch (error: any) {
    ElMessage.error(error?.message || '商户商品加载失败，请确认当前账号已开通商户')
  } finally {
    merchantProductsLoading.value = false
  }
}

async function submitActivity() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    if (new Date(form.endTime).getTime() <= new Date(form.startTime).getTime()) {
      ElMessage.warning('结束时间必须晚于开始时间')
      return
    }
    if (!form.productId) return

    saving.value = true
    await createSeckillActivity({
      productId: form.productId,
      seckillPrice: form.seckillPrice,
      stock: form.stock,
      startTime: form.startTime,
      endTime: form.endTime,
    })
    ElMessage.success('秒杀活动创建成功')
    Object.assign(form, emptyForm())
    isMerchantFormOpen.value = false
    await fetchActivities()
  } catch (error: any) {
    if (error?.message) ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

async function purchase(item: SeckillProduct) {
  purchasingId.value = item.id
  try {
    const order = await purchaseSeckillProduct(item.id)
    ElMessage.success(`抢购成功，订单号：${order.orderNo}`)
    await fetchActivities()
  } catch (error: any) {
    ElMessage.error(error?.message || '抢购失败')
  } finally {
    purchasingId.value = null
  }
}

function firstImage(raw?: string | null): string {
  return firstProductImage(raw)
}

function isEnded(item: SeckillProduct): boolean {
  return new Date(item.endTime.replace(' ', 'T')).getTime() <= Date.now()
}

function activityTime(item: SeckillProduct): string {
  const start = new Date(item.startTime.replace(' ', 'T'))
  const end = new Date(item.endTime.replace(' ', 'T'))
  if (Date.now() < start.getTime()) return `开始于 ${start.toLocaleString()}`
  if (Date.now() >= end.getTime()) return '活动已结束'
  return `结束于 ${end.toLocaleString()}`
}

onMounted(fetchActivities)
</script>

<style scoped>
.sk-page { width: 100%; max-width: 1200px; margin: 0 auto; padding: 40px 24px 88px; color: var(--color-text-primary); }
.sk-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 28px; }
.sk-eyebrow { margin: 0 0 5px; color: #a18b6c; font: 600 10px 'Inter', sans-serif; letter-spacing: 1.4px; }
.sk-title { margin: 0; color: var(--color-text-primary); font-family: var(--font-display); font-size: clamp(1.5rem, 3vw, 2.25rem); font-weight: 400; line-height: 1.5; }
.sk-subtitle { margin: 5px 0 0; color: var(--color-text-secondary); font-size: 13px; }
.sk-refresh { color: #6e665b; border-color: var(--color-border); background: rgba(255,255,255,.6); }
.sk-create { padding: 18px 0 24px; border-top: 1px solid var(--color-border); border-bottom: 1px solid var(--color-border); }
.sk-section-heading, .sk-list-heading { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.sk-section-heading h2, .sk-list-heading h2 { margin: 0; color: var(--color-text-primary); font-size: 17px; font-weight: 650; }
.sk-section-heading p { margin: 5px 0 0; color: var(--color-text-secondary); font-size: 12px; }
.sk-primary { --el-button-bg-color: var(--color-accent); --el-button-border-color: var(--color-accent); --el-button-hover-bg-color: var(--color-accent-darker); --el-button-hover-border-color: var(--color-accent-darker); color: #fff; border-radius: 6px; }
.sk-form { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); column-gap: 16px; margin-top: 20px; }
.sk-form :deep(.el-form-item) { min-width: 0; margin-bottom: 14px; }
.sk-form :deep(.el-select), .sk-form :deep(.el-input-number), .sk-form :deep(.el-date-editor) { width: 100%; }
.sk-product-field { grid-column: span 3; }
.sk-form-actions { display: flex; align-items: end; justify-content: flex-end; gap: 8px; padding-bottom: 14px; }
.sk-activity-section { padding-top: 24px; }
.sk-list-heading { margin-bottom: 16px; }
.sk-list-heading span { color: var(--color-text-muted); font-size: 12px; }
.sk-loading { padding: 48px 0; color: var(--color-text-secondary); text-align: center; font-size: 13px; }
.sk-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.sk-card { min-width: 0; overflow: hidden; border: 1px solid var(--color-border-subtle); border-radius: var(--radius-lg); background: var(--color-bg-raised); }
.sk-image-wrap { position: relative; height: 180px; overflow: hidden; background: var(--color-bg-raised); }
.sk-image-wrap img { width: 100%; height: 100%; object-fit: cover; }
.sk-image-fallback { display: grid; width: 100%; height: 100%; place-items: center; color: #b6a487; font: 600 42px 'Inter', serif; }
.sk-tag { position: absolute; top: 10px; left: 10px; padding: 5px 8px; border-radius: 4px; background: var(--color-accent); color: #fff; font-size: 11px; }
.sk-card-body { padding: 14px; }
.sk-card-body h3 { overflow: hidden; margin: 0; color: var(--color-text-primary); font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.sk-description { overflow: hidden; min-height: 34px; margin: 6px 0 12px; color: var(--color-text-secondary); font-size: 12px; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.sk-price-line { display: flex; align-items: baseline; gap: 8px; }
.sk-price-line strong { color: var(--color-error); font: 700 21px 'Inter', serif; }
.sk-price-line del { color: var(--color-text-muted); font-size: 12px; }
.sk-card-meta { display: flex; flex-direction: column; gap: 4px; margin: 12px 0; color: var(--color-text-secondary); font-size: 11px; }
.sk-buy { width: 100%; border-color: var(--color-accent); background: var(--color-accent); color: #fff; border-radius: 6px; }
.sk-buy:hover { border-color: var(--color-accent-darker); background: var(--color-accent-darker); color: #fff; }
.sk-buy.is-disabled { border-color: var(--color-border); background: var(--color-border); color: #fff; }
@media (max-width: 760px) { .sk-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) {
  .sk-page { padding: 22px 14px 36px; }
  .sk-heading { align-items: flex-start; }
  .sk-title { font-size: 23px; }
  .sk-section-heading { align-items: flex-start; }
  .sk-section-heading p { max-width: 230px; line-height: 1.5; }
  .sk-form { grid-template-columns: 1fr; }
  .sk-product-field { grid-column: auto; }
  .sk-form-actions { grid-column: auto; }
  .sk-grid { grid-template-columns: 1fr; }
  .sk-image-wrap { height: 210px; }
}
</style>

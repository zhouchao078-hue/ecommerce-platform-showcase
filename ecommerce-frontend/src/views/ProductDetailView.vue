<template>
  <AppLayout>
    <div class="detail container" v-if="product">
      <button class="back" type="button" @click="router.back()">
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M10 3L5 8l5 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        返回
      </button>

      <div class="detail__grid">
        <div class="detail__gallery">
          <div class="detail__media">
            <img v-if="activeImage" :src="activeImage" class="detail__img" alt="" />
            <span v-else class="detail__placeholder">{{ product.name.charAt(0) }}</span>
          </div>
          <div v-if="images.length > 1" class="detail__thumbs">
            <button
              v-for="image in images"
              :key="image"
              type="button"
              class="detail__thumb"
              :class="{ active: activeImage === image }"
              @click="activeImage = image"
            >
              <img :src="image" alt="" />
            </button>
          </div>
        </div>

        <div class="detail__info">
          <h1 class="detail__name">{{ product.name }}</h1>
          <p class="detail__price">{{ Number(displayPrice).toFixed(2) }}</p>

          <p v-if="product.description" class="detail__desc">{{ product.description }}</p>

          <div v-if="product.skus?.length" class="sku-block">
            <p class="sku-block__label">选择规格</p>
            <div class="sku-options">
              <button
                v-for="sku in product.skus"
                :key="sku.id"
                type="button"
                class="sku-option"
                :class="{ active: selectedSkuId === sku.id, disabled: sku.stock <= 0 }"
                :disabled="sku.stock <= 0"
                @click="selectSku(sku)"
              >
                {{ sku.skuName }}
              </button>
            </div>
          </div>

          <dl class="detail__meta">
            <div>
              <dt>库存</dt>
              <dd>{{ displayStock }} 件</dd>
            </div>
          </dl>

          <!-- 商品归属的商家：可点击进入店铺主页 -->
          <router-link v-if="shop" class="detail__shop" :to="'/shops/' + shop.id">
            <span class="detail__shop-label">店铺</span>
            <span class="detail__shop-name">{{ shop.shopName }}</span>
            <span class="detail__shop-arrow" aria-hidden="true">→</span>
          </router-link>

          <div class="detail__actions">
            <el-input-number
              v-model="qty"
              :min="1"
              :max="Math.max(displayStock, 1)"
              size="large"
              class="qty"
            />
            <el-button
              type="primary"
              size="large"
              class="add-btn"
              :loading="adding"
              :disabled="displayStock <= 0"
              @click="onAddCart"
            >
              {{ displayStock > 0 ? '加入购物车' : '暂时缺货' }}
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="loading">加载中…</div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppLayout from '@/layouts/AppLayout.vue'
import { getProductById, addToCart } from '@/api/product'
import type { Product, ProductSku } from '@/api/product'
import { getShop } from '@/api/shop'
import type { Shop } from '@/api/shop'
import { parseProductImages } from '@/utils/productImages'

const route = useRoute()
const router = useRouter()
const product = ref<Product | null>(null)
const shop = ref<Shop | null>(null)
const selectedSkuId = ref<number | null>(null)
const activeImage = ref('')
const qty = ref(1)
const adding = ref(false)

const selectedSku = computed(() => {
  const skus = product.value?.skus ?? []
  return skus.find((sku) => sku.id === selectedSkuId.value) ?? skus[0] ?? null
})
const displayPrice = computed(() => selectedSku.value?.price ?? product.value?.price ?? 0)
const displayStock = computed(() => selectedSku.value?.stock ?? product.value?.stock ?? 0)

const images = computed(() => parseProductImages(product.value?.images))

onMounted(async () => {
  try {
    product.value = await getProductById(Number(route.params.id))
    selectedSkuId.value = product.value?.skus?.[0]?.id ?? null
    activeImage.value = images.value[0] ?? ''
    // 商品带了 merchantId，用它去取店铺信息（店铺接口是公开的）
    if (product.value?.merchantId) {
      getShop(product.value.merchantId)
        .then((s) => { shop.value = s })
        .catch(() => { /* 店铺信息失败不影响商品详情 */ })
    }
  } catch {
    ElMessage.error('商品加载失败')
  }
})

function selectSku(sku: ProductSku) {
  selectedSkuId.value = sku.id ?? null
  qty.value = 1
  if (sku.image) activeImage.value = sku.image
}

async function onAddCart() {
  if (!product.value) return
  adding.value = true
  try {
    await addToCart({
      productId: product.value.id,
      skuId: selectedSku.value?.id,
      quantity: qty.value,
    })
    ElMessage.success('已加入购物车')
    window.dispatchEvent(new Event('cart-updated'))
  } catch {
    ElMessage.error('加购失败')
  } finally {
    adding.value = false
  }
}
</script>

<style scoped>
.container {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding-inline: 24px;
}

.detail {
  padding-block: 32px 88px;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 24px;
  padding: 6px 0;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  font-family: var(--font-body);
  font-size: 14px;
  cursor: pointer;
  transition: color 0.15s ease;
}
.back:hover { color: var(--color-text-primary); }

.detail__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 40px;
  align-items: start;
}
@media (min-width: 900px) {
  .detail__grid { grid-template-columns: 1.618fr 1fr; gap: 64px; }
}

.detail__gallery {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.detail__media {
  aspect-ratio: 1 / 1;
  display: grid;
  place-items: center;
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
  border: 1px solid var(--color-border-subtle);
  overflow: hidden;
}

.detail__thumbs {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}
.detail__thumb {
  aspect-ratio: 1 / 1;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--color-border-subtle);
  border-radius: 8px;
  background: var(--color-bg-raised);
  cursor: pointer;
}
.detail__thumb.active { border-color: var(--color-accent); }
.detail__thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }

.detail__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.detail__placeholder {
  font-family: var(--font-display);
  font-size: 120px;
  color: var(--color-border-strong);
  user-select: none;
}

.detail__info {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-top: 8px;
}

.detail__name {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 400;
  line-height: 1.4;
  color: var(--color-text-primary);
  word-break: keep-all;
}

.detail__price {
  font-family: var(--font-display);
  font-size: 32px;
  color: var(--color-accent-dark);
  font-variant-numeric: tabular-nums;
}
.detail__price::before {
  content: '¥';
  font-size: 0.6em;
  margin-right: 2px;
}

.detail__desc {
  font-size: 16px;
  line-height: 1.75;
  color: var(--color-text-secondary);
  max-width: 44ch;
}
.sku-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.sku-block__label {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.sku-options { display: flex; flex-wrap: wrap; gap: 8px; }
.sku-option {
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: transparent;
  color: var(--color-text-secondary);
  font-family: var(--font-body);
  font-size: 13px;
  cursor: pointer;
}
.sku-option:hover:not(:disabled) { border-color: var(--color-accent); color: var(--color-accent-dark); }
.sku-option.active {
  border-color: var(--color-accent);
  background: var(--color-accent-dim);
  color: var(--color-accent-dark);
}
.sku-option:disabled { opacity: 0.45; cursor: not-allowed; text-decoration: line-through; }

.detail__meta {
  display: flex;
  gap: 32px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border-subtle);
}
.detail__meta dt {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  margin-bottom: 4px;
}
.detail__meta dd {
  margin: 0;
  font-size: 15px;
  color: var(--color-text-primary);
}

.detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}

/* 店铺入口：整行可点，hover 时边框变深并轻微右移箭头 */
.detail__shop {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
  text-decoration: none;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}
.detail__shop:hover {
  border-color: var(--color-border-strong);
  background: var(--color-bg-overlay);
}

.detail__shop-label {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.detail__shop-name {
  flex: 1;
  font-family: var(--font-display);
  font-size: 18px;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail__shop-arrow {
  color: var(--color-accent);
  transition: transform 0.15s ease;
}
.detail__shop:hover .detail__shop-arrow { transform: translateX(3px); }

.qty { width: 132px; }

.add-btn {
  flex: 1;
  min-width: 180px;
  height: 46px;
  font-size: 15px;
  border-radius: var(--radius-full);
}

.loading {
  text-align: center;
  padding: 96px 0;
  color: var(--color-text-secondary);
}
</style>

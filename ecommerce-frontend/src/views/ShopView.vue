<template>
  <AppLayout>
    <div class="shop container">
      <button class="back" type="button" @click="router.back()">
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M10 3L5 8l5 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        返回
      </button>

      <div v-if="loading" class="loading">加载中…</div>

      <template v-else-if="shop">
        <!-- 店铺头部 -->
        <header class="shop__head">
          <div class="shop__intro">
            <h1 class="shop__name">{{ shop.shopName }}</h1>
            <p v-if="shop.description" class="shop__desc">{{ shop.description }}</p>
            <p class="shop__meta">
              <span>{{ shop.productCount }} 件在售</span>
              <span class="dot">·</span>
              <span>{{ shop.followerCount }} 人关注</span>
            </p>
          </div>

          <button
            class="follow-btn"
            :class="{ 'is-following': following }"
            type="button"
            :disabled="followLoading"
            @click="onToggleFollow"
          >
            {{ following ? '已关注' : '+ 关注店铺' }}
          </button>
        </header>

        <!-- 商品列表 -->
        <h2 class="shop__section">全部商品</h2>
        <div class="product-grid">
          <ProductCard
            v-for="p in products"
            :key="p.id"
            :product="p"
            @click="router.push('/products/' + p.id)"
          />
        </div>
        <p v-if="!loading && products.length === 0" class="empty">这家店暂时没有在售商品。</p>
      </template>

      <p v-else class="empty">店铺不存在或已关闭。</p>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppLayout from '@/layouts/AppLayout.vue'
import ProductCard from '@/components/ProductCard.vue'
import { getShop, getShopProducts, followMerchant, unfollowMerchant } from '@/api/shop'
import type { Shop } from '@/api/shop'
import type { Product } from '@/api/product'

const route = useRoute()
const router = useRouter()

const shopId = Number(route.params.id)
const shop = ref<Shop | null>(null)
const products = ref<Product[]>([])
const loading = ref(true)
const followLoading = ref(false)
const following = ref(false)

onMounted(async () => {
  try {
    const [info, list] = await Promise.all([
      getShop(shopId),
      getShopProducts(shopId),
    ])
    shop.value = info
    following.value = !!info.following
    products.value = list
  } catch {
    shop.value = null
  } finally {
    loading.value = false
  }
})

async function onToggleFollow() {
  if (!shop.value) return
  followLoading.value = true
  try {
    if (following.value) {
      await unfollowMerchant(shop.value.id)
      following.value = false
      shop.value.followerCount = Math.max(0, shop.value.followerCount - 1)
      ElMessage.success('已取消关注')
    } else {
      await followMerchant(shop.value.id)
      following.value = true
      shop.value.followerCount += 1
      ElMessage.success('已关注，店铺上新时会通知你')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    followLoading.value = false
  }
}
</script>

<style scoped>
.container { width: 100%; max-width: 1200px; margin: 0 auto; padding-inline: 24px; }
.shop { padding-block: 32px 88px; }

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
}
.back:hover { color: var(--color-text-primary); }

.loading, .empty { text-align: center; padding: 80px 0; color: var(--color-text-secondary); }

.shop__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 32px;
  margin-bottom: 40px;
  border-bottom: 1px solid var(--color-border-subtle);
}

.shop__intro { display: flex; flex-direction: column; gap: 12px; max-width: 44ch; }

.shop__name {
  font-family: var(--font-display);
  font-size: clamp(1.75rem, 3.4vw, 2.75rem);
  font-weight: 400;
  line-height: 1.35;
  color: var(--color-text-primary);
  word-break: keep-all;
}

.shop__desc {
  font-size: 16px;
  line-height: 1.75;
  color: var(--color-text-secondary);
}

.shop__meta { font-size: 14px; color: var(--color-text-muted); }
.shop__meta .dot { margin: 0 8px; }

.follow-btn {
  flex-shrink: 0;
  min-height: 46px;
  padding: 0 28px;
  border: 1px solid var(--color-accent);
  border-radius: var(--radius-full);
  background: var(--color-accent);
  color: #fff;
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}
.follow-btn:hover:not(:disabled) { background: var(--color-accent-dark); border-color: var(--color-accent-dark); }
.follow-btn:disabled { opacity: 0.6; cursor: progress; }

/* 已关注：改为描边，视觉上「可取消」而不是继续强调 */
.follow-btn.is-following {
  background: transparent;
  border-color: var(--color-border);
  color: var(--color-text-secondary);
}
.follow-btn.is-following:hover:not(:disabled) {
  border-color: var(--color-border-strong);
  background: var(--color-bg-raised);
  color: var(--color-text-primary);
}

.shop__section {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 400;
  color: var(--color-text-primary);
  margin-bottom: 24px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}
@media (min-width: 720px)  { .product-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (min-width: 1024px) { .product-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); } }

@media (max-width: 640px) {
  .container { padding-inline: 16px; }
  .product-grid { gap: 16px; }
}
</style>

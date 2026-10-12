<template>
  <AppLayout>
    <div class="follow container">
      <h1 class="page-title">我的关注</h1>

      <!-- 分段切换：店铺 / 商品 -->
      <div class="seg" role="tablist">
        <button
          type="button"
          role="tab"
          :aria-selected="tab === 'shops'"
          @click="tab = 'shops'"
        >
          店铺 <span v-if="shops.length" class="seg__count">{{ shops.length }}</span>
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="tab === 'products'"
          @click="tab = 'products'"
        >
          商品 <span v-if="products.length" class="seg__count">{{ products.length }}</span>
        </button>
      </div>

      <div v-if="loading" class="hint">加载中…</div>

      <!-- 店铺 -->
      <template v-else-if="tab === 'shops'">
        <p v-if="shops.length === 0" class="hint">
          还没有关注的店铺。逛逛商品详情页，点店铺卡片进去就能关注。
        </p>
        <div v-else class="shop-list">
          <article v-for="s in shops" :key="s.id" class="shop-item" @click="router.push('/shops/' + s.id)">
            <div class="shop-item__main">
              <h3 class="shop-item__name">{{ s.shopName }}</h3>
              <p v-if="s.description" class="shop-item__desc">{{ s.description }}</p>
              <p class="shop-item__meta">{{ s.productCount }} 件在售 · {{ s.followerCount }} 人关注</p>
            </div>
            <button class="unfollow" type="button" @click.stop="onUnfollowShop(s)">取消关注</button>
          </article>
        </div>
      </template>

      <!-- 商品 -->
      <template v-else>
        <p v-if="products.length === 0" class="hint">
          还没有关注的商品。在商品详情页可以关注，降价或补货时会通知你。
        </p>
        <div v-else class="product-grid">
          <ProductCard
            v-for="p in products"
            :key="p.id"
            :product="p"
            @click="router.push('/products/' + p.id)"
          />
        </div>
      </template>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppLayout from '@/layouts/AppLayout.vue'
import ProductCard from '@/components/ProductCard.vue'
import { getFollowedShops, getFollowedProducts, unfollowMerchant } from '@/api/shop'
import type { Shop } from '@/api/shop'
import type { Product } from '@/api/product'

const router = useRouter()
const tab = ref<'shops' | 'products'>('shops')
const shops = ref<Shop[]>([])
const products = ref<Product[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const [s, p] = await Promise.all([getFollowedShops(), getFollowedProducts()])
    shops.value = s
    products.value = p
  } catch {
    ElMessage.error('加载关注列表失败')
  } finally {
    loading.value = false
  }
})

async function onUnfollowShop(shop: Shop) {
  try {
    await unfollowMerchant(shop.id)
    shops.value = shops.value.filter((s) => s.id !== shop.id)
    ElMessage.success('已取消关注')
  } catch {
    ElMessage.error('操作失败')
  }
}

</script>

<style scoped>
.container { width: 100%; max-width: 1200px; margin: 0 auto; padding-inline: 24px; }
.follow { padding-block: 40px 88px; }

.page-title {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-text-primary);
  margin-bottom: 24px;
}

/* 分段控件：选中用深色填充，和首页 chip 的语言一致 */
.seg {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  margin-bottom: 32px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
}
.seg button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 20px;
  border: none;
  border-radius: var(--radius-full);
  background: transparent;
  color: var(--color-text-secondary);
  font-family: var(--font-body);
  font-size: 15px;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.seg button[aria-selected='true'] {
  background: var(--color-bg-inverted);
  color: var(--color-bg-overlay);
}
.seg__count { font-size: 13px; opacity: 0.7; }

.hint {
  padding: 64px 0;
  text-align: center;
  font-size: 15px;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.shop-list { display: flex; flex-direction: column; gap: 12px; }

.shop-item {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 20px 24px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.shop-item:hover { border-color: var(--color-border); box-shadow: var(--shadow-card); }

.shop-item__main { flex: 1; min-width: 0; }
.shop-item__name {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 400;
  color: var(--color-text-primary);
  margin-bottom: 6px;
}
.shop-item__desc {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.shop-item__meta { font-size: 13px; color: var(--color-text-muted); }

.unfollow {
  flex-shrink: 0;
  min-height: 38px;
  padding: 0 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: transparent;
  color: var(--color-text-secondary);
  font-family: var(--font-body);
  font-size: 14px;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}
.unfollow:hover { border-color: var(--color-error); color: var(--color-error); }

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
  .shop-item { flex-direction: column; align-items: flex-start; gap: 12px; }
}
</style>

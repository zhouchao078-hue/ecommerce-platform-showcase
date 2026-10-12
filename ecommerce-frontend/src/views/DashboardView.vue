<template>
  <AppLayout>
    <!-- ═══════════ Hero ═══════════ -->
    <section class="hero">
      <div class="container hero__grid">
        <div class="hero__copy">
          <h1 class="hero__title">把日常用品，<br />挑成<em>值得久留</em>的东西。</h1>
          <p class="hero__body">
            不追热点，不堆参数。我们只把真正耐用、好用、看得顺眼的物件放到这里，
            剩下的交给你的判断。
          </p>
          <div class="hero__actions">
            <button class="btn btn--primary" type="button" @click="scrollToProducts">开始逛逛</button>
            <button class="btn btn--ghost" type="button" @click="askAgent">问问店里的助手</button>
          </div>
        </div>

        <div class="hero__art" aria-hidden="true">
          <span class="hero__arc hero__arc--3"></span>
          <span class="hero__arc hero__arc--2"></span>
          <span class="hero__arc hero__arc--1"></span>
          <div class="hero__disc">૮ ˶ᵔ ᵕ ᵔ˶ ა</div>
          <div class="hero__tag"><i></i>助手在线，可以帮你挑</div>
        </div>
      </div>
    </section>

    <!-- ═══════════ 商品区 ═══════════ -->
    <section id="products" class="section">
      <div class="container">
        <div class="section__row">
          <div>
            <div class="section__label">Curated this week</div>
            <h2 class="section__title">今日推荐</h2>
          </div>
          <CategoryBar :model-value="selectedCategory" @change="onCategoryChange" />
        </div>

        <div class="product-grid">
          <template v-if="loading && products.length === 0">
            <SkeletonProductCard v-for="n in 8" :key="n" />
          </template>
          <ProductCard
            v-for="p in products"
            :key="p.id"
            :product="p"
            @add-cart="onAddCart"
            @click="router.push('/products/' + p.id)"
          />
        </div>

        <div v-if="!loading && products.length === 0" class="empty-state">
          <p>这个分类下暂时没有商品，换个分类看看。</p>
        </div>

        <div v-if="loading && products.length > 0" class="load-more">载入中…</div>
        <div v-if="!hasMore && products.length > 0" class="load-end">— 已经到底了 —</div>
      </div>
    </section>

    <!-- ═══════════ 深色区块 ═══════════ -->
    <section class="band">
      <div class="container band__grid">
        <div class="band__lead">
          <div class="section__label section__label--onDark">Store membership</div>
          <h2 class="band__title">买得放心，比买得便宜更重要。</h2>
          <p class="band__body">
            会员享 30 天无理由退货、免费上门取件，以及专属客服。
            我们不承诺最低价，但承诺出问题时有人负责。
          </p>
        </div>
        <dl class="band__note">
          <dt>会员权益</dt>
          <dd>30 天无理由退货</dd>
          <dd>上门取件，运费我们承担</dd>
          <dd>工作日 9:00–21:00 真人客服</dd>
        </dl>
      </div>
    </section>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getProducts } from '@/api/product'
import type { Product } from '@/api/product'
import AppLayout from '@/layouts/AppLayout.vue'
import CategoryBar from '@/components/CategoryBar.vue'
import ProductCard from '@/components/ProductCard.vue'
import SkeletonProductCard from '@/components/SkeletonProductCard.vue'

const PAGE_SIZE = 12

const router = useRouter()
const route = useRoute()
const products = ref<Product[]>([])
const pageNum = ref(1)
const loading = ref(false)
const hasMore = ref(true)
const selectedCategory = ref<number | null>(null)

async function loadProducts(reset = false) {
  if (loading.value || (!hasMore.value && !reset)) return
  loading.value = true
  if (reset) {
    pageNum.value = 1
    hasMore.value = true
  }
  try {
    const res = await getProducts({
      categoryId: selectedCategory.value,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
    })
    if (reset) {
      products.value = res
    } else {
      products.value.push(...res)
    }
    hasMore.value = res.length === PAGE_SIZE
    pageNum.value++
  } catch {
    // 出错时保留已加载的商品，只停止继续加载
    hasMore.value = false
  } finally {
    loading.value = false
  }
}

function onCategoryChange(categoryId: number | null) {
  selectedCategory.value = categoryId
  // 分类写回 URL，刷新/分享后仍保持当前分类
  router.replace({
    query: categoryId ? { categoryId: String(categoryId) } : {},
  })
}

function onAddCart(_product: Product) {
  // 加购逻辑在 ProductCard 内部完成，这里只是接住事件
}

function scrollToProducts() {
  document.getElementById('products')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

// 交给 ChatPanel 处理（它监听 open-chat 事件），沿用项目里 cart-updated 的写法
function askAgent() {
  window.dispatchEvent(new Event('open-chat'))
}

// 滚动到底部前 200px 时加载下一页（防抖，滚动停止 200ms 后才判断）
let scrollTimer: number | undefined
function onScroll() {
  if (scrollTimer) return
  scrollTimer = window.setTimeout(() => {
    scrollTimer = undefined
    const el = document.documentElement
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 200) {
      loadProducts()
    }
  }, 200)
}

watch(selectedCategory, () => loadProducts(true))

onMounted(() => {
  const catFromUrl = route.query.categoryId
  if (catFromUrl) { selectedCategory.value = Number(catFromUrl) }
  loadProducts(true)
  window.addEventListener('scroll', onScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  if (scrollTimer) window.clearTimeout(scrollTimer)
})
</script>

<style scoped>
/* ======================== 通用容器 ======================== */
.container {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding-inline: 24px;
}

/* ======================== Hero ======================== */
.hero {
  padding-block: 64px 56px;
  background:
    radial-gradient(ellipse 70% 55% at 12% 0%, rgba(217, 119, 87, 0.09) 0%, transparent 58%),
    radial-gradient(ellipse 60% 50% at 92% 30%, rgba(120, 140, 93, 0.07) 0%, transparent 60%);
}

.hero__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 40px;
  align-items: center;
}

@media (min-width: 900px) {
  .hero__grid { grid-template-columns: 1.618fr 1fr; }
}

.hero__copy {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 620px;
}

.hero__title {
  font-family: var(--font-display);
  font-size: clamp(2rem, 5vw, 3.5rem);
  font-weight: 400;
  line-height: 1.35;
  color: var(--color-text-primary);
  /* 中文标题不拆词，避免「值得久留」被断开 */
  word-break: keep-all;
  overflow-wrap: break-word;
}

.hero__title em {
  font-style: italic;
  color: var(--color-accent);
}

.hero__body {
  font-size: 17px;
  line-height: 1.75;
  color: var(--color-text-secondary);
  max-width: 44ch;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.btn {
  min-height: 46px;
  padding: 0 24px;
  border-radius: var(--radius-full);
  border: 1px solid transparent;
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}
.btn:active { transform: scale(0.98); }

.btn--primary {
  background: var(--color-accent);
  color: #fff;
  box-shadow: var(--shadow-btn);
}
.btn--primary:hover { background: var(--color-accent-dark); }

.btn--ghost {
  background: var(--color-bg-overlay);
  border-color: var(--color-border);
  color: var(--color-text-primary);
}
.btn--ghost:hover { border-color: var(--color-border-strong); }

/* 右侧装饰：同心弧 + 圆盘，弧线刻意溢出容器制造张力 */
.hero__art {
  position: relative;
  min-height: 300px;
  display: grid;
  place-items: center;
}

.hero__arc {
  position: absolute;
  border-radius: var(--radius-full);
  border: 1px solid var(--color-border);
}
.hero__arc--1 { width: 210px; height: 210px; }
.hero__arc--2 { width: 296px; height: 296px; opacity: 0.7; }
.hero__arc--3 { width: 384px; height: 384px; opacity: 0.4; }

@media (min-width: 900px) {
  .hero__art { min-height: 420px; }
  .hero__arc--3 { transform: translateX(48px); }
}

.hero__disc {
  position: relative;
  width: 168px;
  height: 168px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-full);
  background:
    radial-gradient(circle at 32% 28%, rgba(255, 255, 255, 0.9), transparent 58%),
    linear-gradient(150deg, #f3d9c6, #e5e0c8);
  box-shadow: var(--shadow-elevated);
  font-size: 62px;
  line-height: 1;
  animation: floaty 6s ease-in-out infinite;
}

@keyframes floaty {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-10px); }
}

.hero__tag {
  position: absolute;
  bottom: 6%;
  right: 4%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: var(--radius-full);
  background: var(--color-bg-overlay);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-card);
  font-size: 13px;
  color: var(--color-text-secondary);
}
.hero__tag i {
  width: 7px;
  height: 7px;
  border-radius: var(--radius-full);
  background: var(--color-success);
}

/* ======================== 区块与网格 ======================== */
.section {
  padding-block: 56px 72px;
}

.section__row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 32px;
}

.section__label {
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-accent);
  margin-bottom: 8px;
}
.section__label--onDark { color: var(--color-accent); }

.section__title {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-text-primary);
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}
@media (min-width: 720px)  { .product-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (min-width: 1024px) { .product-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); } }

.empty-state {
  text-align: center;
  padding: 80px 16px;
  font-size: 15px;
  color: var(--color-text-secondary);
}

.load-more,
.load-end {
  text-align: center;
  padding: 24px 16px 0;
  font-size: 13px;
  color: var(--color-text-muted);
}

/* ======================== 深色区块 ======================== */
.band {
  padding-block: 72px;
  background:
    radial-gradient(ellipse 70% 60% at 78% 100%, rgba(217, 119, 87, 0.14) 0%, transparent 62%),
    var(--color-bg-inverted);
}

.band__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 32px;
}
@media (min-width: 900px) {
  .band__grid { grid-template-columns: 1.618fr 1fr; align-items: end; }
}

.band__lead { display: flex; flex-direction: column; gap: 20px; }

.band__title {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-bg-overlay);
  max-width: 20ch;
}

.band__body {
  font-size: 16px;
  line-height: 1.75;
  color: rgba(253, 252, 248, 0.66);
  max-width: 40ch;
}

.band__note {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px;
  border: 1px solid rgba(253, 252, 248, 0.16);
  border-radius: var(--radius-lg);
}
.band__note dt {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(253, 252, 248, 0.45);
}
.band__note dd {
  margin: 0;
  font-size: 15px;
  line-height: 1.6;
  color: var(--color-bg-overlay);
}

/* ======================== 响应式 ======================== */
@media (max-width: 640px) {
  .container { padding-inline: 16px; }
  .hero { padding-block: 40px 32px; }
  .hero__art { min-height: 260px; }
  .hero__arc--1 { width: 150px; height: 150px; }
  .hero__arc--2 { width: 212px; height: 212px; }
  .hero__arc--3 { width: 274px; height: 274px; }
  .hero__disc { width: 132px; height: 132px; font-size: 46px; }
  .hero__tag {
    right: auto;
    left: 50%;
    bottom: 0;
    transform: translateX(-50%);
    white-space: nowrap;
  }
  .product-grid { gap: 16px; }
  .section { padding-block: 40px 56px; }
}
</style>

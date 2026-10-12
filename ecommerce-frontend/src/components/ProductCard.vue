<template>
  <article class="card" @click="$emit('click', product)">
    <div class="card__media">
      <img v-if="firstImage" :src="firstImage" class="card__img" alt="" loading="lazy" />
      <span v-else class="card__placeholder">{{ firstChar }}</span>
    </div>

    <h3 class="card__name">{{ product.name }}</h3>
    <p v-if="product.description" class="card__desc">{{ product.description }}</p>

    <div class="card__foot">
      <span class="card__price">{{ displayPrice }}</span>
      <button
        class="card__add"
        :class="{ bump: bumping }"
        type="button"
        :aria-label="`把 ${product.name} 加入购物车`"
        @click.stop="handleAddCart"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M8 3v10M3 8h10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
        </svg>
      </button>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { addToCart } from '@/api/product'
import type { Product } from '@/api/product'
import { firstProductImage } from '@/utils/productImages'

const props = defineProps<{
  product: Product
}>()

const emit = defineEmits<{
  (e: 'add-cart', product: Product): void
  (e: 'click', product: Product): void
}>()

const firstChar = computed(() => props.product.name?.charAt(0) || '?')
const displayPrice = computed(() => Number(props.product.price).toFixed(2))

// 加购成功后的轻微弹跳
const bumping = ref(false)

// 商品图片：支持单个 URL、JSON 数组、逗号分隔三种格式，取第一张
const firstImage = computed(() => {
  return firstProductImage(props.product.images)
})

async function handleAddCart() {
  try {
    await addToCart({ productId: props.product.id })
    ElMessage.success('已加入购物车')
    bumping.value = true
    setTimeout(() => { bumping.value = false }, 400)
    // 通知顶栏刷新购物车角标
    window.dispatchEvent(new Event('cart-updated'))
    emit('add-cart', props.product)
  } catch {
    ElMessage.error('加入购物车失败')
  }
}
</script>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: var(--color-bg-raised);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}

.card:hover {
  transform: translateY(-4px);
  border-color: var(--color-border);
  box-shadow: var(--shadow-elevated);
}

.card__media {
  aspect-ratio: 1 / 1;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--color-bg-base);
  overflow: hidden;
}

.card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.4s ease;
}
.card:hover .card__img { transform: scale(1.04); }

.card__placeholder {
  font-family: var(--font-display);
  font-size: 52px;
  color: var(--color-border-strong);
  user-select: none;
}

.card__name {
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__desc {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-secondary);
  /* 最多两行，超出省略，保证卡片高度一致 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 42px;
}

.card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
}

.card__price {
  font-family: var(--font-display);
  font-size: 20px;
  color: var(--color-accent-dark);
  font-variant-numeric: tabular-nums;
}
.card__price::before {
  content: '¥';
  font-size: 0.62em;
  margin-right: 2px;
}

.card__add {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: var(--radius-full);
  background: var(--color-bg-inverted);
  color: var(--color-bg-overlay);
  cursor: pointer;
  transition: background-color 0.15s ease, transform 0.15s ease;
}

.card__add:hover {
  background: var(--color-accent);
  transform: scale(1.08);
}
.card__add:active { transform: scale(0.94); }

.card__add.bump { animation: btnPop 0.4s ease; }
@keyframes btnPop {
  0%   { transform: scale(1); }
  40%  { transform: scale(1.18); }
  100% { transform: scale(1); }
}
</style>

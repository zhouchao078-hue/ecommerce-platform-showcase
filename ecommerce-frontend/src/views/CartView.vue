<template>
  <AppLayout>
    <div class="cart-page container">
      <h1 class="page-title">购物车</h1>
      <div v-if="loading" class="loading">加载中…</div>
      <el-empty v-else-if="items.length === 0" description="购物车是空的">
        <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
      </el-empty>
      <div v-else class="cart-list">
        <div v-for="item in items" :key="item.id" class="cart-item">
          <div class="item-image">
            <img v-if="item.productImage" :src="item.productImage" class="item-img" alt="" loading="lazy" />
            <span v-else>{{ item.productName.charAt(0) }}</span>
          </div>
          <div class="item-info">
            <p class="item-name">{{ item.productName }}</p>
            <p v-if="item.skuName" class="item-sku">{{ item.skuName }}</p>
            <p class="item-price">¥{{ Number(item.productPrice).toFixed(2) }}</p>
          </div>
          <el-input-number :model-value="item.quantity" :min="1" size="small" class="item-qty" @change="onQtyChange(item, $event)" />
          <el-button text type="danger" size="small" @click="onDelete(item)">删除</el-button>
        </div>
      </div>
      <div v-if="items.length > 0" class="cart-bottom">
        <span class="total-label">合计：</span>
        <span class="total-amount">¥{{ total.toFixed(2) }}</span>
        <el-button type="primary" size="large" class="checkout-btn" @click="onCheckout">去结算</el-button>
      </div>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue"
import { useRouter } from "vue-router"
import AppLayout from "@/layouts/AppLayout.vue"
import { ElMessage, ElMessageBox } from "element-plus"
import { getCart, updateCartQuantity, deleteCartItem } from "@/api/product"
import type { CartVO } from "@/api/product"

const router = useRouter()
const loading = ref(true)
const items = ref<CartVO[]>([])
const total = computed(() => items.value.reduce((s, i) => s + Number(i.productPrice) * i.quantity, 0))

async function fetchCart() {
  loading.value = true
  try { items.value = await getCart() }
  catch { ElMessage.error("加载购物车失败") }
  finally { loading.value = false }
}

async function onQtyChange(item: CartVO, val: number | undefined) {
  const old = item.quantity
  item.quantity = val ?? 1
  try {
    await updateCartQuantity(item.id, item.quantity)
    window.dispatchEvent(new Event('cart-updated'))
  } catch {
    item.quantity = old
    ElMessage.error("修改数量失败")
  }
}

async function onDelete(item: CartVO) {
  try {
    await ElMessageBox.confirm("确定要删除该商品吗？", "提示")
    await deleteCartItem(item.id)
    items.value = items.value.filter((i) => i.id !== item.id)
    ElMessage.success("已删除")
    window.dispatchEvent(new Event('cart-updated'))
  } catch {}
}

function onCheckout() { router.push("/checkout") }

onMounted(fetchCart)
</script>

<style scoped>
.container { width: 100%; max-width: 900px; margin: 0 auto; padding-inline: 24px; }
.cart-page { padding-block: 40px 140px; }

.page-title {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-text-primary);
  margin-bottom: 32px;
}

.loading { text-align: center; padding: 80px 0; color: var(--color-text-secondary); }

.cart-list {
  background: var(--color-bg-raised);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  padding: 8px 24px;
}

.cart-item { display: flex; align-items: center; gap: 16px; padding: 20px 0; border-bottom: 1px solid var(--color-border-subtle); }
.cart-item:last-child { border-bottom: none; }

.item-image { width: 72px; height: 72px; border-radius: var(--radius-md); background: var(--color-bg-base); display: flex; align-items: center; justify-content: center; font-family: var(--font-display); font-size: 28px; color: var(--color-border-strong); flex-shrink: 0; overflow: hidden; }
.item-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.item-info { flex: 1; min-width: 0; }
.item-name { font-size: 15px; color: var(--color-text-primary); margin: 0 0 6px; }
.item-sku { font-size: 12px; color: var(--color-text-muted); margin: 0 0 6px; }
.item-price { font-family: var(--font-display); font-size: 17px; color: var(--color-accent-dark); margin: 0; }

.cart-bottom {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(253, 252, 248, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-top: 1px solid var(--color-border);
  padding: 16px 24px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  z-index: 50;
}
.total-label { font-size: 14px; color: var(--color-text-secondary); }
.total-amount { font-family: var(--font-display); font-size: 24px; color: var(--color-accent-dark); margin-right: 8px; }
.checkout-btn { min-width: 160px; height: 46px; border-radius: var(--radius-full); }
</style>

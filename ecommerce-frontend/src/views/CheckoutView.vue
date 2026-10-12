<template>
  <AppLayout>
    <div class="checkout container">
      <h1 class="page-title">确认订单</h1>
      <div v-if="loading" class="loading">加载中…</div>
      <template v-else>
        <h3 class="section-title">收货地址</h3>
        <div v-if="addresses.length === 0" class="no-addr">暂无收货地址</div>
        <div v-else class="addr-list">
          <div v-for="addr in addresses" :key="addr.id" class="addr-card" :class="{ selected: selectedId === addr.id }">
            <div class="addr-body" @click="selectedId = addr.id">
              <p class="addr-name">{{ addr.receiverName }} <span class="addr-phone">{{ addr.phone }}</span></p>
              <p class="addr-detail">{{ addr.province }}{{ addr.city }}{{ addr.district }}{{ addr.detailAddress }}</p>
            </div>
            <el-button text type="danger" size="small" @click.stop="onDeleteAddress(addr.id)">删除</el-button>
          </div>
        </div>
        <el-button type="primary" size="small" class="add-addr-btn" @click="showAddressDialog = true">+ 添加新地址</el-button>

        <el-dialog v-model="showAddressDialog" title="添加收货地址" width="90%" max-width="460px">
          <el-form>
            <el-input v-model="addressForm.receiverName" placeholder="收件人姓名" style="margin-bottom:12px" />
            <el-input v-model="addressForm.phone" placeholder="联系电话" style="margin-bottom:12px" />
            <div style="display:flex;gap:8px;margin-bottom:12px">
              <el-input v-model="addressForm.province" placeholder="省" />
              <el-input v-model="addressForm.city" placeholder="市" />
              <el-input v-model="addressForm.district" placeholder="区" />
            </div>
            <el-input v-model="addressForm.detailAddress" placeholder="详细地址" />
          </el-form>
          <template #footer>
            <el-button @click="showAddressDialog = false">取消</el-button>
            <el-button type="primary" @click="onAddAddress">保存</el-button>
          </template>
        </el-dialog>

        <h3 class="section-title">商品清单</h3>
        <div v-for="item in items" :key="item.id" class="item-row">
          <span class="item-name">
            {{ item.productName }}
            <small v-if="item.skuName">{{ item.skuName }}</small>
          </span>
          <span class="item-qty">x{{ item.quantity }}</span>
          <span class="item-price">¥{{ Number(item.productPrice).toFixed(2) }}</span>
        </div>
        <div class="total-row">
          <span>商品总额</span>
          <span class="total-amount">¥{{ total.toFixed(2) }}</span>
        </div>
        <el-input v-model="remark" placeholder="备注（可选）" class="remark-input" />
        <p v-if="errorMsg" class="error-text">{{ errorMsg }}</p>
        <el-button type="primary" size="large" class="submit-btn" :loading="submitting" :disabled="!selectedId" @click="onSubmit">提交订单</el-button>
      </template>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue"
import { useRouter } from "vue-router"
import { ElMessage } from "element-plus"
import AppLayout from "@/layouts/AppLayout.vue"
import { getCart, createOrder } from "@/api/product"
import type { CartVO } from "@/api/product"
import { getAddressList, addAddress, deleteAddress } from "@/api/user"
import type { Address } from "@/api/user"

const router = useRouter()
const loading = ref(true)
const submitting = ref(false)
const errorMsg = ref("")
const addresses = ref<Address[]>([])
const items = ref<CartVO[]>([])
const selectedId = ref<number | null>(null)
const remark = ref("")
const showAddressDialog = ref(false)
const addressForm = ref({ receiverName: "", phone: "", province: "", city: "", district: "", detailAddress: "" })
const total = computed(() => items.value.reduce((s, i) => s + Number(i.productPrice) * i.quantity, 0))

onMounted(async () => {
  try {
    const [addrRes, cartRes] = await Promise.all([getAddressList(), getCart()])
    addresses.value = addrRes
    items.value = cartRes
    // 默认选中默认地址
    const def = addrRes.find((a) => a.isDefault === 1)
    if (def) selectedId.value = def.id
  } catch {
    ElMessage.error("加载结算信息失败")
  }
  finally { loading.value = false }
})

async function onAddAddress() {
  try {
    await addAddress(addressForm.value)
    showAddressDialog.value = false
    addressForm.value = { receiverName: "", phone: "", province: "", city: "", district: "", detailAddress: "" }
    addresses.value = await getAddressList()
  } catch (e: any) {
    // 保存失败不关闭对话框，提示用户
    ElMessage.error(e?.response?.data?.message || e?.message || "添加地址失败")
  }
}

async function onDeleteAddress(id: number) {
  try {
    await deleteAddress(id)
    addresses.value = await getAddressList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || "删除地址失败")
  }
}

async function onSubmit() {
  if (!selectedId.value) { errorMsg.value = "请选择收货地址"; return }
  submitting.value = true
  errorMsg.value = ""
  try {
    await createOrder({
      cartItemIds: items.value.map((i) => i.id),
      addressId: selectedId.value,
      remark: remark.value || undefined,
    })
    ElMessage.success("下单成功")
    router.push("/orders")
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || e?.message || "下单失败"
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.container { width: 100%; max-width: 900px; margin: 0 auto; padding-inline: 24px; }
.checkout { padding-block: 40px 88px; }

.page-title {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-text-primary);
  margin-bottom: 32px;
}

.section-title {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 400;
  color: var(--color-text-primary);
  margin: 40px 0 16px;
}

.loading, .no-addr { text-align: center; padding: 64px 0; color: var(--color-text-secondary); }

.addr-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  border: 1.5px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
  margin-bottom: 12px;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}
.addr-card.selected {
  border-color: var(--color-accent);
  background: var(--color-accent-dim);
}

.addr-body { flex: 1; cursor: pointer; min-width: 0; }
.addr-name { font-size: 15px; font-weight: 500; color: var(--color-text-primary); margin: 0 0 6px; }
.addr-phone { font-weight: 400; color: var(--color-text-secondary); margin-left: 12px; font-size: 14px; }
.addr-detail { font-size: 14px; line-height: 1.6; color: var(--color-text-secondary); margin: 0; }

.add-addr-btn { margin-bottom: 8px; border-radius: var(--radius-full); }

.item-row {
  display: flex;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border-subtle);
  font-size: 15px;
  color: var(--color-text-primary);
}
.item-row .item-name { flex: 1; }
.item-row .item-name small { display: block; margin-top: 3px; color: var(--color-text-muted); font-size: 12px; }
.item-row .item-qty { margin: 0 24px; color: var(--color-text-secondary); }
.item-row .item-price {
  font-family: var(--font-display);
  color: var(--color-accent-dark);
  min-width: 80px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.total-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 20px 0;
  font-size: 15px;
  color: var(--color-text-secondary);
}
.total-amount { font-family: var(--font-display); font-size: 26px; color: var(--color-accent-dark); }

.remark-input { margin-top: 8px; }
.error-text { color: var(--color-error); font-size: 14px; margin: 12px 0 0; }

.submit-btn {
  width: 100%;
  height: 50px;
  margin-top: 24px;
  font-size: 16px;
  border-radius: var(--radius-full);
}
</style>

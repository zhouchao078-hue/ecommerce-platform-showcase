<template>
  <AppLayout>
    <div class="pf-page">
      <h2 class="pf-title">个人中心</h2>
      <!-- 用户信息 -->
      <el-card class="pf-card">
        <p>用户名：{{ authStore.userInfo?.username }}</p>
        <p>手机：{{ authStore.userInfo?.phone || '未设置' }}</p>
        <p>登录次数：{{ authStore.userInfo?.loginCount }}</p>
        <p>注册时间：{{ authStore.userInfo?.createdAt }}</p>
      </el-card>

      
      <!-- 修改密码 -->
      <el-card class="pf-card">
        <h3 class="pf-section">修改密码</h3>
        <el-input v-model="pwForm.oldPassword" type="password" placeholder="原密码" style="margin-bottom:12px" size="large" show-password />
        <el-input v-model="pwForm.newPassword" type="password" placeholder="新密码" style="margin-bottom:12px" size="large" show-password />
        <el-button type="primary" @click="onChangePw" :loading="changingPw" class="pw-btn">修改密码</el-button>
      </el-card>

      <!-- 商家入口 -->
      <el-card class="pf-card">
        <h3 class="pf-section">商家中心</h3>
        <p class="pf-desc">{{ merchantStatus?.statusDesc || '加载商家状态中…' }}</p>
        <div class="merchant-actions">
          <el-button v-if="merchantStatus?.status === -1 || merchantStatus?.status === 2" type="primary" @click="showMerchantDialog = true">
            {{ merchantStatus.status === 2 ? '重新申请成为商家' : '申请成为商家' }}
          </el-button>
          <el-button v-if="merchantStatus?.status === 1" type="primary" plain @click="router.push('/merchant/products')">商品管理</el-button>
          <el-button v-if="merchantStatus?.status === 1" type="primary" plain @click="router.push('/merchant/orders')">订单管理</el-button>
          <el-button v-if="merchantStatus?.status === 1" type="primary" plain @click="router.push('/merchant/after-sales')">售后处理</el-button>
          <el-tag v-if="merchantStatus?.status === 0" type="warning">申请审核中</el-tag>
        </div>
      </el-card>

      <!-- 地址管理 -->
      <h3 class="pf-section">收货地址</h3>
      <div v-if="addresses.length === 0" class="no-addr">暂无地址</div>
      <div v-else class="addr-list">
        <div v-for="addr in addresses" :key="addr.id" class="addr-card">
          <div class="addr-body">
            <p class="addr-name">{{ addr.receiverName }} <span class="addr-phone">{{ addr.phone }}</span></p>
            <p class="addr-detail">{{ addr.province }}{{ addr.city }}{{ addr.district }}{{ addr.detailAddress }}</p>
          </div>
          <div class="addr-actions">
            <el-button text size="small" @click="onSetDefault(addr.id)" v-if="!addr.isDefault">设为默认</el-button>
            <el-button text type="danger" size="small" @click="onDeleteAddress(addr.id)">删除</el-button>
          </div>
        </div>
      </div>
      <el-button class="add-addr-btn" @click="showDialog = true">+ 添加新地址</el-button>

      <!-- 添加对话框 -->
      <el-dialog v-model="showDialog" title="添加收货地址" width="90%" max-width="460px">
        <el-input v-model="form.receiverName" placeholder="收件人姓名" style="margin-bottom:12px" />
        <el-input v-model="form.phone" placeholder="联系电话" style="margin-bottom:12px" />
        <el-input v-model="form.province" placeholder="省份" style="margin-bottom:12px" />
        <el-input v-model="form.city" placeholder="城市" style="margin-bottom:12px" />
        <el-input v-model="form.district" placeholder="区/县" style="margin-bottom:12px" />
        <el-input v-model="form.detailAddress" placeholder="详细地址" />
        <template #footer>
          <el-button @click="showDialog = false">取消</el-button>
          <el-button type="primary" @click="onAddAddress">保存</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="showMerchantDialog" title="申请成为商家" width="min(92vw, 460px)">
        <el-input v-model="merchantForm.shopName" maxlength="100" show-word-limit placeholder="店铺名称" />
        <el-input v-model="merchantForm.description" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="简单介绍一下你的店铺（选填）" class="merchant-description" />
        <template #footer>
          <el-button @click="showMerchantDialog = false">取消</el-button>
          <el-button type="primary" :loading="applyingMerchant" @click="onApplyMerchant">提交申请</el-button>
        </template>
      </el-dialog>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { getAddressList, addAddress, deleteAddress, setDefaultAddress, changePassword } from '@/api/user'
import type { Address } from '@/api/user'
import { applyMerchant, getMerchantStatus, type MerchantStatus } from '@/api/merchant'
const authStore = useAuthStore()
const router = useRouter()
const addresses = ref<Address[]>([])
const showDialog = ref(false)
const changingPw = ref(false)
const merchantStatus = ref<MerchantStatus | null>(null)
const showMerchantDialog = ref(false)
const applyingMerchant = ref(false)
const merchantForm = ref({ shopName: '', description: '' })
const pwForm = ref({ oldPassword: '', newPassword: '' })
const form = ref({ receiverName: '', phone: '', province: '', city: '', district: '', detailAddress: '' })


async function onChangePw() {
  if (!pwForm.value.oldPassword || !pwForm.value.newPassword) { ElMessage.error("请填写完整"); return }
  changingPw.value = true
  try { await changePassword(pwForm.value.oldPassword, pwForm.value.newPassword); ElMessage.success("密码修改成功"); pwForm.value = { oldPassword: '', newPassword: '' } }
  catch (e: any) { ElMessage.error(e?.response?.data?.message || e?.message || "修改失败") }
  finally { changingPw.value = false }
}

async function fetchAddrs() {
  try { addresses.value = await getAddressList() }
  catch (e: any) { ElMessage.error(e?.response?.data?.message || e?.message || "地址加载失败") }
}
onMounted(() => {
  // 刷新页面后重新拉取用户信息（登录流程已自动拉取过）
  authStore.fetchUserInfo().catch(() => {})
  fetchAddrs()
  getMerchantStatus().then(value => { merchantStatus.value = value }).catch(() => {})
})

async function onApplyMerchant() {
  const shopName = merchantForm.value.shopName.trim()
  if (!shopName) { ElMessage.warning('请先填写店铺名称'); return }
  applyingMerchant.value = true
  try {
    await applyMerchant({ shopName, description: merchantForm.value.description.trim() })
    merchantStatus.value = await getMerchantStatus()
    showMerchantDialog.value = false
    merchantForm.value = { shopName: '', description: '' }
    ElMessage.success('申请已提交，请等待审核')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '提交申请失败')
  } finally {
    applyingMerchant.value = false
  }
}

async function onAddAddress() {
  try {
    await addAddress(form.value)
    showDialog.value = false
    form.value = { receiverName: '', phone: '', province: '', city: '', district: '', detailAddress: '' }
    await fetchAddrs()
  } catch (e: any) {
    // 保存失败不关闭对话框，提示用户
    ElMessage.error(e?.response?.data?.message || e?.message || "添加地址失败")
  }
}
async function onDeleteAddress(id: number) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deleteAddress(id)
    await fetchAddrs()
  } catch (e: any) {
    // 用户取消确认框时不提示
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.response?.data?.message || e?.message || "删除地址失败")
    }
  }
}
async function onSetDefault(id: number) {
  try { await setDefaultAddress(id); await fetchAddrs() }
  catch (e: any) { ElMessage.error(e?.response?.data?.message || e?.message || "设置默认地址失败") }
}
</script>

<style scoped>
.pf-page {
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 24px 88px;
}
.pf-title {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-text-primary);
  margin-bottom: 32px;
}
.pf-card { margin-bottom: 24px; }
.pf-section {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 400;
  color: var(--color-text-primary);
  margin: 0 0 16px;
}
.no-addr { text-align: center; color: var(--color-text-secondary); padding: 24px 0; }
.addr-card {
  padding: 20px;
  margin-bottom: 12px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-raised);
}
.addr-body { margin-bottom: 8px; }
.addr-name { font-size: 14px; font-weight: 600; color: var(--color-text-primary); margin: 0 0 4px; }
.addr-phone { font-weight: 400; color: var(--color-text-secondary); margin-left: 12px; font-size: 13px; }
.addr-detail { font-size: 13px; color: var(--color-text-secondary); margin: 0; }
.addr-actions { display: flex; gap: 8px; }
.add-addr-btn { margin-top: 12px; }
.pw-btn { width: 100%; border-radius: 8px; }
.merchant-actions { display: flex; align-items: center; gap: 10px; }
.merchant-description { margin-top: 12px; }
</style>

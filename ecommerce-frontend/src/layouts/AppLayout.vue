<template>
  <div class="app-layout">
    <!-- ==================== HEADER ==================== -->
    <header class="header">
      <div class="header-inner">
        <!-- Logo -->
        <router-link to="/dashboard" class="logo">STORE</router-link>

        <!-- Nav links -->
        <nav class="nav-links">
          <template v-for="link in navLinks" :key="link.label">
            <router-link
              v-if="link.path"
              :to="link.path"
              class="nav-link"
              :class="{ active: route.path === link.path }"
            >{{ link.label }}</router-link>
            <span v-else class="nav-link disabled">{{ link.label }}</span>
          </template>
        </nav>

        <!-- Right actions -->
        <div class="header-actions">
          <el-autocomplete
            v-model="searchKeyword"
            :fetch-suggestions="onSearch"
            placeholder="搜索商品..."
            :trigger-on-focus="false"
            @select="onSelectProduct"
            clearable
            size="small"
            class="header-search"
            popper-class="search-popper"
          >
            <template #prefix>
              <el-icon :size="16"><Search /></el-icon>
            </template>
            <template #default="{ item }">
              <div class="search-item">
                <span class="search-item-name">{{ item.value }}</span>
                <span class="search-item-price">¥{{ item.price }}</span>
              </div>
            </template>
          </el-autocomplete>
          <!-- 通知 -->
          <router-link to="/notifications" class="cart-icon" title="通知">
            <el-icon :size="20"><Bell /></el-icon>
            <span v-if="unread > 0" class="cart-badge">{{ unread > 99 ? '99+' : unread }}</span>
          </router-link>
          <!-- Cart -->
          <router-link to="/cart" class="cart-icon">
            <el-icon :size="20"><ShoppingCart /></el-icon>
            <span v-if="cartCount > 0" class="cart-badge">{{ cartCount }}</span>
          </router-link>
          <button class="support-link" title="联系平台客服" @click="supportOpen = true">客服</button>
          <!-- User -->
          <el-dropdown trigger="click" @command="handleUserCommand">
            <el-icon class="action-icon user-avatar" :size="22"><UserFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="orders">我的订单</el-dropdown-item>
                <el-dropdown-item command="supportTickets">我的工单</el-dropdown-item>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item v-if="isMerchant" command="merchantDashboard">商家看板</el-dropdown-item>
                <el-dropdown-item v-if="isAdmin" command="agentConsole">智能体控制台</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <!-- Glass decorative line -->
      <div class="header-decor" />
    </header>

    <!-- ==================== CONTENT ==================== -->
    <main class="main-content">
      <slot />
    </main>

    <!-- ==================== FOOTER ==================== -->
    <ChatPanel />
    <PetAgentWidget />
    <el-dialog v-model="supportOpen" title="联系平台客服" width="min(92vw, 460px)">
      <el-input v-model="supportSubject" maxlength="120" placeholder="问题简述（选填）" />
      <el-input v-model="supportMessage" class="support-message" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="描述一下你遇到的问题" />
      <template #footer>
        <el-button @click="supportOpen = false">取消</el-button>
        <el-button type="primary" :loading="supportLoading" @click="submitSupport">提交工单</el-button>
      </template>
    </el-dialog>
    <footer class="footer">
      <p>&copy; 2026 电商平台 &middot; 用心选好物</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { Search, ShoppingCart, UserFilled, Bell } from '@element-plus/icons-vue'
import { unreadCount } from '@/api/notification'
import { searchProducts, getCart } from '@/api/product'
import ChatPanel from '@/components/ChatPanel.vue'
import PetAgentWidget from '@/components/PetAgentWidget.vue'
import { useAuthStore } from '@/stores/auth'
import { createSupportTicket } from '@/api/support'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const searchKeyword = ref('')
const supportOpen = ref(false)
const supportSubject = ref('')
const supportMessage = ref('')
const supportLoading = ref(false)

async function submitSupport() {
  const content = supportMessage.value.trim()
  if (!content) { ElMessage.warning('请先描述你遇到的问题'); return }
  supportLoading.value = true
  try {
    const run = await createSupportTicket({
      subject: supportSubject.value.trim(),
      content,
      externalTicketId: crypto.randomUUID(),
      externalMessageId: crypto.randomUUID(),
    })
    ElMessage.success('工单已创建，智能客服正在处理')
    supportOpen.value = false
    supportSubject.value = ''
    supportMessage.value = ''
    router.push({
      path: `/support/tickets/${encodeURIComponent(run.ticketNo)}`,
      query: { runId: String(run.runId) },
    })
  } catch (error: any) {
    ElMessage.error(error?.message || '提交失败，请稍后重试')
  } finally {
    supportLoading.value = false
  }
}

// 管理员专属入口（智能体控制台）仅管理员可见
const isAdmin = computed(() => !!authStore.userInfo?.roles?.includes('ROLE_ADMIN'))
const isMerchant = computed(() => !!authStore.userInfo?.roles?.includes('ROLE_MERCHANT'))

async function onSearch(query: string, cb: (results: any[]) => void) {
  if (!query || query.length < 2) { cb([]); return }
  try {
    const products = await searchProducts({ keyword: query, pageNum: 1, pageSize: 5 })
    cb(products.map((p: any) => ({ value: p.name, price: p.price, productId: p.id })))
  } catch { cb([]) }
}

function onSelectProduct(item: any) {
  searchKeyword.value = ''
  if (item?.productId) {
    router.push('/products/' + item.productId)
  }
}

const cartCount = ref(0)

function updateCartCount() { getCart().then(r => cartCount.value = r.length).catch(() => {}) }

// 通知未读数：进页面拉一次，通知页标记已读后靠 notify-updated 事件刷新
const unread = ref(0)
function updateUnread() { unreadCount().then(n => unread.value = n).catch(() => {}) }

onMounted(() => {
  updateCartCount()
  updateUnread()
  window.addEventListener('cart-updated', updateCartCount)
  window.addEventListener('notify-updated', updateUnread)
})
onUnmounted(() => {
  window.removeEventListener('cart-updated', updateCartCount)
  window.removeEventListener('notify-updated', updateUnread)
})

  const navLinks = ref([
    { label: '推荐', path: '/dashboard' },
    { label: '广场', path: '/square' },
    { label: '关注', path: '/following' },
    { label: '秒杀', path: '/seckill' },
  ])

function handleUserCommand(command: string) {
  if (command === 'logout') {
    authStore.logout()
    router.push('/login')
  } else if (command === 'orders') {
    router.push('/orders')
  } else if (command === 'supportTickets') {
    router.push('/support/tickets')
  } else if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'merchantDashboard') {
    router.push('/merchant/dashboard')
  } else if (command === 'agentConsole') {
    router.push('/agent-console')
  }
}
</script>

<style scoped>
/* ==================== HEADER ==================== */
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(250, 247, 241, 0.78);
  backdrop-filter: blur(22px);
  -webkit-backdrop-filter: blur(22px);
  border-bottom: 1px solid rgba(121, 105, 91, 0.18);
}

.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 68px;
  display: flex;
  align-items: center;
  gap: 32px;
}

.logo {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
  text-decoration: none;
  letter-spacing: 0.22em;
  white-space: nowrap;
}

/* Nav */
.nav-links {
  display: flex;
  gap: 24px;
  flex: 1;
}

.nav-link {
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  text-decoration: none;
  position: relative;
  padding: 4px 0;
  transition: color 0.2s ease;
}

.nav-link:hover,
.nav-link.active {
  color: var(--color-text-primary);
}

.nav-link.disabled {
  cursor: default;
  color: var(--color-text-muted);
}

.nav-link.active::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--color-accent);
  border-radius: 1px;
}

/* Right actions */
.header-actions {
  display: flex;
  align-items: center;
  gap: 18px;
}

.action-icon {
  color: var(--color-text-primary);
  cursor: pointer;
  transition: color 0.2s ease;
}

.action-icon:hover {
  color: var(--color-accent);
}

.cart-icon {
  position: relative;
  color: var(--color-text-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: color 0.2s ease;
  text-decoration: none;
}

.cart-icon:hover {
  color: var(--color-accent);
}

.cart-badge {
  position: absolute;
  top: -6px;
  right: -8px;
  background: var(--color-accent-dark);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  min-width: 16px;
  height: 16px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
}

.user-avatar {
  color: var(--color-accent);
}

/* Glass decorative line */
.header-decor {
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--color-accent-soft), transparent);
}

/* ==================== MAIN ==================== */
.main-content {
  min-height: calc(100vh - 56px - 64px);
  background: transparent;
}

/* ==================== FOOTER ==================== */
.footer {
  text-align: center;
  padding: 32px 16px;
  background: transparent;
  border-top: 1px solid rgba(121, 105, 91, 0.18);
}

.footer p {
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  color: var(--color-text-secondary);
  margin: 0;
}

.header-search { width: 160px; transition: width 0.3s; }
.support-link { border: 0; padding: 0; background: transparent; color: var(--color-text-secondary); cursor: pointer; font-size: 13px; white-space: nowrap; flex-shrink: 0; }
.support-link:hover { color: var(--color-success); }
.support-message { margin-top: 12px; }
.header-search:focus-within { width: 240px; }
.search-item { display: flex; justify-content: space-between; align-items: center; padding: 4px 0; }
.search-item-name { font-size: 13px; color: var(--color-text-primary); }
.search-item-price { font-family: 'Inter', sans-serif; font-size: 13px; color: var(--color-accent-dark); margin-left: 12px; }

/* ==================== RESPONSIVE ==================== */
@media (max-width: 640px) {
  .header-inner {
    padding: 0 12px;
    gap: 14px;
  }

  .nav-links {
    gap: 14px;
    overflow-x: auto;
  }

  .logo {
    font-size: 18px;
  }
}
</style>

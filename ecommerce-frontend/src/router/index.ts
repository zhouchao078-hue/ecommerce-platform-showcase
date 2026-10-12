import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: () => {
        const authStore = useAuthStore()
        return authStore.isLoggedIn ? '/dashboard' : '/login'
      },
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: () => import('@/views/DashboardView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/cart',
      name: 'Cart',
      component: () => import('@/views/CartView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/checkout',
      name: 'Checkout',
      component: () => import('@/views/CheckoutView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/orders',
      name: 'Orders',
      component: () => import('@/views/OrderListView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/orders/:id',
      name: 'OrderDetail',
      component: () => import('@/views/OrderDetailView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/products/:id',
      name: 'ProductDetail',
      component: () => import('@/views/ProductDetailView.vue'),
      // 商品详情后端是公开接口（/api/products/*），前端也放开，
      // 否则未登录用户没法从商品进到店铺页
      meta: { requiresAuth: false },
    },
    {
      // 商家主页：公开页面，未登录也能浏览
      path: '/shops/:id',
      name: 'Shop',
      component: () => import('@/views/ShopView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/following',
      name: 'Following',
      component: () => import('@/views/FollowView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/notifications',
      name: 'Notifications',
      component: () => import('@/views/NotificationView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/profile',
      name: 'Profile',
      component: () => import('@/views/ProfileView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/support/tickets',
      name: 'SupportTickets',
      component: () => import('@/views/SupportTicketListView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/support/tickets/:ticketNo',
      name: 'SupportTicketDetail',
      component: () => import('@/views/SupportTicketDetailView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/merchant/dashboard',
      name: 'MerchantDashboard',
      component: () => import('@/views/MerchantDashboardView.vue'),
      meta: { requiresAuth: true, requiresMerchant: true },
    },
    {
      path: '/merchant/products',
      name: 'MerchantProducts',
      component: () => import('@/views/MerchantProductsView.vue'),
      meta: { requiresAuth: true, requiresMerchant: true },
    },
    {
      // 商家订单：只有已开通的商家能进，后端也带 hasRole('MERCHANT')
      path: '/merchant/orders',
      name: 'MerchantOrders',
      component: () => import('@/views/MerchantOrdersView.vue'),
      meta: { requiresAuth: true, requiresMerchant: true },
    },
    {
      path: '/merchant/after-sales',
      name: 'MerchantAfterSales',
      component: () => import('@/views/MerchantAfterSalesView.vue'),
      meta: { requiresAuth: true, requiresMerchant: true },
    },
    {
      path: '/square',
      name: 'Square',
      component: () => import('@/views/SquareView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/seckill',
      name: 'Seckill',
      component: () => import('@/views/SeckillView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/agent-console',
      name: 'AgentConsole',
      component: () => import('@/views/AgentConsoleView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
  ],
})

// 判断当前用户是否为管理员（userInfo 缺失时先拉取）
async function isAdminUser(authStore: ReturnType<typeof useAuthStore>): Promise<boolean> {
  if (!authStore.userInfo) {
    try {
      await authStore.fetchUserInfo()
    } catch {
      return false
    }
  }
  return !!authStore.userInfo?.roles?.includes('ROLE_ADMIN')
}

// 判断当前用户是否为已开通的商家。
// 商家审核通过时后端会自动授予 ROLE_MERCHANT（MerchantServiceImpl#grantMerchantRole），
// 所以这里读角色即可，不需要额外打接口查商家状态。
// 注意：这一层只是把入口收敛掉，真正的权限边界在后端的 @PreAuthorize("hasRole('MERCHANT')")。
async function isMerchantUser(authStore: ReturnType<typeof useAuthStore>): Promise<boolean> {
  if (!authStore.userInfo) {
    try {
      await authStore.fetchUserInfo()
    } catch {
      return false
    }
  }
  return !!authStore.userInfo?.roles?.includes('ROLE_MERCHANT')
}

// Navigation guard
router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    next({ name: 'Login' })
    return
  }
  if (to.name === 'Login' && authStore.isLoggedIn) {
    next({ name: 'Dashboard' })
    return
  }
  // 管理员专属页面：非管理员引导回首页
  if (to.meta.requiresAdmin) {
    const admin = await isAdminUser(authStore)
    if (!admin) {
      next({ name: 'Dashboard' })
      return
    }
  }
  // 商家专属页面：非商家引导回首页
  if (to.meta.requiresMerchant) {
    const merchant = await isMerchantUser(authStore)
    if (!merchant) {
      next({ name: 'Dashboard' })
      return
    }
  }
  next()
})

export default router

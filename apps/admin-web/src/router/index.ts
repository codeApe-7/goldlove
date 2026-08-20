import { createRouter, createWebHistory, type Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

export function createAuthGuard(router: Router) {
  return (to: { meta: Record<string, unknown> }) => {
    const auth = useAuthStore()
    if (to.meta.requiresAuth && !auth.isAuthenticated) {
      return { name: 'login' }
    }
    if (to.meta.guestOnly && auth.isAuthenticated) {
      return { name: 'dashboard' }
    }
    return true
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
        { path: 'profiles', name: 'profiles', component: () => import('@/views/ProfilesView.vue') },
        {
          path: 'profiles/:id',
          name: 'profile-detail',
          component: () => import('@/views/ProfileDetailView.vue'),
        },
        {
          path: 'payment-orders',
          name: 'payment-orders',
          component: () => import('@/views/PaymentOrdersView.vue'),
        },
        {
          path: 'activation-codes',
          name: 'activation-codes',
          component: () => import('@/views/ActivationCodesView.vue'),
        },
        {
          path: 'field-definitions',
          name: 'field-definitions',
          component: () => import('@/views/FieldDefinitionsView.vue'),
        },
      ],
    },
  ],
})

router.beforeEach(createAuthGuard(router))

export default router

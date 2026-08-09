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
        {
          path: 'guests/register',
          name: 'guests-register',
          component: () => import('@/views/GuestRegisterView.vue'),
        },
        {
          path: 'field-definitions',
          name: 'field-definitions',
          component: () => import('@/views/FieldDefinitionsView.vue'),
        },
        { path: 'reviews', name: 'reviews', component: () => import('@/views/ReviewsView.vue') },
        {
          path: 'reviews/:id',
          name: 'review-detail',
          component: () => import('@/views/ReviewDetailView.vue'),
        },
      ],
    },
  ],
})

router.beforeEach(createAuthGuard(router))

export default router

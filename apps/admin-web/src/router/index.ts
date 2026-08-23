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
          // 详情改成了列表页上的右侧抽屉。这条路径保留给旧链接与书签，
          // 直接翻译成抽屉的打开方式，不再维护第二份详情界面。
          path: 'profiles/:id',
          name: 'profile-detail',
          redirect: (to) => ({ name: 'profiles', query: { profile: String(to.params.id) } }),
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
          path: 'courses',
          name: 'courses',
          component: () => import('@/views/CoursesView.vue'),
        },
        {
          path: 'course-collections',
          name: 'course-collections',
          component: () => import('@/views/CourseCollectionsView.vue'),
        },
        {
          path: 'field-definitions',
          name: 'field-definitions',
          component: () => import('@/views/FieldDefinitionsView.vue'),
        },
        {
          path: 'payment-settings',
          name: 'payment-settings',
          component: () => import('@/views/PaymentSettingsView.vue'),
        },
      ],
    },
  ],
})

router.beforeEach(createAuthGuard(router))

export default router

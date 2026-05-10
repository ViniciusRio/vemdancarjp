import AgendaHomeView from '@/views/AgendaHomeView.vue'
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: AgendaHomeView,
    },
    {
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('@/views/AdminLoginView.vue')
    },
    {
      path: 'admin',
      name: 'admin',
      component: () => import('@/views/AdminView.vue'),
      meta: { requiresAuth: true }
    }
  ],
})

router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true

  const { useAuthStore } = await import('@/stores/authStore')
  const authStore = useAuthStore()

  await authStore.init()

  if (!authStore.isLoggedIn) return { name: 'admin-login' }
  if (!authStore.isAdmin) return { name: 'home' }

  return true
})

export default router

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
  ],
})

export default router

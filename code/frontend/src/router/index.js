import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    redirect: '/reservations'
  },
  {
    path: '/reservations',
    name: 'ReservationList',
    component: () => import('@/views/ReservationList.vue'),
    meta: { title: '预约列表' }
  },
  {
    path: '/reservations/create',
    name: 'ReservationForm',
    component: () => import('@/views/ReservationForm.vue'),
    meta: { title: '发起预约' }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '数据看板' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/reservations'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.meta.title) {
    document.title = `${to.meta.title} — 会议室预约系统`
  }

  if (!to.meta.public && !userStore.isAuthenticated) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.path === '/login' && userStore.isAuthenticated) {
    next('/reservations')
  } else {
    next()
  }
})

export default router

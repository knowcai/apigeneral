import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import LoginView from '../views/LoginView.vue'
import DatasourceView from '../views/DatasourceView.vue'
import ApiView from '../views/ApiView.vue'
import DashboardView from '../views/DashboardView.vue'
import LogView from '../views/LogView.vue'
import PolicyView from '../views/PolicyView.vue'
import UserView from '../views/UserView.vue'
import ConsumerView from '../views/ConsumerView.vue'
import AuditView from '../views/AuditView.vue'
import ThemeView from '../views/ThemeView.vue'
import ApprovalView from '../views/ApprovalView.vue'
import { auth } from '../stores/auth'
import http from '../api/http'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView },
    {
      path: '/',
      component: MainLayout,
      redirect: '/datasources',
      meta: { requiresAuth: true },
      children: [
        { path: 'datasources', component: DatasourceView },
        { path: 'apis', component: ApiView },
        { path: 'themes', component: ThemeView },
        { path: 'approvals', component: ApprovalView },
        { path: 'dashboard', component: DashboardView },
        { path: 'logs', component: LogView },
        { path: 'policy', component: PolicyView },
        { path: 'consumers', component: ConsumerView, meta: { superAdmin: true } },
        { path: 'users', component: UserView, meta: { superAdmin: true } },
        { path: 'audit', component: AuditView }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  if (to.path === '/login') {
    if (auth.isLoggedIn.value) return '/datasources'
    return true
  }
  if (!auth.getToken()) {
    return '/login'
  }
  if (!auth.state.user) {
    try {
      const user = await http.get('/admin/auth/me')
      auth.setSession(auth.getToken(), user as any)
    } catch {
      auth.clear()
      return '/login'
    }
  }
  if (to.meta.superAdmin && !auth.isSuperAdmin.value) {
    return '/datasources'
  }
  return true
})

export default router

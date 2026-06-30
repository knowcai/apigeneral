import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import MainLayout from '../layout/MainLayout.vue'
import LoginView from '../views/LoginView.vue'
import DatasourceView from '../views/DatasourceView.vue'
import ApiView from '../views/ApiView.vue'
import DashboardView from '../views/DashboardView.vue'
import LogView from '../views/LogView.vue'
import PolicyView from '../views/PolicyView.vue'
import UserView from '../views/UserView.vue'
import AuditView from '../views/AuditView.vue'
import ThemeView from '../views/ThemeView.vue'
import ApprovalView from '../views/ApprovalView.vue'
import { auth } from '../stores/auth'
import http from '../api/http'
import { i18n } from '../locales'
import type { UserInfo } from '../stores/auth'

function defaultHome() {
  if (auth.isApiViewer.value) return '/dashboard'
  if (auth.isApiEditor.value) return '/apis'
  return '/themes'
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView },
    {
      path: '/',
      component: MainLayout,
      redirect: () => defaultHome(),
      meta: { requiresAuth: true },
      children: [
        { path: 'datasources', component: DatasourceView, meta: { titleKey: 'datasource.title', requiresEditor: true } },
        { path: 'apis', component: ApiView, meta: { titleKey: 'api.title', requiresEditor: true } },
        { path: 'themes', component: ThemeView, meta: { titleKey: 'theme.title' } },
        { path: 'approvals', component: ApprovalView, meta: { titleKey: 'approval.title', requiresEditor: true } },
        { path: 'dashboard', component: DashboardView, meta: { titleKey: 'dashboard.title' } },
        { path: 'logs', component: LogView, meta: { titleKey: 'log.title' } },
        { path: 'policy', component: PolicyView, meta: { titleKey: 'policy.title', superAdmin: true } },
        { path: 'users', component: UserView, meta: { superAdmin: true, titleKey: 'user.title' } },
        { path: 'audit', component: AuditView, meta: { titleKey: 'audit.title', superAdmin: true } }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  if (to.path === '/login') {
    if (auth.state.user) return defaultHome()
    return true
  }
  if (!auth.state.user) {
    try {
      const user = await http.get<UserInfo>('/admin/auth/me')
      auth.setSession(auth.getToken(), user)
    } catch {
      return '/login'
    }
  }
  if (to.meta.superAdmin && !auth.isSuperAdmin.value) {
    ElMessage.warning(i18n.global.t('common.accessDenied'))
    return defaultHome()
  }
  if (to.meta.requiresEditor && auth.isApiViewer.value) {
    ElMessage.warning(i18n.global.t('common.accessDenied'))
    return defaultHome()
  }
  return true
})

router.afterEach((to) => {
  const key = to.meta.titleKey as string | undefined
  if (key) {
    document.title = `${i18n.global.t(key)} · ${i18n.global.t('common.brandName')}`
  }
})

export default router

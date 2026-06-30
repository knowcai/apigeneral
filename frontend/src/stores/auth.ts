import { reactive, computed } from 'vue'

export type UserRole = 'SUPER_ADMIN' | 'API_EDITOR' | 'API_VIEWER'

export interface UserInfo {
  id: number
  username: string
  displayName?: string
  role: UserRole
  enabled?: boolean
}

export interface ThemeBrief {
  id: number
  myRole?: 'THEME_ADMIN' | 'MEMBER' | string
}

const TOKEN_KEY = 'gw_token'

const state = reactive({
  token: sessionStorage.getItem(TOKEN_KEY) || '',
  user: null as UserInfo | null
})

export const auth = {
  state,
  isLoggedIn: computed(() => !!state.user),
  isSuperAdmin: computed(() => state.user?.role === 'SUPER_ADMIN'),
  isApiEditor: computed(() => state.user?.role === 'API_EDITOR'),
  isApiViewer: computed(() => state.user?.role === 'API_VIEWER'),
  canEditPolicy: computed(() => state.user?.role === 'SUPER_ADMIN'),
  canEditDatasource: computed(() => ['SUPER_ADMIN', 'API_EDITOR'].includes(state.user?.role || '')),
  canManageUsers: computed(() => state.user?.role === 'SUPER_ADMIN'),
  canManageThemes: computed(() => state.user?.role === 'SUPER_ADMIN'),
  canCreateApi: computed(() => ['SUPER_ADMIN', 'API_EDITOR'].includes(state.user?.role || '')),
  canViewAudit: computed(() => state.user?.role === 'SUPER_ADMIN'),
  canViewPolicy: computed(() => state.user?.role === 'SUPER_ADMIN'),
  canApprove: computed(() => ['SUPER_ADMIN', 'API_EDITOR'].includes(state.user?.role || '')),
  canWriteInTheme(themeId: number, themes: ThemeBrief[]) {
    const u = state.user
    if (!u) return false
    if (u.role === 'SUPER_ADMIN') return true
    if (u.role === 'API_VIEWER') return false
    return themes.some(t => t.id === themeId)
  },
  canAdminTheme(themeId: number, themes: ThemeBrief[]) {
    const u = state.user
    if (!u) return false
    if (u.role === 'SUPER_ADMIN') return true
    if (u.role === 'API_VIEWER') return false
    return themes.some(t => t.id === themeId && t.myRole === 'THEME_ADMIN')
  },
  canEditApi(def: { themeId?: number }, themes: ThemeBrief[] = []) {
    if (def.themeId != null) {
      return auth.canWriteInTheme(def.themeId, themes)
    }
    return auth.canCreateApi.value
  },
  canDeleteApi(def: { themeId?: number }, themes: ThemeBrief[] = []) {
    if (def.themeId != null) {
      return auth.canAdminTheme(def.themeId, themes)
    }
    return auth.isSuperAdmin.value
  },
  canDeleteDatasource(row: { themeId?: number }, themes: ThemeBrief[] = []) {
    return auth.canAdminTheme(row.themeId ?? 0, themes)
  },
  setSession(token: string, user: UserInfo) {
    state.token = token
    state.user = user
    if (token) {
      sessionStorage.setItem(TOKEN_KEY, token)
    }
  },
  async clear() {
    state.token = ''
    state.user = null
    sessionStorage.removeItem(TOKEN_KEY)
    try {
      const { default: http } = await import('../api/http')
      await http.post('/admin/auth/logout')
    } catch {
      // ignore
    }
  },
  getToken() {
    return state.token
  }
}

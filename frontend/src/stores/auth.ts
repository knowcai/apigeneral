import { reactive, computed } from 'vue'

export type UserRole = 'SUPER_ADMIN' | 'API_EDITOR' | 'API_VIEWER'

export interface UserInfo {
  id: number
  username: string
  displayName?: string
  role: UserRole
  enabled?: boolean
}

const TOKEN_KEY = 'gw_token'

const state = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: null as UserInfo | null
})

export const auth = {
  state,
  isLoggedIn: computed(() => !!state.token),
  isSuperAdmin: computed(() => state.user?.role === 'SUPER_ADMIN'),
  isApiEditor: computed(() => state.user?.role === 'API_EDITOR'),
  isApiViewer: computed(() => state.user?.role === 'API_VIEWER'),
  canEditPolicy: computed(() => state.user?.role === 'SUPER_ADMIN'),
  canEditDatasource: computed(() => ['SUPER_ADMIN', 'API_EDITOR'].includes(state.user?.role || '')),
  canManageUsers: computed(() => state.user?.role === 'SUPER_ADMIN'),
  canManageThemes: computed(() => state.user?.role === 'SUPER_ADMIN'),
  canCreateApi: computed(() => ['SUPER_ADMIN', 'API_EDITOR'].includes(state.user?.role || '')),
  canEditApi(def: { themeId?: number }, themeIds: number[] = []) {
    const u = state.user
    if (!u) return false
    if (u.role === 'SUPER_ADMIN') return true
    if (u.role === 'API_VIEWER') return false
    if (def.themeId != null && themeIds.length > 0) {
      return themeIds.includes(def.themeId)
    }
    return u.role === 'API_EDITOR'
  },
  setSession(token: string, user: UserInfo) {
    state.token = token
    state.user = user
    localStorage.setItem(TOKEN_KEY, token)
  },
  clear() {
    state.token = ''
    state.user = null
    localStorage.removeItem(TOKEN_KEY)
  },
  getToken() {
    return state.token
  }
}

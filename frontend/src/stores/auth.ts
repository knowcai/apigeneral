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
  canEditDatasource: computed(() => state.user?.role === 'SUPER_ADMIN'),
  canManageUsers: computed(() => state.user?.role === 'SUPER_ADMIN'),
  canCreateApi: computed(() => ['SUPER_ADMIN', 'API_EDITOR'].includes(state.user?.role || '')),
  canEditApi(def: { createdBy?: string }) {
    const u = state.user
    if (!u) return false
    if (u.role === 'SUPER_ADMIN') return true
    if (u.role === 'API_EDITOR') return def.createdBy === u.username
    return false
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

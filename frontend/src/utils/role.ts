import type { UserRole } from '../stores/auth'

export function roleLabelKey(role?: UserRole | string): string {
  if (role === 'SUPER_ADMIN') return 'role.superAdmin'
  if (role === 'API_VIEWER') return 'role.viewer'
  return 'role.regularUser'
}

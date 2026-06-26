import { ElMessage } from 'element-plus'
import type { Router } from 'vue-router'
import { isApprovalResult } from '../api/http'

export function notifyApprovalResult(
  result: unknown,
  fallback: string,
  t: (key: string) => string,
  router?: Router
) {
  if (isApprovalResult(result)) {
    ElMessage({
      type: 'success',
      message: `${result.message} ${t('approval.viewCenterHint')}`,
      duration: 5000,
      showClose: true,
      onClose: () => {}
    })
    if (router) {
      setTimeout(() => router.push('/approvals'), 300)
    }
    return true
  }
  ElMessage.success(fallback)
  return false
}

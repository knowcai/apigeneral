/** 侧边栏「待我审批」角标与审批页列表同步刷新。 */
let refreshHandler: (() => void) | null = null

export function registerApprovalPendingRefresh(fn: () => void) {
  refreshHandler = fn
  return () => {
    if (refreshHandler === fn) refreshHandler = null
  }
}

export function notifyApprovalPendingRefresh() {
  refreshHandler?.()
}

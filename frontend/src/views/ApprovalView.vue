<template>
  <div>
    <div class="toolbar">
      <h2>{{ t('approval.title') }}</h2>
      <el-badge :value="myTasks.length" :hidden="!myTasks.length">
        <el-tag type="warning">{{ t('approval.pendingBadge') }} {{ myTasks.length }}</el-tag>
      </el-badge>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane :label="t('approval.tabMine')" name="mine">
        <div class="hint block-hint">{{ t('approval.hintMine') }}</div>
        <el-table :data="myTasks" stripe>
          <el-table-column prop="title" :label="t('col.title')" min-width="200" />
          <el-table-column prop="resourceType" :label="t('col.resource')" width="140">
            <template #default="{ row }">{{ resourceLabel(row.resourceType) }}</template>
          </el-table-column>
          <el-table-column :label="t('col.action')" width="90">
            <template #default="{ row }">{{ actionLabel(row.action) }}</template>
          </el-table-column>
          <el-table-column prop="submitterName" :label="t('col.submitter')" width="120" />
          <el-table-column :label="t('col.actions')" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTask(row)">{{ t('common.view') }}</el-button>
              <el-button link type="success" @click="act(row.taskId, true)">{{ t('common.approve') }}</el-button>
              <el-button link type="danger" @click="act(row.taskId, false)">{{ t('common.reject') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!myTasks.length" :description="t('approval.emptyMine')" />
      </el-tab-pane>

      <el-tab-pane :label="t('approval.tabPending')" name="pending">
        <div class="hint block-hint">{{ t('approval.hintPending') }}</div>
        <el-table :data="pending" stripe>
          <el-table-column prop="title" :label="t('col.title')" min-width="200" />
          <el-table-column prop="resourceType" :label="t('col.resource')" width="140">
            <template #default="{ row }">{{ resourceLabel(row.resourceType) }}</template>
          </el-table-column>
          <el-table-column :label="t('col.action')" width="90">
            <template #default="{ row }">{{ actionLabel(row.action) }}</template>
          </el-table-column>
          <el-table-column prop="submitterName" :label="t('col.submitter')" width="120" />
          <el-table-column prop="createdAt" :label="t('col.submittedAt')" width="170" />
          <el-table-column :label="t('col.actions')" width="160">
            <template #default="{ row }">
              <el-button link @click="openRequest(row)">{{ t('common.view') }}</el-button>
              <el-button
                v-if="canWithdraw(row)"
                link
                type="warning"
                @click="withdraw(row)"
              >{{ t('approval.withdraw') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!pending.length" :description="t('approval.emptyPending')" />
      </el-tab-pane>

      <el-tab-pane :label="t('approval.tabHistory')" name="history">
        <div class="hint block-hint">{{ t('approval.hintHistory') }}</div>
        <el-table :data="history" stripe size="small">
          <el-table-column prop="title" :label="t('col.title')" min-width="200" />
          <el-table-column prop="resourceType" :label="t('col.resource')" width="140">
            <template #default="{ row }">{{ resourceLabel(row.resourceType) }}</template>
          </el-table-column>
          <el-table-column :label="t('col.action')" width="90">
            <template #default="{ row }">{{ actionLabel(row.action) }}</template>
          </el-table-column>
          <el-table-column prop="status" :label="t('col.status')" width="100">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="submitterName" :label="t('col.submitter')" width="120" />
          <el-table-column :label="t('col.approver')" width="120">
            <template #default="{ row }">{{ formatApprover(row) }}</template>
          </el-table-column>
          <el-table-column prop="resolvedAt" :label="t('col.resolvedAt')" width="170" />
          <el-table-column :label="t('col.actions')" width="100">
            <template #default="{ row }">
              <el-button link @click="openRequest(row)">{{ t('common.view') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!history.length" :description="t('approval.emptyHistory')" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="detailVisible" :title="t('approval.detailTitle')" width="720px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item :label="t('col.title')">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item :label="t('col.submitter')">{{ detail.submitterName }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.approverName" :label="t('col.approver')">{{ formatApprover(detail) }}</el-descriptions-item>
        <el-descriptions-item :label="t('col.resource')">{{ resourceLabel(detail.resourceType) }}</el-descriptions-item>
        <el-descriptions-item :label="t('col.action')">{{ actionLabel(detail.action) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.status" :label="t('col.status')">{{ statusLabel(detail.status) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.resolvedAt" :label="t('col.resolvedAt')">{{ detail.resolvedAt }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.approverComment" :label="t('approval.approverComment')" :span="2">
          {{ detail.approverComment }}
        </el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="detail.diffError" type="warning" :title="t('approval.diffError')" :description="detail.diffError" show-icon :closable="false" class="diff-alert" />
      <div v-if="isThemeApiKey(detail)" class="key-summary-block">
        <div class="payload-title">{{ t('approval.keySummaryTitle') }}</div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="t('col.name')">{{ themeKeySummary(detail).name }}</el-descriptions-item>
          <el-descriptions-item v-if="themeKeySummary(detail).department" :label="t('approval.department')">
            {{ themeKeySummary(detail).department }}
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.action === 'CREATE'" class="hint block-hint key-hint">{{ t('approval.themeKeyCreateHint') }}</div>
        <div v-else-if="detail.action === 'DELETE'" class="hint block-hint key-hint">{{ t('approval.themeKeyDeleteHint') }}</div>
      </div>
      <template v-else>
        <div v-if="detail.diff?.length" class="diff-block">
          <div class="payload-title">{{ t('approval.diff') }}</div>
          <el-table :data="detail.diff" stripe size="small" max-height="240">
            <el-table-column prop="field" :label="t('approval.field')" width="140" />
            <el-table-column :label="t('approval.before')" min-width="180">
              <template #default="{ row }"><pre class="diff-cell">{{ formatVal(row.before) }}</pre></template>
            </el-table-column>
            <el-table-column :label="t('approval.after')" min-width="180">
              <template #default="{ row }"><pre class="diff-cell">{{ formatVal(row.after) }}</pre></template>
            </el-table-column>
          </el-table>
        </div>
        <div v-else-if="!detail.diffError" class="hint">{{ t('approval.noDiff') }}</div>
        <div v-if="detail.payload" class="payload-block">
          <div class="payload-title">{{ t('approval.payloadTitle') }}</div>
          <pre class="payload">{{ formatPayload(detail.payload) }}</pre>
        </div>
      </template>
      <el-input v-if="detail.taskId" v-model="comment" type="textarea" :placeholder="t('approval.commentPlaceholder')" :rows="2" />
      <template #footer>
        <el-button @click="detailVisible = false">{{ t('common.close') }}</el-button>
        <template v-if="detail.taskId">
          <el-button type="danger" @click="act(detail.taskId, false)">{{ t('common.reject') }}</el-button>
          <el-button type="success" @click="act(detail.taskId, true)">{{ t('common.approve') }}</el-button>
        </template>
      </template>
    </el-dialog>

    <el-dialog v-model="keyVisible" :title="t('approval.keyRevealedTitle')" width="520px" :close-on-click-modal="false">
      <el-alert type="error" :closable="false" show-icon :title="t('theme.apiKeyWarningStrong')" class="key-alert" />
      <el-input class="key-box mono-input" :model-value="revealedKey" readonly>
        <template #append><el-button @click="copyRevealedKey">{{ t('common.copy') }}</el-button></template>
      </el-input>
      <template #footer>
        <el-button type="primary" @click="keyVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { auth } from '../stores/auth'
import { notifyApprovalPendingRefresh } from '../utils/approvalPending'

const { t, te } = useI18n()

const tab = ref('mine')
const myTasks = ref<any[]>([])
const pending = ref<any[]>([])
const history = ref<any[]>([])
const detailVisible = ref(false)
const keyVisible = ref(false)
const revealedKey = ref('')
const comment = ref('')
const detail = reactive<any>({})

function actionLabel(action?: string) {
  if (!action) return ''
  const key = `approval.actions.${action}`
  return te(key) ? t(key) : action
}

function resourceLabel(type?: string) {
  if (!type) return ''
  const key = `approval.resources.${type}`
  return te(key) ? t(key) : type
}

function formatApprover(row: any) {
  if (!row?.approverName) return '—'
  if (row.directApply) return `${row.approverName}（${t('approval.directApply')}）`
  return row.approverName
}

function formatPayload(raw?: string) {
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function isThemeApiKey(row: any) {
  return row?.resourceType === 'THEME_API_KEY'
}

function themeKeyNameFromTitle(title?: string) {
  if (!title) return '—'
  const idx = Math.max(title.lastIndexOf(':'), title.lastIndexOf('：'))
  return idx >= 0 ? title.slice(idx + 1).trim() : title
}

function themeKeySummary(row: any) {
  let name = themeKeyNameFromTitle(row.title)
  let department: string | null = null
  if (row.payload) {
    try {
      const p = JSON.parse(row.payload)
      if (p.name) name = p.name
      if (p.department) department = p.department
    } catch { /* use title fallback */ }
  }
  return { name, department }
}

function formatVal(val: unknown) {
  if (val == null) return ''
  if (typeof val === 'object') return JSON.stringify(val, null, 2)
  return String(val)
}

function statusLabel(status?: string) {
  if (!status) return ''
  const key = `approval.statuses.${status}`
  return te(key) ? t(key) : status
}

function canWithdraw(row: any) {
  return row.status === 'PENDING' && row.submitterId === auth.state.user?.id
}

async function withdraw(row: any) {
  try {
    await ElMessageBox.confirm(t('approval.confirmWithdraw'), t('common.confirm'), { type: 'warning' })
    await http.post(`/admin/approvals/requests/${row.id}/withdraw`)
    await load()
    ElMessage.success(t('approval.withdrawn'))
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || t('common.operationFailed'))
  }
}

async function load() {
  try {
    myTasks.value = await http.get('/admin/approvals/my-tasks')
    pending.value = await http.get('/admin/approvals/pending')
    history.value = await http.get('/admin/approvals/history', { params: { limit: 100 } })
  } catch (e: any) {
    ElMessage.error(e?.message || t('common.operationFailed'))
  } finally {
    notifyApprovalPendingRefresh()
  }
}

function openTask(row: any) {
  Object.assign(detail, { ...row, taskId: row.taskId })
  comment.value = ''
  detailVisible.value = true
}

function openRequest(row: any) {
  Object.assign(detail, { ...row, taskId: null })
  comment.value = ''
  detailVisible.value = true
}

function copyRevealedKey() {
  navigator.clipboard.writeText(revealedKey.value)
  ElMessage.success(t('common.copied'))
}

async function act(taskId: number, approved: boolean) {
  try {
    await ElMessageBox.confirm(
      approved ? t('approval.confirmApprove') : t('approval.confirmReject'),
      t('common.confirm'),
      { type: approved ? 'info' : 'warning' }
    )
    const url = `/admin/approvals/tasks/${taskId}/${approved ? 'approve' : 'reject'}`
    if (approved) {
      const result = await http.post<{ apiKey?: string; themeKeyPickupPending?: boolean }>(url, { comment: comment.value || undefined })
      detailVisible.value = false
      await load()
      if (result?.apiKey) {
        revealedKey.value = result.apiKey
        keyVisible.value = true
      } else if (result?.themeKeyPickupPending) {
        ElMessage.info(t('approval.themeKeyPickupHint'))
      }
      ElMessage.success(t('approval.approved'))
    } else {
      await http.post(url, { comment: comment.value || undefined })
      detailVisible.value = false
      await load()
      ElMessage.success(t('approval.rejected'))
    }
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || t('common.operationFailed'))
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.toolbar h2 { margin: 0; }
.payload-title { margin: 16px 0 8px; font-weight: 600; font-size: 13px; }
.payload {
  background: #fafafa; border: 1px solid #e5e5e5; padding: 12px; border-radius: 6px;
  max-height: 320px; overflow: auto; font-size: 12px;
}
.hint { font-size: 12px; color: #737373; }
.block-hint { margin-bottom: 12px; padding: 10px 12px; background: #fafafa; border-radius: 6px; }
.diff-cell { margin: 0; font-size: 11px; white-space: pre-wrap; word-break: break-all; }
.diff-block { margin-bottom: 12px; }
.diff-alert { margin-top: 12px; }
.key-box { margin-top: 12px; }
.key-alert { margin-bottom: 12px; }
.key-hint { margin-top: 12px; }
.key-summary-block { margin-top: 12px; }
.payload-block { margin-top: 4px; }
.mono-input :deep(.el-input__inner) { font-family: ui-monospace, monospace; }
</style>

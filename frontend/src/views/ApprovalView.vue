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
          <el-table-column prop="resourceType" :label="t('col.resource')" width="120" />
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
          <el-table-column prop="resourceType" :label="t('col.resource')" width="120" />
          <el-table-column :label="t('col.action')" width="90">
            <template #default="{ row }">{{ actionLabel(row.action) }}</template>
          </el-table-column>
          <el-table-column prop="submitterName" :label="t('col.submitter')" width="120" />
          <el-table-column prop="createdAt" :label="t('col.submittedAt')" width="170" />
          <el-table-column :label="t('col.actions')" width="100">
            <template #default="{ row }">
              <el-button link @click="openRequest(row)">{{ t('common.view') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="detailVisible" :title="t('approval.detailTitle')" width="720px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item :label="t('col.title')">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item :label="t('col.submitter')">{{ detail.submitterName }}</el-descriptions-item>
        <el-descriptions-item :label="t('col.resource')">{{ detail.resourceType }}</el-descriptions-item>
        <el-descriptions-item :label="t('col.action')">{{ actionLabel(detail.action) }}</el-descriptions-item>
      </el-descriptions>
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
      <div v-else class="hint">{{ t('approval.noDiff') }}</div>
      <div class="payload-title">{{ t('approval.payloadTitle') }}</div>
      <pre class="payload">{{ formatPayload(detail.payload) }}</pre>
      <el-input v-if="detail.taskId" v-model="comment" type="textarea" :placeholder="t('approval.commentPlaceholder')" :rows="2" />
      <template #footer>
        <el-button @click="detailVisible = false">{{ t('common.close') }}</el-button>
        <template v-if="detail.taskId">
          <el-button type="danger" @click="act(detail.taskId, false)">{{ t('common.reject') }}</el-button>
          <el-button type="success" @click="act(detail.taskId, true)">{{ t('common.approve') }}</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'

const { t, te } = useI18n()

const tab = ref('mine')
const myTasks = ref<any[]>([])
const pending = ref<any[]>([])
const detailVisible = ref(false)
const comment = ref('')
const detail = reactive<any>({})

function actionLabel(action?: string) {
  if (!action) return ''
  const key = `approval.actions.${action}`
  return te(key) ? t(key) : action
}

function formatPayload(raw?: string) {
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function formatVal(val: unknown) {
  if (val == null) return ''
  if (typeof val === 'object') return JSON.stringify(val, null, 2)
  return String(val)
}

async function load() {
  myTasks.value = await http.get('/admin/approvals/my-tasks')
  pending.value = await http.get('/admin/approvals/pending')
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

async function act(taskId: number, approved: boolean) {
  const label = approved ? t('common.approve') : t('common.reject')
  try {
    await ElMessageBox.confirm(
      approved ? t('approval.confirmApprove') : t('approval.confirmReject'),
      t('common.confirm'),
      { type: approved ? 'info' : 'warning' }
    )
    const url = `/admin/approvals/tasks/${taskId}/${approved ? 'approve' : 'reject'}`
    await http.post(url, { comment: comment.value || undefined })
    detailVisible.value = false
    await load()
    ElMessage.success(approved ? t('approval.approved') : t('approval.rejected'))
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
  background: #fafafa;
  border: 1px solid #e5e5e5;
  padding: 12px;
  border-radius: 6px;
  max-height: 320px;
  overflow: auto;
  font-size: 12px;
}
.hint { font-size: 12px; color: #737373; }
.block-hint { margin-bottom: 12px; padding: 10px 12px; background: #fafafa; border-radius: 6px; }
.diff-cell { margin: 0; font-size: 11px; white-space: pre-wrap; word-break: break-all; }
.diff-block { margin-bottom: 12px; }
</style>

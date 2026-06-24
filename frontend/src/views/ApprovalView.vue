<template>
  <div>
    <div class="toolbar">
      <h2>审批中心</h2>
      <el-badge :value="myTasks.length" :hidden="!myTasks.length">
        <el-tag type="warning">待我审批 {{ myTasks.length }}</el-tag>
      </el-badge>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="待我审批" name="mine">
        <div class="hint block-hint">主题管理员与超级管理员可在此审批；任一管理员通过即可生效。</div>
        <el-table :data="myTasks" stripe>
          <el-table-column prop="title" label="标题" min-width="200" />
          <el-table-column prop="resourceType" label="资源" width="120" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">{{ actionLabel(row.action) }}</template>
          </el-table-column>
          <el-table-column prop="submitterName" label="提交人" width="120" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTask(row)">查看</el-button>
              <el-button link type="success" @click="act(row.taskId, true)">通过</el-button>
              <el-button link type="danger" @click="act(row.taskId, false)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!myTasks.length" description="暂无待审批任务" />
      </el-tab-pane>

      <el-tab-pane label="进行中的审批" name="pending">
        <div class="hint block-hint">提交人请在此查看自己发起的审批；审批人请在「待我审批」处理任务。</div>
        <el-table :data="pending" stripe>
          <el-table-column prop="title" label="标题" min-width="200" />
          <el-table-column prop="resourceType" label="资源" width="120" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">{{ actionLabel(row.action) }}</template>
          </el-table-column>
          <el-table-column prop="submitterName" label="提交人" width="120" />
          <el-table-column prop="createdAt" label="提交时间" width="170" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link @click="openRequest(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="detailVisible" title="审批详情" width="720px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="提交人">{{ detail.submitterName }}</el-descriptions-item>
        <el-descriptions-item label="资源">{{ detail.resourceType }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ actionLabel(detail.action) }}</el-descriptions-item>
      </el-descriptions>
      <div class="payload-title">变更内容</div>
      <pre class="payload">{{ formatPayload(detail.payload) }}</pre>
      <el-input v-if="detail.taskId" v-model="comment" type="textarea" placeholder="审批意见（可选）" :rows="2" />
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <template v-if="detail.taskId">
          <el-button type="danger" @click="act(detail.taskId, false)">驳回</el-button>
          <el-button type="success" @click="act(detail.taskId, true)">通过</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'

const tab = ref('mine')
const myTasks = ref<any[]>([])
const pending = ref<any[]>([])
const detailVisible = ref(false)
const comment = ref('')
const detail = reactive<any>({})

const ACTION_LABELS: Record<string, string> = {
  CREATE: '新建',
  UPDATE: '修改',
  PUBLISH: '发布',
  SUSPEND: '关闭',
  RESUME: '重启'
}

function actionLabel(action?: string) {
  if (!action) return ''
  return ACTION_LABELS[action] || action
}

function formatPayload(raw?: string) {
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
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
  const label = approved ? '通过' : '驳回'
  try {
    await ElMessageBox.confirm(`确定${label}该审批？`, '确认', { type: approved ? 'info' : 'warning' })
    const url = `/admin/approvals/tasks/${taskId}/${approved ? 'approve' : 'reject'}`
    await http.post(url, { comment: comment.value || undefined })
    detailVisible.value = false
    await load()
    ElMessage.success(`已${label}`)
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
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
</style>

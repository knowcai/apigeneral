<template>
  <div>
    <div class="toolbar">
      <h2>调用方 / API Key</h2>
      <el-button type="primary" @click="openCreate">新建调用方</el-button>
    </div>

    <el-alert
      class="hint-bar"
      type="info"
      :closable="false"
      show-icon
      title="动态 API 调用须在请求头携带 API Key：Authorization: Bearer &lt;key&gt; 或 X-Api-Key: &lt;key&gt;"
    />

    <el-table :data="consumers" stripe>
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="department" label="部门" width="120" />
      <el-table-column prop="keyPrefix" label="Key 前缀" width="120" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">{{ row.status === 'ACTIVE' ? '启用' : '禁用' }}</template>
      </el-table-column>
      <el-table-column label="授权 API" min-width="200">
        <template #default="{ row }">
          <span class="api-tags">{{ apiNames(row.apiIds).join('、') || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link @click="openEdit(row)">编辑</el-button>
          <el-button link @click="rotateKey(row)">轮换 Key</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑调用方' : '新建调用方'" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="部门"><el-input v-model="form.department" placeholder="可选" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="授权 API">
          <el-select v-model="form.apiIds" multiple filterable style="width: 100%" placeholder="选择可调用的 API">
            <el-option v-for="api in apis" :key="api.id" :label="`${api.apiCode} (${api.name})`" :value="api.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="keyVisible" title="请妥善保存 API Key" width="520px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon title="Key 仅显示一次，关闭后无法再次查看，请立即复制保存。" />
      <el-input class="key-box" :model-value="createdKey" readonly>
        <template #append>
          <el-button @click="copyKey">复制</el-button>
        </template>
      </el-input>
      <template #footer>
        <el-button type="primary" @click="keyVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'

const consumers = ref<any[]>([])
const apis = ref<any[]>([])
const visible = ref(false)
const keyVisible = ref(false)
const createdKey = ref('')
const form = reactive<any>({ name: '', department: '', status: 'ACTIVE', apiIds: [] as number[] })

function apiNames(ids: number[]) {
  return (ids || []).map(id => apis.value.find(a => a.id === id)?.apiCode).filter(Boolean)
}

async function load() {
  try {
    consumers.value = await http.get('/admin/consumers')
  } catch (e: any) {
    consumers.value = []
    ElMessage.warning(e.message || '加载调用方失败，请确认后端已重启')
  }
  try {
    apis.value = await http.get('/admin/apis')
  } catch (e: any) {
    apis.value = []
    ElMessage.error(e.message || '加载 API 列表失败')
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, name: '', department: '', status: 'ACTIVE', apiIds: [] })
  visible.value = true
}

function openEdit(row: any) {
  Object.assign(form, { id: row.id, name: row.name, department: row.department, status: row.status, apiIds: [...(row.apiIds || [])] })
  visible.value = true
}

function showKey(key: string) {
  createdKey.value = key
  keyVisible.value = true
}

async function save() {
  const payload = { name: form.name, department: form.department, status: form.status, apiIds: form.apiIds }
  if (form.id) {
    await http.put(`/admin/consumers/${form.id}`, payload)
    ElMessage.success('已保存')
  } else {
    const res = await http.post<any>('/admin/consumers', payload)
    showKey(res.apiKey)
    ElMessage.success('创建成功')
  }
  visible.value = false
  await load()
}

async function rotateKey(row: any) {
  await ElMessageBox.confirm(`轮换后旧 Key 立即失效，确定继续？`, '轮换 API Key', { type: 'warning' })
  const res = await http.post<any>(`/admin/consumers/${row.id}/rotate-key`)
  showKey(res.apiKey)
  await load()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确定删除调用方「${row.name}」？`, '删除', { type: 'warning' })
  await http.delete(`/admin/consumers/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

async function copyKey() {
  await navigator.clipboard.writeText(createdKey.value)
  ElMessage.success('已复制')
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.toolbar h2 { margin: 0; }
.hint-bar { margin-bottom: 16px; }
.key-box { margin-top: 16px; }
.api-tags { color: #525252; font-size: 13px; }
</style>

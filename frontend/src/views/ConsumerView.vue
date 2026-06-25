<template>
  <div>
    <div class="toolbar">
      <h2>{{ t('consumer.title') }}</h2>
      <el-button type="primary" @click="openCreate">{{ t('consumer.create') }}</el-button>
    </div>

    <el-alert class="hint-bar" type="info" :closable="false" show-icon :title="t('consumer.headerHint')" />

    <el-table :data="consumers" stripe>
      <el-table-column prop="name" :label="t('col.name')" min-width="140" />
      <el-table-column prop="department" :label="t('col.department')" width="120" />
      <el-table-column prop="keyPrefix" :label="t('col.keyPrefix')" width="120" />
      <el-table-column prop="status" :label="t('col.status')" width="90">
        <template #default="{ row }">{{ row.status === 'ACTIVE' ? t('status.enabled') : t('status.disabled') }}</template>
      </el-table-column>
      <el-table-column :label="t('col.grantedApis')" min-width="200">
        <template #default="{ row }"><span class="api-tags">{{ apiNames(row.apiIds).join(', ') || '—' }}</span></template>
      </el-table-column>
      <el-table-column :label="t('col.actions')" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button link @click="rotateKey(row)">{{ t('consumer.rotate') }}</el-button>
          <el-button link type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? t('consumer.edit') : t('consumer.create')" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item :label="t('col.name')"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="t('col.department')"><el-input v-model="form.department" :placeholder="t('common.optional')" /></el-form-item>
        <el-form-item :label="t('col.status')">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :label="t('status.enabled')" value="ACTIVE" />
            <el-option :label="t('status.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('col.grantedApis')">
          <el-select v-model="form.apiIds" multiple filterable style="width: 100%" :placeholder="t('consumer.selectApis')">
            <el-option v-for="api in apis" :key="api.id" :label="`${api.apiCode} (${api.name})`" :value="api.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="keyVisible" :title="t('consumer.keyDialogTitle')" width="520px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon :title="t('consumer.keyWarning')" />
      <el-input class="key-box" :model-value="createdKey" readonly>
        <template #append><el-button @click="copyKey">{{ t('common.copy') }}</el-button></template>
      </el-input>
      <template #footer>
        <el-button type="primary" @click="keyVisible = false">{{ t('consumer.keySaved') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'

const { t } = useI18n()
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
    ElMessage.warning(e.message || t('consumer.loadFailed'))
  }
  try {
    apis.value = await http.get('/admin/apis')
  } catch (e: any) {
    apis.value = []
    ElMessage.error(e.message)
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
    ElMessage.success(t('common.saved'))
  } else {
    const res = await http.post<any>('/admin/consumers', payload)
    showKey(res.apiKey)
    ElMessage.success(t('consumer.created'))
  }
  visible.value = false
  await load()
}

async function rotateKey(row: any) {
  await ElMessageBox.confirm(t('consumer.rotateConfirm'), t('consumer.rotateTitle'), { type: 'warning' })
  const res = await http.post<any>(`/admin/consumers/${row.id}/rotate-key`)
  showKey(res.apiKey)
  await load()
}

async function remove(row: any) {
  await ElMessageBox.confirm(t('consumer.deleteConfirm', { name: row.name }), t('common.delete'), { type: 'warning' })
  await http.delete(`/admin/consumers/${row.id}`)
  ElMessage.success(t('common.deleted'))
  await load()
}

async function copyKey() {
  await navigator.clipboard.writeText(createdKey.value)
  ElMessage.success(t('consumer.copied'))
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

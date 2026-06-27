<template>
  <div>
    <div class="toolbar">
      <h2>{{ t('user.title') }}</h2>
      <el-button type="primary" @click="openCreate">{{ t('user.create') }}</el-button>
    </div>

    <p class="hint">{{ t('user.hint') }}</p>

    <el-table :data="users" stripe>
      <el-table-column prop="username" :label="t('col.username')" />
      <el-table-column prop="displayName" :label="t('col.displayName')" />
      <el-table-column prop="role" :label="t('col.type')" width="120">
        <template #default="{ row }">{{ roleLabel(row.role) }}</template>
      </el-table-column>
      <el-table-column prop="enabled" :label="t('col.status')" width="90">
        <template #default="{ row }">{{ row.enabled ? t('status.enabled') : t('status.disabled') }}</template>
      </el-table-column>
      <el-table-column :label="t('col.actions')" width="180">
        <template #default="{ row }">
          <el-button link @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button link type="danger" :disabled="row.role === 'SUPER_ADMIN'" @click="remove(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? t('user.edit') : t('user.create')" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item :label="t('col.username')"><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item>
        <el-form-item :label="t('col.displayName')"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item v-if="form.id && form.role === 'SUPER_ADMIN'" :label="t('col.type')">
          <el-tag>{{ t('role.superAdmin') }}</el-tag>
        </el-form-item>
        <el-form-item v-else-if="!form.id" :label="t('col.type')">
          <el-select v-model="form.role" style="width: 100%">
            <el-option :label="t('role.regularUser')" value="API_EDITOR" />
            <el-option :label="t('role.viewer')" value="API_VIEWER" />
          </el-select>
        </el-form-item>
        <el-form-item v-else-if="form.id" :label="t('col.type')">
          <el-tag type="info">{{ roleLabel(form.role) }}</el-tag>
        </el-form-item>
        <el-form-item :label="t('user.password')">
          <el-input v-model="form.password" type="password" show-password :placeholder="form.id ? t('user.passwordPlaceholderEdit') : t('user.passwordPlaceholderNew')" />
        </el-form-item>
        <el-form-item :label="t('status.enabled')"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import type { UserRole } from '../stores/auth'

const { t } = useI18n()
const users = ref<any[]>([])
const visible = ref(false)
const form = reactive<any>({ username: '', displayName: '', role: 'API_EDITOR', password: '', enabled: true })

function roleLabel(role: UserRole) {
  if (role === 'SUPER_ADMIN') return t('role.superAdmin')
  if (role === 'API_VIEWER') return t('role.viewer')
  return t('role.regularUser')
}

async function load() {
  users.value = await http.get('/admin/users')
}

function openCreate() {
  Object.assign(form, { id: undefined, username: '', displayName: '', role: 'API_EDITOR', password: '', enabled: true })
  visible.value = true
}

function openEdit(row: any) {
  Object.assign(form, { ...row, password: '' })
  visible.value = true
}

async function save() {
  if (!form.username?.trim()) return ElMessage.warning(t('user.needUsername'))
  if (!form.id && (!form.password || form.password.length < 6)) {
    return ElMessage.warning(t('user.needPassword'))
  }
  const payload: Record<string, unknown> = {
    username: form.username.trim(),
    displayName: form.displayName?.trim() || form.username.trim(),
    enabled: form.enabled,
    password: form.password || undefined
  }
  if (!form.id) payload.role = form.role
  try {
    if (form.id) await http.put(`/admin/users/${form.id}`, payload)
    else await http.post('/admin/users', payload)
    visible.value = false
    await load()
    ElMessage.success(t('common.saved'))
  } catch (e: any) {
    ElMessage.error(e.message || t('common.operationFailed'))
  }
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(t('user.deleteConfirm', { username: row.username }), t('common.confirm'))
    await http.delete(`/admin/users/${row.id}`)
    await load()
    ElMessage.success(t('common.deleted'))
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || t('common.operationFailed'))
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.hint { font-size: 13px; color: #737373; margin: 0 0 16px; }
</style>

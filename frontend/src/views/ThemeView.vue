<template>
  <div>
    <div class="toolbar">
      <h2>{{ t('theme.title') }}</h2>
      <el-button v-if="auth.canManageThemes.value" type="primary" @click="openCreate">{{ t('theme.create') }}</el-button>
    </div>

    <el-table :data="themes" stripe @row-click="openDetail">
      <el-table-column prop="id" :label="t('theme.id')" width="80" />
      <el-table-column prop="name" :label="t('col.name')" />
      <el-table-column prop="enabled" :label="t('col.status')" width="90">
        <template #default="{ row }">{{ row.enabled ? t('status.enabled') : t('status.disabled') }}</template>
      </el-table-column>
      <el-table-column :label="t('col.admins')" width="90">
        <template #default="{ row }">{{ adminCount(row) }}</template>
      </el-table-column>
      <el-table-column :label="t('col.members')" width="90">
        <template #default="{ row }">{{ memberCount(row) }}</template>
      </el-table-column>
      <el-table-column :label="t('col.actions')" width="120">
        <template #default="{ row }">
          <el-button link @click.stop="openDetail(row)">{{ canEditTheme(row) ? t('common.manage') : t('common.view') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="dialogTitle" width="720px">
      <el-form :model="form" label-width="100px">
        <template v-if="canEditThemeConfig">
          <el-form-item v-if="form.id" :label="t('theme.id')">
            <el-input :model-value="String(form.id)" disabled />
          </el-form-item>
          <el-form-item :label="t('col.name')"><el-input v-model="form.name" :placeholder="t('theme.nameUnique')" /></el-form-item>
          <el-form-item :label="t('col.description')"><el-input v-model="form.description" type="textarea" /></el-form-item>
          <el-form-item :label="t('status.enabled')"><el-switch v-model="form.enabled" /></el-form-item>

          <el-divider content-position="left">{{ t('theme.adminSection') }}</el-divider>
          <div class="hint block-hint">{{ t('theme.adminHint') }}</div>
          <el-select v-model="form.themeAdminIds" multiple filterable :placeholder="t('theme.selectAdmins')" style="width: 100%">
            <el-option v-for="u in adminCandidateUsers" :key="u.id" :label="`${u.displayName || u.username} (${u.username})`" :value="u.id" />
          </el-select>

          <el-divider content-position="left">{{ t('theme.memberSection') }}</el-divider>
          <div class="hint block-hint">{{ t('theme.memberHint') }}</div>
          <el-select v-model="form.memberIds" multiple filterable :placeholder="t('theme.selectMembers')" style="width: 100%">
            <el-option v-for="u in memberCandidateUsers" :key="u.id" :label="`${u.displayName || u.username} (${u.username})`" :value="u.id" />
          </el-select>
        </template>

        <template v-else>
          <el-descriptions :column="2" border size="small" class="theme-summary">
            <el-descriptions-item :label="t('theme.id')">{{ form.id }}</el-descriptions-item>
            <el-descriptions-item :label="t('col.name')">{{ form.name }}</el-descriptions-item>
            <el-descriptions-item :label="t('col.status')">{{ form.enabled ? t('status.enabled') : t('status.disabled') }}</el-descriptions-item>
            <el-descriptions-item :label="t('theme.myRole')">{{ form.myRole === 'THEME_ADMIN' ? t('role.themeAdmin') : t('role.themeMember') }}</el-descriptions-item>
          </el-descriptions>
        </template>

        <template v-if="canEditThemeMembers">
          <el-divider content-position="left">{{ t('theme.memberSection') }}</el-divider>
          <div class="hint block-hint">{{ t('theme.memberHintShort') }}</div>
          <el-select v-model="form.memberIds" multiple filterable :placeholder="t('theme.selectMembers')" style="width: 100%">
            <el-option v-for="u in memberCandidateUsers" :key="u.id" :label="`${u.displayName || u.username} (${u.username})`" :value="u.id" />
          </el-select>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">{{ t('common.close') }}</el-button>
        <el-button v-if="canEditThemeConfig" type="primary" @click="saveTheme">{{ t('theme.saveConfig') }}</el-button>
        <el-button v-else-if="canEditThemeMembers" type="primary" @click="saveMembers">{{ t('theme.saveMembers') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { auth } from '../stores/auth'

const { t } = useI18n()
const themes = ref<any[]>([])
const regularUsers = ref<any[]>([])
const visible = ref(false)
const form = reactive<any>({
  id: undefined, name: '', description: '', enabled: true, myRole: null,
  themeAdminIds: [] as number[], memberIds: [] as number[]
})

const canEditThemeConfig = computed(() => auth.canManageThemes.value)
const canEditThemeMembers = computed(() => !auth.canManageThemes.value && form.myRole === 'THEME_ADMIN')

const memberCandidateUsers = computed(() => {
  const adminIds = new Set(form.themeAdminIds.map((id: number) => Number(id)))
  return regularUsers.value.filter((u: any) => !adminIds.has(Number(u.id)))
})

const adminCandidateUsers = computed(() => {
  const memberIds = new Set(form.memberIds.map((id: number) => Number(id)))
  return regularUsers.value.filter((u: any) => !memberIds.has(Number(u.id)))
})

watch(() => [...form.themeAdminIds], (adminIds) => {
  const next = form.memberIds.filter((id: number) => !adminIds.map(Number).includes(Number(id)))
  if (next.length !== form.memberIds.length) form.memberIds = next
})

watch(() => [...form.memberIds], (memberIds) => {
  const next = form.themeAdminIds.filter((id: number) => !memberIds.map(Number).includes(Number(id)))
  if (next.length !== form.themeAdminIds.length) form.themeAdminIds = next
})

const dialogTitle = computed(() => {
  if (!form.id) return t('theme.create')
  if (canEditThemeConfig.value) return t('theme.edit')
  if (canEditThemeMembers.value) return t('theme.manageMembers')
  return t('theme.detail')
})

function adminCount(row: any) {
  return (row.members || []).filter((m: any) => m.role === 'THEME_ADMIN').length
}

function memberCount(row: any) {
  return (row.members || []).filter((m: any) => m.role === 'MEMBER').length
}

function canEditTheme(row: any) {
  return auth.canManageThemes.value || row.myRole === 'THEME_ADMIN'
}

async function loadUsers() {
  if (auth.canManageThemes.value) {
    const allUsers = await http.get<any[]>('/admin/users')
    regularUsers.value = allUsers.filter((u: any) => u.role !== 'SUPER_ADMIN')
  }
}

async function load() {
  themes.value = await http.get('/admin/themes')
  await loadUsers()
}

async function loadRegularUsersForTheme(themeId: number) {
  regularUsers.value = await http.get(`/admin/themes/${themeId}/regular-users`)
}

function openCreate() {
  Object.assign(form, { id: undefined, name: '', description: '', enabled: true, myRole: null, themeAdminIds: [], memberIds: [] })
  visible.value = true
}

async function openDetail(row: any) {
  const members = row.members || []
  Object.assign(form, {
    id: row.id, name: row.name, description: row.description, enabled: row.enabled, myRole: row.myRole,
    themeAdminIds: members.filter((m: any) => m.role === 'THEME_ADMIN').map((m: any) => Number(m.userId)),
    memberIds: members.filter((m: any) => m.role === 'MEMBER').map((m: any) => Number(m.userId))
  })
  try {
    if (auth.canManageThemes.value) await loadUsers()
    else if (row.myRole === 'THEME_ADMIN') await loadRegularUsersForTheme(row.id)
    visible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || t('theme.openFailed'))
  }
}

async function saveTheme() {
  if (!form.name?.trim()) return ElMessage.warning(t('theme.needName'))
  if (form.themeAdminIds.length === 0) return ElMessage.warning(t('theme.needAdmin'))
  const adminIds = form.themeAdminIds.map((id: number) => Number(id)).filter((id: number) => !form.memberIds.map(Number).includes(id))
  const payload = { name: form.name.trim(), description: form.description, enabled: form.enabled, members: adminIds.map((userId: number) => ({ userId, role: 'THEME_ADMIN' })) }
  try {
    let themeId = form.id
    const memberIds = form.memberIds.map((id: number) => Number(id)).filter((id: number) => !adminIds.includes(id))
    if (form.id) { await http.put(`/admin/themes/${form.id}`, payload); themeId = form.id }
    else { const created = await http.post<any>('/admin/themes', payload); themeId = created.id }
    await http.put(`/admin/themes/${themeId}/members`, { userIds: memberIds })
    visible.value = false
    await load()
    ElMessage.success(t('common.saved'))
  } catch (e: any) {
    ElMessage.error(e?.message || t('common.operationFailed'))
  }
}

async function saveMembers() {
  try {
    const memberIds = form.memberIds.map((id: number) => Number(id)).filter((id: number) => !form.themeAdminIds.map(Number).includes(id))
    await http.put(`/admin/themes/${form.id}/members`, { userIds: memberIds })
    visible.value = false
    await load()
    ElMessage.success(t('theme.membersUpdated'))
  } catch (e: any) {
    ElMessage.error(e?.message || t('common.operationFailed'))
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.toolbar h2 { margin: 0; flex: 1; }
.hint { font-size: 12px; color: #737373; }
.block-hint { margin-bottom: 12px; padding: 10px 12px; background: #fafafa; border-radius: 6px; }
.theme-summary { margin-bottom: 8px; }
</style>

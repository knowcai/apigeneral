<template>
  <div>
    <div class="toolbar">
      <h2>{{ t('theme.title') }}</h2>
      <el-button v-if="auth.canManageThemes.value" type="primary" @click="openCreate">{{ t('theme.create') }}</el-button>
      <el-tag v-if="!auth.isSuperAdmin.value && !auth.canManageThemes.value" type="info">{{ t('theme.needApproval') }}</el-tag>
    </div>

    <el-table v-loading="loading" :data="themes" stripe @row-click="openDetail">
      <el-table-column prop="id" :label="t('theme.id')" width="80" />
      <el-table-column prop="name" :label="t('col.name')" />
      <el-table-column prop="enabled" :label="t('col.status')" width="100">
        <template #default="{ row }">
          <el-tooltip v-if="!row.enabled" :content="t('theme.disabledWarningBody')" placement="top">
            <el-tag type="danger" size="small">{{ t('status.disabled') }}</el-tag>
          </el-tooltip>
          <el-tag v-else type="success" size="small">{{ t('status.enabled') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('col.admins')" width="90">
        <template #default="{ row }">{{ adminCount(row) }}</template>
      </el-table-column>
      <el-table-column :label="t('col.members')" width="90">
        <template #default="{ row }">{{ memberCount(row) }}</template>
      </el-table-column>
      <el-table-column :label="t('theme.apiKeyColumn')" min-width="160">
        <template #default="{ row }">
          <template v-if="row.apiKeyPhase === 'PENDING'">
            <el-tag type="warning" size="small">{{ t('theme.apiKeyPending') }}</el-tag>
            <span v-if="row.apiKeyPrefix" class="key-prefix">{{ row.apiKeyPrefix }}…</span>
          </template>
          <template v-else-if="row.apiKeyPhase === 'ACTIVE'">
            <span class="key-prefix">{{ row.apiKeyPrefix }}…</span>
            <el-tag type="success" size="small" class="key-tag">{{ t('theme.apiKeyActive') }}</el-tag>
            <el-tag v-if="row.apiKeyPickupPending" type="warning" size="small" class="key-tag">{{ t('theme.apiKeyPickupPending') }}</el-tag>
          </template>
          <template v-else-if="row.apiKeyPhase === 'DISABLED'">
            <span class="key-prefix">{{ row.apiKeyPrefix }}…</span>
            <el-tag type="info" size="small" class="key-tag">{{ t('status.disabled') }}</el-tag>
          </template>
          <span v-else class="hint">{{ t('theme.apiKeyNone') }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('col.actions')" min-width="280" fixed="right">
        <template #default="{ row }">
          <div class="action-group">
            <el-button link @click.stop="openDetail(row)">{{ canEditTheme(row) ? t('common.manage') : t('common.view') }}</el-button>
            <template v-if="canManageKey(row)">
              <el-button v-if="row.apiKeyPhase === 'NONE'" link @click.stop="createApiKey(row)">{{ t('theme.apiKeyCreate') }}</el-button>
              <el-button v-else-if="row.apiKeyPhase === 'PENDING'" link type="warning" @click.stop="withdrawApiKeyApproval(row)">{{ t('theme.apiKeyWithdraw') }}</el-button>
              <template v-else-if="row.apiKeyPhase === 'ACTIVE'">
                <el-button v-if="row.apiKeyPickupPending" link type="warning" @click.stop="claimApiKey(row)">{{ t('theme.apiKeyClaim') }}</el-button>
                <el-button link @click.stop="rotateApiKey(row)">{{ t('theme.apiKeyRotateNew') }}</el-button>
                <el-button link type="danger" @click.stop="revokeApiKey(row)">{{ t('theme.apiKeyRevoke') }}</el-button>
              </template>
            </template>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="dialogTitle" width="720px" class="theme-dialog">
      <el-tabs v-model="activeTab" class="theme-tabs">
        <el-tab-pane :label="t('theme.tabBasic')" name="basic">
          <el-form :model="form" label-width="100px">
            <template v-if="canEditThemeConfig">
              <el-form-item v-if="form.id" :label="t('theme.id')">
                <el-input :model-value="String(form.id)" disabled />
              </el-form-item>
              <el-form-item :label="t('col.name')"><el-input v-model="form.name" :placeholder="t('theme.nameUnique')" /></el-form-item>
              <el-form-item :label="t('col.description')"><el-input v-model="form.description" type="textarea" /></el-form-item>
              <el-form-item :label="t('status.enabled')"><el-switch v-model="form.enabled" /></el-form-item>
              <el-alert
                v-if="!form.enabled"
                type="warning"
                :closable="false"
                show-icon
                class="disable-alert"
                :title="t('theme.disabledWarningTitle')"
                :description="t('theme.disabledWarningBody')"
              />
              <el-divider content-position="left">{{ t('theme.adminSection') }}</el-divider>
              <div class="hint block-hint">{{ t('theme.adminHint') }}</div>
              <el-select v-model="form.themeAdminIds" multiple filterable :placeholder="t('theme.selectAdmins')" style="width: 100%">
                <el-option v-for="u in adminCandidateUsers" :key="u.id" :label="`${u.displayName || u.username} (${u.username})`" :value="u.id" />
              </el-select>
            </template>
            <template v-else>
              <el-descriptions :column="2" border size="small" class="theme-summary">
                <el-descriptions-item :label="t('theme.id')">{{ form.id }}</el-descriptions-item>
                <el-descriptions-item :label="t('col.name')">{{ form.name }}</el-descriptions-item>
                <el-descriptions-item :label="t('col.status')">{{ form.enabled ? t('status.enabled') : t('status.disabled') }}</el-descriptions-item>
                <el-descriptions-item :label="t('theme.myRole')">{{ form.myRole === 'THEME_ADMIN' ? t('role.themeAdmin') : t('role.themeMember') }}</el-descriptions-item>
              </el-descriptions>
              <el-alert
                v-if="!form.enabled"
                type="warning"
                :closable="false"
                show-icon
                class="disable-alert"
                :title="t('theme.disabledWarningTitle')"
                :description="t('theme.disabledWarningBody')"
              />
            </template>
          </el-form>
        </el-tab-pane>

        <el-tab-pane v-if="canEditThemeConfig || canEditThemeMembers" :label="t('theme.tabMembers')" name="members">
          <div class="hint block-hint">{{ canEditThemeConfig ? t('theme.memberHint') : t('theme.memberHintShort') }}</div>
          <el-select v-model="form.memberIds" multiple filterable :placeholder="t('theme.selectMembers')" style="width: 100%">
            <el-option v-for="u in memberCandidateUsers" :key="u.id" :label="`${u.displayName || u.username} (${u.username})`" :value="u.id" />
          </el-select>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="visible = false">{{ t('common.close') }}</el-button>
        <el-button v-if="canEditThemeConfig" type="primary" @click="saveTheme">{{ t('theme.saveConfig') }}</el-button>
        <el-button v-else-if="canEditThemeMembers" type="primary" @click="saveMembers">{{ t('theme.saveMembers') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="keyVisible" :title="t('theme.apiKeyDialogTitle')" width="520px" :close-on-click-modal="false">
      <el-alert type="error" :closable="false" show-icon :title="t('theme.apiKeyWarningStrong')" class="key-alert" />
      <el-input class="key-box mono-input" :model-value="revealedKey" readonly>
        <template #append><el-button @click="copyKey">{{ t('common.copy') }}</el-button></template>
      </el-input>
      <template #footer>
        <el-button type="primary" @click="keyVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { auth } from '../stores/auth'
import { notifyApprovalResult } from '../utils/approval'

const { t } = useI18n()
const router = useRouter()
const themes = ref<any[]>([])
const loading = ref(false)
const regularUsers = ref<any[]>([])
const visible = ref(false)
const keyVisible = ref(false)
const revealedKey = ref('')
const activeTab = ref('basic')
const originalEnabled = ref(true)
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

function canManageKey(row: any) {
  return auth.canManageThemes.value || row.myRole === 'THEME_ADMIN'
}

async function loadUsers() {
  if (auth.canManageThemes.value) {
    const allUsers = await http.get<any[]>('/admin/users')
    regularUsers.value = allUsers.filter((u: any) => u.role !== 'SUPER_ADMIN')
  }
}

async function load() {
  loading.value = true
  try {
    themes.value = await http.get('/admin/themes')
    await loadUsers()
  } finally {
    loading.value = false
  }
}

async function loadRegularUsersForTheme(themeId: number) {
  regularUsers.value = await http.get(`/admin/themes/${themeId}/regular-users`)
}

function showRevealedKey(key: string) {
  revealedKey.value = key
  keyVisible.value = true
}

function copyKey() {
  navigator.clipboard.writeText(revealedKey.value)
  ElMessage.success(t('common.copied'))
}

async function createApiKey(row: any) {
  try {
    await ElMessageBox.confirm(t('theme.apiKeyCreateConfirm'), t('common.confirm'), { type: 'info' })
    const result = await http.post(`/admin/themes/${row.id}/api-key`, {
      name: `${row.name} Key`,
      status: 'ACTIVE'
    })
    if (notifyApprovalResult(result, t('theme.apiKeyApprovalSubmitted'), t, router)) {
      await load()
      return
    }
    if ((result as { apiKey?: string })?.apiKey) {
      showRevealedKey((result as { apiKey: string }).apiKey)
    }
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || t('common.operationFailed'))
  }
}

async function withdrawApiKeyApproval(row: any) {
  if (!row.apiKeyPendingRequestId) return
  try {
    await ElMessageBox.confirm(t('theme.apiKeyWithdrawConfirm'), t('common.confirm'), { type: 'warning' })
    await http.post(`/admin/approvals/requests/${row.apiKeyPendingRequestId}/withdraw`)
    ElMessage.success(t('theme.apiKeyWithdrawn'))
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || t('common.operationFailed'))
  }
}

async function claimApiKey(row: any) {
  try {
    await ElMessageBox.confirm(t('theme.apiKeyClaimConfirm'), t('common.confirm'), { type: 'info' })
    const result = await http.post<{ apiKey: string }>(`/admin/themes/${row.id}/api-key/claim`)
    if (result?.apiKey) {
      showRevealedKey(result.apiKey)
    }
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || t('common.operationFailed'))
  }
}

async function rotateApiKey(row: any) {
  try {
    await ElMessageBox.confirm(t('theme.apiKeyRotateConfirm'), t('common.confirm'), { type: 'warning' })
    const result = await http.post(`/admin/themes/${row.id}/api-key/rotate`)
    if (notifyApprovalResult(result, t('theme.apiKeyApprovalSubmitted'), t, router)) {
      await load()
      return
    }
    if ((result as { apiKey?: string })?.apiKey) {
      showRevealedKey((result as { apiKey: string }).apiKey)
    }
    ElMessage.success(t('theme.apiKeyRotated'))
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || t('common.operationFailed'))
  }
}

async function revokeApiKey(row: any) {
  try {
    await ElMessageBox.confirm(t('theme.apiKeyRevokeConfirm'), t('common.confirm'), { type: 'warning' })
    const result = await http.post(`/admin/themes/${row.id}/api-key/revoke`)
    if (notifyApprovalResult(result, t('theme.apiKeyApprovalSubmitted'), t, router)) {
      await load()
      return
    }
    ElMessage.success(t('theme.apiKeyRevoked'))
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || t('common.operationFailed'))
  }
}

function openCreate() {
  originalEnabled.value = true
  activeTab.value = 'basic'
  Object.assign(form, { id: undefined, name: '', description: '', enabled: true, myRole: null, themeAdminIds: [], memberIds: [] })
  visible.value = true
}

async function openDetail(row: any) {
  originalEnabled.value = row.enabled !== false
  activeTab.value = 'basic'
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
  if (form.id && originalEnabled.value && !form.enabled) {
    try {
      const impact = await http.get<{ apiCount: number }>(`/admin/themes/${form.id}/impact`)
      await ElMessageBox.confirm(
        t('theme.disableConfirmWithApis', { count: impact.apiCount ?? 0 }),
        t('common.confirm'),
        { type: 'warning' }
      )
    } catch {
      return
    }
  }
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
.disable-alert { margin-bottom: 16px; }
.theme-tabs { min-height: 240px; }
.key-prefix { font-family: ui-monospace, monospace; font-size: 12px; margin-right: 8px; }
.key-tag { vertical-align: middle; }
.mono { font-family: ui-monospace, monospace; }
.mono-input :deep(.el-input__inner) { font-family: ui-monospace, monospace; }
.action-group { display: flex; flex-wrap: wrap; gap: 4px 8px; align-items: center; }
.key-box { margin-top: 12px; }
.key-alert { margin-bottom: 12px; }
</style>

<template>
  <div>
    <div class="toolbar">
      <h2>主题管理</h2>
      <el-button v-if="auth.canManageThemes.value" type="primary" @click="openCreate">新建主题</el-button>
    </div>

    <el-table :data="themes" stripe @row-click="openDetail">
      <el-table-column prop="id" label="编码" width="80" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="enabled" label="状态" width="90">
        <template #default="{ row }">{{ row.enabled ? '启用' : '禁用' }}</template>
      </el-table-column>
      <el-table-column label="管理员" width="90">
        <template #default="{ row }">{{ adminCount(row) }}</template>
      </el-table-column>
      <el-table-column label="普通成员" width="90">
        <template #default="{ row }">{{ memberCount(row) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link @click.stop="openDetail(row)">{{ canEditTheme(row) ? '管理' : '查看' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="visible"
      :title="dialogTitle"
      width="720px"
    >
      <el-form :model="form" label-width="100px">
        <template v-if="canEditThemeConfig">
          <el-form-item v-if="form.id" label="编码">
            <el-input :model-value="String(form.id)" disabled />
          </el-form-item>
          <el-form-item label="名称"><el-input v-model="form.name" placeholder="主题名称不可重复" /></el-form-item>
          <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>

          <el-divider content-position="left">主题管理员</el-divider>
          <div class="hint block-hint">
            由超级管理员指定。主题管理员负责审批本主题下的变更，并可维护普通成员。同一用户不能同时担任两种角色。
          </div>
          <el-select
            v-model="form.themeAdminIds"
            multiple
            filterable
            placeholder="选择主题管理员"
            style="width: 100%"
          >
            <el-option
              v-for="u in adminCandidateUsers"
              :key="u.id"
              :label="`${u.displayName || u.username} (${u.username})`"
              :value="u.id"
            />
          </el-select>

          <el-divider content-position="left">主题普通成员</el-divider>
          <div class="hint block-hint">
            普通成员可新建/编辑 API 与连接串，变更需由主题管理员或超级管理员审批后生效。已是管理员的用户不可选为普通成员。
          </div>
          <el-select
            v-model="form.memberIds"
            multiple
            filterable
            placeholder="选择普通成员"
            style="width: 100%"
          >
            <el-option
              v-for="u in memberCandidateUsers"
              :key="u.id"
              :label="`${u.displayName || u.username} (${u.username})`"
              :value="u.id"
            />
          </el-select>
        </template>

        <template v-else>
          <el-descriptions :column="2" border size="small" class="theme-summary">
            <el-descriptions-item label="编码">{{ form.id }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ form.name }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ form.enabled ? '启用' : '禁用' }}</el-descriptions-item>
            <el-descriptions-item label="我的角色">{{ form.myRole === 'THEME_ADMIN' ? '主题管理员' : '普通成员' }}</el-descriptions-item>
          </el-descriptions>
        </template>

        <template v-if="canEditThemeMembers">
          <el-divider content-position="left">主题普通成员</el-divider>
          <div class="hint block-hint">从平台普通用户中选择，加入本主题后可进行 API/连接串操作（变更需审批）。</div>
          <el-select
            v-model="form.memberIds"
            multiple
            filterable
            placeholder="选择普通成员"
            style="width: 100%"
          >
            <el-option
              v-for="u in memberCandidateUsers"
              :key="u.id"
              :label="`${u.displayName || u.username} (${u.username})`"
              :value="u.id"
            />
          </el-select>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="canEditThemeConfig" type="primary" @click="saveTheme">保存主题配置</el-button>
        <el-button v-else-if="canEditThemeMembers" type="primary" @click="saveMembers">保存成员</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { auth } from '../stores/auth'

const themes = ref<any[]>([])
const regularUsers = ref<any[]>([])
const visible = ref(false)
const form = reactive<any>({
  id: undefined,
  name: '',
  description: '',
  enabled: true,
  myRole: null,
  themeAdminIds: [] as number[],
  memberIds: [] as number[]
})

const canEditThemeConfig = computed(() => auth.canManageThemes.value)

const canEditThemeMembers = computed(() => {
  if (auth.canManageThemes.value) return false
  return form.myRole === 'THEME_ADMIN'
})

const memberCandidateUsers = computed(() => {
  const adminIds = new Set(form.themeAdminIds.map((id: number) => Number(id)))
  return regularUsers.value.filter((u: any) => !adminIds.has(Number(u.id)))
})

const adminCandidateUsers = computed(() => {
  const memberIds = new Set(form.memberIds.map((id: number) => Number(id)))
  return regularUsers.value.filter((u: any) => !memberIds.has(Number(u.id)))
})

watch(
  () => [...form.themeAdminIds],
  (adminIds) => {
    const next = form.memberIds.filter((id: number) => !adminIds.map(Number).includes(Number(id)))
    if (next.length !== form.memberIds.length) {
      form.memberIds = next
    }
  }
)

watch(
  () => [...form.memberIds],
  (memberIds) => {
    const next = form.themeAdminIds.filter((id: number) => !memberIds.map(Number).includes(Number(id)))
    if (next.length !== form.themeAdminIds.length) {
      form.themeAdminIds = next
    }
  }
)
const dialogTitle = computed(() => {
  if (!form.id) return '新建主题'
  if (canEditThemeConfig.value) return '编辑主题'
  if (canEditThemeMembers.value) return '管理主题成员'
  return '主题详情'
})

function adminCount(row: any) {
  return (row.members || []).filter((m: any) => m.role === 'THEME_ADMIN').length
}

function memberCount(row: any) {
  return (row.members || []).filter((m: any) => m.role === 'MEMBER').length
}

function canEditTheme(row: any) {
  if (auth.canManageThemes.value) return true
  return row.myRole === 'THEME_ADMIN'
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
  Object.assign(form, {
    id: undefined,
    name: '',
    description: '',
    enabled: true,
    myRole: null,
    themeAdminIds: [],
    memberIds: []
  })
  visible.value = true
}

async function openDetail(row: any) {
  const members = row.members || []
  Object.assign(form, {
    id: row.id,
    name: row.name,
    description: row.description,
    enabled: row.enabled,
    myRole: row.myRole,
    themeAdminIds: members
      .filter((m: any) => m.role === 'THEME_ADMIN')
      .map((m: any) => Number(m.userId)),
    memberIds: members
      .filter((m: any) => m.role === 'MEMBER')
      .map((m: any) => Number(m.userId))
  })
  try {
    if (auth.canManageThemes.value) {
      await loadUsers()
    } else if (row.myRole === 'THEME_ADMIN') {
      await loadRegularUsersForTheme(row.id)
    }
    visible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '打开主题失败')
  }
}

async function saveTheme() {
  if (!form.name?.trim()) {
    ElMessage.warning('请填写主题名称')
    return
  }
  if (form.themeAdminIds.length === 0) {
    ElMessage.warning('请至少指定一名主题管理员')
    return
  }
  const adminIds = form.themeAdminIds
    .map((id: number) => Number(id))
    .filter((id: number) => !form.memberIds.map(Number).includes(id))
  const payload = {
    name: form.name.trim(),
    description: form.description,
    enabled: form.enabled,
    members: adminIds.map((userId: number) => ({ userId, role: 'THEME_ADMIN' }))
  }
  try {
    let themeId = form.id
    const memberIds = form.memberIds
      .map((id: number) => Number(id))
      .filter((id: number) => !adminIds.includes(id))
    if (form.id) {
      await http.put(`/admin/themes/${form.id}`, payload)
      themeId = form.id
    } else {
      const created = await http.post<any>('/admin/themes', payload)
      themeId = created.id
    }
    await http.put(`/admin/themes/${themeId}/members`, { userIds: memberIds })
    visible.value = false
    await load()
    ElMessage.success('保存成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function saveMembers() {
  try {
    const memberIds = form.memberIds
      .map((id: number) => Number(id))
      .filter((id: number) => !form.themeAdminIds.map(Number).includes(id))
    await http.put(`/admin/themes/${form.id}/members`, { userIds: memberIds })
    visible.value = false
    await load()
    ElMessage.success('成员已更新')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
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

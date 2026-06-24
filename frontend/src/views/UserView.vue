<template>
  <div>
    <div class="toolbar">
      <h2>用户管理</h2>
      <el-button type="primary" @click="openCreate">新建用户</el-button>
    </div>

    <p class="hint">此处仅创建平台登录账号（普通用户）。主题管理员与普通成员在「主题管理」中按主题分配。</p>

    <el-table :data="users" stripe>
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="displayName" label="显示名" />
      <el-table-column prop="role" label="类型" width="120">
        <template #default="{ row }">{{ roleLabel(row.role) }}</template>
      </el-table-column>
      <el-table-column prop="enabled" label="状态" width="90">
        <template #default="{ row }">{{ row.enabled ? '启用' : '禁用' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" :disabled="row.role === 'SUPER_ADMIN'" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑用户' : '新建用户'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名"><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="显示名"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item v-if="form.id && form.role === 'SUPER_ADMIN'" label="类型">
          <el-tag>超级管理员</el-tag>
        </el-form-item>
        <el-form-item v-else-if="form.id" label="类型">
          <el-tag type="info">普通用户</el-tag>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password :placeholder="form.id ? '留空不修改' : '至少 6 位'" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import type { UserRole } from '../stores/auth'

const users = ref<any[]>([])
const visible = ref(false)
const form = reactive<any>({ username: '', displayName: '', role: 'API_EDITOR', password: '', enabled: true })

function roleLabel(role: UserRole) {
  if (role === 'SUPER_ADMIN') return '超级管理员'
  return '普通用户'
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
  if (!form.username?.trim()) return ElMessage.warning('请填写用户名')
  if (!form.id && (!form.password || form.password.length < 6)) {
    return ElMessage.warning('密码至少 6 位')
  }
  const payload = {
    username: form.username.trim(),
    displayName: form.displayName?.trim() || form.username.trim(),
    enabled: form.enabled,
    password: form.password || undefined
  }
  try {
    if (form.id) {
      await http.put(`/admin/users/${form.id}`, payload)
    } else {
      await http.post('/admin/users', payload)
    }
    visible.value = false
    await load()
    ElMessage.success('保存成功')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(`删除用户 ${row.username}？`, '确认')
    await http.delete(`/admin/users/${row.id}`)
    await load()
    ElMessage.success('已删除')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.hint { font-size: 13px; color: #737373; margin: 0 0 16px; }
</style>

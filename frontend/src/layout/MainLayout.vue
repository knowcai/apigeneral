<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">{{ t('common.brandName') }}</div>
      <el-menu :default-active="route.path" router class="side-menu">
        <el-menu-item index="/themes">
          <el-icon><Folder /></el-icon>
          <span>{{ t('nav.themes') }}</span>
        </el-menu-item>
        <el-menu-item index="/datasources">
          <el-icon><Connection /></el-icon>
          <span>{{ t('nav.datasources') }}</span>
        </el-menu-item>
        <el-menu-item index="/apis">
          <el-icon><Document /></el-icon>
          <span>{{ t('nav.apis') }}</span>
        </el-menu-item>
        <el-menu-item index="/approvals" class="approvals-item">
          <el-icon><CircleCheck /></el-icon>
          <span>{{ t('nav.approvals') }}</span>
          <el-badge v-if="pendingCount > 0" :value="pendingCount" class="nav-badge" />
        </el-menu-item>
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>{{ t('nav.dashboard') }}</span>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon><DataLine /></el-icon>
          <span>{{ t('nav.logs') }}</span>
        </el-menu-item>
        <el-menu-item index="/policy">
          <el-icon><Setting /></el-icon>
          <span>{{ t('nav.policy') }}</span>
        </el-menu-item>
        <el-menu-item index="/audit">
          <el-icon><List /></el-icon>
          <span>{{ t('nav.audit') }}</span>
        </el-menu-item>
        <el-menu-item v-if="auth.canManageUsers.value" index="/users">
          <el-icon><User /></el-icon>
          <span>{{ t('nav.users') }}</span>
        </el-menu-item>
      </el-menu>
      <div class="user-bar">
        <div class="user-name">{{ auth.state.user?.displayName || auth.state.user?.username }}</div>
        <div class="user-role">{{ roleLabel }}</div>
        <el-select v-model="locale" size="small" class="locale-select" :aria-label="t('nav.language')" @change="onLocaleChange">
          <el-option :label="t('nav.localeZh')" value="zh-CN" />
          <el-option :label="t('nav.localeEn')" value="en-US" />
        </el-select>
        <el-button link class="logout" @click="logout">{{ t('nav.logout') }}</el-button>
      </div>
    </el-aside>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { auth } from '../stores/auth'
import { setLocale } from '../locales'
import http from '../api/http'

const route = useRoute()
const router = useRouter()
const { t, locale: i18nLocale } = useI18n()
const locale = ref(i18nLocale.value as string)
const pendingCount = ref(0)
let pendingTimer: ReturnType<typeof setInterval> | null = null

const roleLabel = computed(() => {
  const r = auth.state.user?.role
  if (r === 'SUPER_ADMIN') return t('role.superAdmin')
  if (r === 'API_VIEWER') return t('role.viewer')
  return t('role.user')
})

async function refreshPendingCount() {
  try {
    const data = await http.get<{ count: number }>('/admin/approvals/my-tasks/count')
    pendingCount.value = data?.count ?? 0
  } catch {
    pendingCount.value = 0
  }
}

function onLocaleChange(lang: string) {
  setLocale(lang as 'zh-CN' | 'en-US')
}

function logout() {
  auth.clear()
  router.replace('/login')
}

onMounted(() => {
  refreshPendingCount()
  pendingTimer = setInterval(refreshPendingCount, 30000)
})

onUnmounted(() => {
  if (pendingTimer) clearInterval(pendingTimer)
})
</script>

<style scoped>
.layout {
  height: 100vh;
  background: #fff;
}

.aside {
  background: #000;
  color: #fff;
  border-right: 1px solid #000;
  display: flex;
  flex-direction: column;
}

.logo {
  padding: 24px 20px;
  font-weight: 600;
  font-size: 14px;
  color: #fff;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  border-bottom: 1px solid #333;
}

.side-menu {
  border-right: none;
  background: #000;
  flex: 1;
}

.user-bar {
  padding: 16px 20px;
  border-top: 1px solid #333;
  font-size: 12px;
  color: #a3a3a3;
}

.user-name { color: #fff; font-weight: 600; margin-bottom: 4px; }
.locale-select { width: 100%; margin: 8px 0; }
.logout { color: #a3a3a3 !important; padding: 0; margin-top: 4px; }

.approvals-item :deep(.el-menu-item__content) {
  position: relative;
  width: 100%;
}

.nav-badge {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
}

:deep(.el-menu-item) {
  color: #a3a3a3;
  border-left: 3px solid transparent;
  transition: background-color 0.15s ease, color 0.15s ease;
}

:deep(.el-menu-item:hover) {
  background: #171717 !important;
  color: #fff;
}

:deep(.el-menu-item.is-active) {
  background: #171717 !important;
  color: #fff !important;
  border-left-color: #fff;
  font-weight: 600;
}

:deep(.el-menu-item.is-active .el-icon) {
  color: #fff;
}

.main {
  background: #fff;
  padding: 28px 40px 32px;
}
</style>

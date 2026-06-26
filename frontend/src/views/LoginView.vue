<template>
  <div class="login-page">
    <aside class="login-brand">
      <div class="brand-inner">
        <div class="brand-logo">{{ t('common.brandName') }}</div>
        <p class="brand-tagline">{{ t('login.tagline') }}</p>
        <ul class="brand-features">
          <li><el-icon><Connection /></el-icon><span>{{ t('login.feature1') }}</span></li>
          <li><el-icon><Document /></el-icon><span>{{ t('login.feature2') }}</span></li>
          <li><el-icon><Setting /></el-icon><span>{{ t('login.feature3') }}</span></li>
        </ul>
      </div>
      <div class="brand-footer">{{ t('login.console') }}</div>
    </aside>

    <main class="login-main">
      <div class="login-panel">
        <header class="panel-header">
          <h1>{{ t('login.welcome') }}</h1>
          <p>{{ t('login.subtitle') }}</p>
        </header>

        <el-form class="login-form" :model="form" size="large" @submit.prevent="submit">
          <el-form-item :label="t('login.username')">
            <el-input v-model="form.username" autocomplete="username" :prefix-icon="User" />
          </el-form-item>
          <el-form-item :label="t('login.password')">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              autocomplete="current-password"
              :prefix-icon="Lock"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button type="primary" native-type="submit" class="login-btn" :loading="loading">
            {{ t('login.submit') }}
          </el-button>
        </el-form>

        <div class="login-hint">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ t('login.hint') }}</span>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { User, Lock, InfoFilled } from '@element-plus/icons-vue'
import http from '../api/http'
import { auth, type UserInfo } from '../stores/auth'

const { t } = useI18n()
const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function submit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning(t('login.needCredentials'))
    return
  }
  loading.value = true
  try {
    const data = await http.post<{ token: string; user: UserInfo }>('/admin/auth/login', form)
    auth.setSession(data.token, data.user)
    ElMessage.success(t('login.success'))
    router.replace('/themes')
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height: 100vh; display: flex; background: var(--bw-white); }
.login-brand {
  flex: 0 0 42%; max-width: 520px; min-height: 100vh; background: #000; color: #fff;
  display: flex; flex-direction: column; justify-content: space-between; padding: 48px 40px; position: relative; overflow: hidden;
}
.login-brand::before {
  content: ''; position: absolute; inset: 0;
  background: radial-gradient(circle at 20% 80%, rgba(255,255,255,0.06) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(255,255,255,0.04) 0%, transparent 40%);
  pointer-events: none;
}
.brand-inner { position: relative; z-index: 1; }
.brand-logo { font-weight: 600; font-size: 13px; letter-spacing: 0.12em; text-transform: uppercase; color: #fff; padding-bottom: 24px; border-bottom: 1px solid #333; margin-bottom: 32px; }
.brand-tagline { margin: 0 0 40px; font-size: 22px; font-weight: 600; line-height: 1.5; letter-spacing: -0.02em; color: #fff; }
.brand-features { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 20px; }
.brand-features li { display: flex; align-items: flex-start; gap: 12px; font-size: 14px; line-height: 1.5; color: #a3a3a3; }
.brand-features .el-icon { flex-shrink: 0; margin-top: 2px; font-size: 16px; color: #fff; }
.brand-footer { position: relative; z-index: 1; font-size: 11px; letter-spacing: 0.14em; text-transform: uppercase; color: #525252; }
.login-main { flex: 1; display: flex; align-items: center; justify-content: center; padding: 40px 24px; background: linear-gradient(135deg, var(--bw-gray-100) 0%, var(--bw-white) 50%, var(--bw-gray-100) 100%); }
.login-panel { width: 100%; max-width: 400px; padding: 48px 40px; background: var(--bw-white); border: 1px solid var(--bw-gray-200); box-shadow: 0 4px 24px rgba(0,0,0,0.06); }
.panel-header { margin-bottom: 36px; }
.panel-header h1 { margin: 0 0 8px; font-size: 28px; font-weight: 600; letter-spacing: -0.03em; color: var(--bw-black); }
.panel-header p { margin: 0; font-size: 14px; color: var(--bw-gray-500); }
.login-form :deep(.el-form-item) { margin-bottom: 20px; }
.login-form :deep(.el-input__wrapper) { padding: 4px 12px; border-radius: 0; }
.login-btn { width: 100%; height: 44px; margin-top: 8px; font-size: 15px; font-weight: 600; letter-spacing: 0.04em; border-radius: 0; }
.login-hint { display: flex; align-items: flex-start; gap: 8px; margin-top: 28px; padding: 12px 14px; background: var(--bw-gray-100); border: 1px solid var(--bw-gray-200); font-size: 12px; line-height: 1.6; color: var(--bw-gray-500); }
.login-hint .el-icon { flex-shrink: 0; margin-top: 2px; font-size: 14px; color: var(--bw-gray-700); }
@media (max-width: 768px) {
  .login-page { flex-direction: column; }
  .login-brand { flex: none; max-width: none; min-height: auto; padding: 32px 24px; }
  .brand-tagline { font-size: 18px; margin-bottom: 24px; }
  .brand-features, .brand-footer { display: none; }
  .login-main { padding: 24px 16px 40px; }
  .login-panel { padding: 32px 24px; box-shadow: none; }
}
</style>

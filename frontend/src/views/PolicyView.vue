<template>
  <div>
    <div class="toolbar">
      <h2>{{ t('policy.title') }}</h2>
      <el-button v-if="auth.canEditPolicy.value" type="primary" :loading="saving" @click="save">{{ t('policy.save') }}</el-button>
      <el-tag v-else type="info">{{ t('policy.readonlyHint') }}</el-tag>
    </div>

    <el-form v-if="loaded" :model="form" label-width="160px" class="policy-form" :disabled="!auth.canEditPolicy.value">
      <el-divider content-position="left">{{ t('policy.qpsSection') }}</el-divider>

      <el-form-item :label="t('policy.globalQps')">
        <el-switch v-model="form.globalQpsEnabled" />
        <el-input-number v-model="form.globalQps" :min="1" :max="100000" :disabled="!form.globalQpsEnabled" class="num" />
        <span class="hint">{{ t('policy.globalQpsHint') }}</span>
      </el-form-item>

      <el-form-item :label="t('policy.ipQps')">
        <el-switch v-model="form.ipQpsEnabled" />
        <el-input-number v-model="form.ipQps" :min="1" :max="10000" :disabled="!form.ipQpsEnabled" class="num" />
        <span class="hint">{{ t('policy.ipQpsHint') }}</span>
      </el-form-item>

      <el-form-item :label="t('policy.apiQps')">
        <el-switch v-model="form.apiQpsEnabled" />
        <el-input-number v-model="form.apiQps" :min="1" :max="10000" :disabled="!form.apiQpsEnabled" class="num" />
        <span class="hint">{{ t('policy.apiQpsHint') }}</span>
      </el-form-item>

      <el-divider content-position="left">{{ t('policy.circuitSection') }}</el-divider>
      <p class="section-desc">{{ t('policy.circuitDesc') }}</p>

      <el-form-item :label="t('policy.circuitEnabled')">
        <el-switch v-model="form.circuitEnabled" />
      </el-form-item>
      <el-form-item :label="t('policy.failureRate')">
        <el-input-number v-model="form.circuitFailureRate" :min="1" :max="100" :disabled="!form.circuitEnabled" />
        <span class="hint">{{ t('policy.failureRateHint') }}</span>
      </el-form-item>
      <el-form-item :label="t('policy.minCalls')">
        <el-input-number v-model="form.circuitMinCalls" :min="5" :max="1000" :disabled="!form.circuitEnabled" />
        <span class="hint">{{ t('policy.minCallsHint') }}</span>
      </el-form-item>
      <el-form-item :label="t('policy.waitSec')">
        <el-input-number v-model="form.circuitWaitSec" :min="5" :max="600" :disabled="!form.circuitEnabled" />
        <span class="hint">{{ t('policy.waitSecHint') }}</span>
      </el-form-item>
      <el-form-item :label="t('policy.fallback')">
        <el-input v-model="form.circuitFallback" type="textarea" :rows="4" :disabled="!form.circuitEnabled" />
        <span class="hint">{{ t('policy.fallbackHint') }}</span>
      </el-form-item>

      <el-divider content-position="left">{{ t('policy.retrySection') }}</el-divider>

      <el-form-item :label="t('policy.retryEnabled')">
        <el-switch v-model="form.retryEnabled" />
      </el-form-item>
      <el-form-item :label="t('policy.retryMax')">
        <el-input-number v-model="form.retryMaxAttempts" :min="0" :max="5" :disabled="!form.retryEnabled" />
        <span class="hint">{{ t('policy.retryMaxHint') }}</span>
      </el-form-item>
      <el-form-item :label="t('policy.retryInterval')">
        <el-input-number v-model="form.retryIntervalMs" :min="100" :max="10000" :step="100" :disabled="!form.retryEnabled" />
        <span class="hint">{{ t('policy.retryIntervalHint') }}</span>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { auth } from '../stores/auth'

const { t } = useI18n()
const loaded = ref(false)
const saving = ref(false)
const form = reactive({
  globalQpsEnabled: true, globalQps: 1000, ipQpsEnabled: true, ipQps: 100,
  apiQpsEnabled: true, apiQps: 50, circuitEnabled: true, circuitFailureRate: 50,
  circuitMinCalls: 20, circuitWaitSec: 30,
  circuitFallback: '{"code":503,"message":"API circuit open","data":null}',
  retryEnabled: true, retryMaxAttempts: 2, retryIntervalMs: 500
})

async function load() {
  const data = await http.get<typeof form>('/admin/gateway-policy')
  Object.assign(form, data)
  loaded.value = true
}

async function save() {
  try { JSON.parse(form.circuitFallback) } catch { return ElMessage.error(t('policy.invalidJson')) }
  saving.value = true
  try {
    await http.put('/admin/gateway-policy', form)
    ElMessage.success(t('common.saved'))
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.policy-form { max-width: 720px; }
.num { margin-left: 16px; }
.section-desc { font-size: 13px; color: #525252; margin: -8px 0 16px 160px; line-height: 1.5; max-width: 520px; }
.hint { display: block; font-size: 12px; color: #737373; margin-top: 6px; line-height: 1.4; }
:deep(.el-form-item__content) { flex-wrap: wrap; }
</style>

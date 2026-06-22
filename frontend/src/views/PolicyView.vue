<template>
  <div>
    <div class="toolbar">
      <h2>API 限流 / 熔断 / 重试</h2>
      <el-button v-if="auth.canEditPolicy.value" type="primary" :loading="saving" @click="save">保存配置</el-button>
      <el-tag v-else type="info">只读（仅超级管理员可修改）</el-tag>
    </div>

    <el-form v-if="loaded" :model="form" label-width="160px" class="policy-form" :disabled="!auth.canEditPolicy.value">
      <el-divider content-position="left">QPS 限流</el-divider>

      <el-form-item label="全局 QPS 限流">
        <el-switch v-model="form.globalQpsEnabled" />
        <el-input-number v-model="form.globalQps" :min="1" :max="100000" :disabled="!form.globalQpsEnabled" class="num" />
        <span class="hint">全平台每秒最大请求数，默认 1000</span>
      </el-form-item>

      <el-form-item label="单 IP QPS 限流">
        <el-switch v-model="form.ipQpsEnabled" />
        <el-input-number v-model="form.ipQps" :min="1" :max="10000" :disabled="!form.ipQpsEnabled" class="num" />
        <span class="hint">同一 IP 每秒最大请求数，默认 100</span>
      </el-form-item>

      <el-form-item label="单接口 QPS 限流">
        <el-switch v-model="form.apiQpsEnabled" />
        <el-input-number v-model="form.apiQps" :min="1" :max="10000" :disabled="!form.apiQpsEnabled" class="num" />
        <span class="hint">每个 API 每秒最大请求数，默认 50；可在 API 版本里单独覆盖</span>
      </el-form-item>

      <el-divider content-position="left">单 API 熔断</el-divider>

      <p class="section-desc">按每个 API（apiCode）独立统计与熔断，互不影响。失败率基于最近 1 分钟内的 SQL 执行结果滚动计算。</p>

      <el-form-item label="启用 API 熔断">
        <el-switch v-model="form.circuitEnabled" />
      </el-form-item>
      <el-form-item label="失败率阈值(%)">
        <el-input-number v-model="form.circuitFailureRate" :min="1" :max="100" :disabled="!form.circuitEnabled" />
        <span class="hint">该 API 在最近 1 分钟窗口内失败率达到该比例即触发熔断，默认 50%</span>
      </el-form-item>
      <el-form-item label="最小调用次数">
        <el-input-number v-model="form.circuitMinCalls" :min="5" :max="1000" :disabled="!form.circuitEnabled" />
        <span class="hint">该 API 在 1 分钟窗口内至少达到该次 SQL 执行后才计算失败率，默认 20</span>
      </el-form-item>
      <el-form-item label="熔断等待(秒)">
        <el-input-number v-model="form.circuitWaitSec" :min="5" :max="600" :disabled="!form.circuitEnabled" />
        <span class="hint">该 API 熔断后多久进入半开试探，默认 30 秒</span>
      </el-form-item>
      <el-form-item label="API 熔断返回内容">
        <el-input
          v-model="form.circuitFallback"
          type="textarea"
          :rows="4"
          :disabled="!form.circuitEnabled"
          placeholder='{"code":503,"message":"该 API 已熔断，请稍后重试","data":null}'
        />
        <span class="hint">该 API 熔断时直接返回的 JSON 响应体，可自定义 message 和 data</span>
      </el-form-item>

      <el-divider content-position="left">重试</el-divider>

      <el-form-item label="启用重试">
        <el-switch v-model="form.retryEnabled" />
      </el-form-item>
      <el-form-item label="最大重试次数">
        <el-input-number v-model="form.retryMaxAttempts" :min="0" :max="5" :disabled="!form.retryEnabled" />
        <span class="hint">不含首次请求，默认 2 次（共 3 次尝试）</span>
      </el-form-item>
      <el-form-item label="重试间隔(ms)">
        <el-input-number v-model="form.retryIntervalMs" :min="100" :max="10000" :step="100" :disabled="!form.retryEnabled" />
        <span class="hint">仅对连接超时等可重试错误生效，默认 500ms</span>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { auth } from '../stores/auth'

const loaded = ref(false)
const saving = ref(false)
const form = reactive({
  globalQpsEnabled: true,
  globalQps: 1000,
  ipQpsEnabled: true,
  ipQps: 100,
  apiQpsEnabled: true,
  apiQps: 50,
  circuitEnabled: true,
  circuitFailureRate: 50,
  circuitMinCalls: 20,
  circuitWaitSec: 30,
  circuitFallback: '{"code":503,"message":"该 API 已熔断，请稍后重试","data":null}',
  retryEnabled: true,
  retryMaxAttempts: 2,
  retryIntervalMs: 500
})

async function load() {
  const data = await http.get<typeof form>('/admin/gateway-policy')
  Object.assign(form, data)
  loaded.value = true
}

async function save() {
  try {
    JSON.parse(form.circuitFallback)
  } catch {
    return ElMessage.error('熔断返回内容必须是合法 JSON')
  }
  saving.value = true
  try {
    await http.put('/admin/gateway-policy', form)
    ElMessage.success('保存成功')
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

<template>
  <div>
    <div class="toolbar">
      <h2>{{ t('log.title') }}</h2>
      <el-input v-model="apiCode" :placeholder="t('log.filterApi')" style="width: 180px" clearable @clear="reload" />
      <el-select v-model="status" :placeholder="t('log.filterStatus')" clearable style="width: 140px" @clear="reload">
        <el-option :label="t('log.allStatus')" value="" />
        <el-option v-for="s in statusOptions" :key="s" :label="statusLabel(s)" :value="s" />
      </el-select>
      <el-input v-model="consumerName" :placeholder="t('log.filterConsumer')" style="width: 160px" clearable @clear="reload" />
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        :start-placeholder="t('log.timeRange')"
        value-format="YYYY-MM-DDTHH:mm:ss"
        style="width: 360px"
      />
      <el-button type="primary" @click="reload">{{ t('common.query') }}</el-button>
      <el-button @click="exportCsv">{{ t('log.export') }}</el-button>
    </div>

    <el-table :data="logs" stripe>
      <el-table-column :label="t('col.time')" width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="apiCode" :label="t('col.code')" width="140" />
      <el-table-column prop="apiVersion" :label="t('col.version')" width="70" />
      <el-table-column prop="consumerName" :label="t('col.consumer')" width="120" />
      <el-table-column prop="clientIp" :label="t('col.ip')" width="130" />
      <el-table-column :label="t('col.status')" width="110">
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column prop="responseRows" :label="t('col.rows')" width="80" />
      <el-table-column :label="t('col.durationSec')" width="90">
        <template #default="{ row }">{{ formatDurationSec(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column prop="errorMessage" :label="t('col.error')" min-width="160" />
    </el-table>

    <el-pagination class="pager" background layout="prev, pager, next" :total="total" :page-size="size" v-model:current-page="page" @current-change="load" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const { t } = useI18n()
const logs = ref<any[]>([])
const apiCode = ref('')
const status = ref('')
const consumerName = ref('')
const timeRange = ref<[string, string] | null>(null)
const page = ref(1)
const size = 20
const total = ref(0)
const statusOptions = ['SUCCESS', 'RATE_LIMITED', 'CIRCUIT_OPEN', 'FORBIDDEN', 'ERROR']

function statusLabel(status: string) {
  const key = `log.status.${status}`
  const translated = t(key)
  return translated === key ? status : translated
}

function queryParams(forExport = false) {
  const params: Record<string, string | number | undefined> = {
    apiCode: apiCode.value || undefined,
    status: status.value || undefined,
    consumerName: consumerName.value || undefined,
    from: timeRange.value?.[0],
    to: timeRange.value?.[1]
  }
  if (!forExport) {
    params.page = page.value - 1
    params.size = size
  }
  return params
}

function formatTime(value: string | null | undefined) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value.replace('T', ' ').replace(/\.\d+/, '').slice(0, 19)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatDurationSec(ms: number | null | undefined) {
  if (ms == null) return ''
  return String(Math.round(ms / 1000))
}

async function load() {
  try {
    const data = await http.get<{ content: any[]; totalElements: number }>('/admin/logs', { params: queryParams() })
    logs.value = data.content
    total.value = data.totalElements
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

function reload() {
  page.value = 1
  load()
}

async function exportCsv() {
  try {
    const qs = new URLSearchParams()
    const params = queryParams(true)
    Object.entries(params).forEach(([k, v]) => {
      if (v != null && v !== '') qs.set(k, String(v))
    })
    await http.download(`/admin/logs/export?${qs}`, 'access-logs.csv')
    ElMessage.success(t('log.exportOk'))
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>

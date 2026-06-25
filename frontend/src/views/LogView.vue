<template>
  <div>
    <div class="toolbar">
      <h2>{{ t('log.title') }}</h2>
      <el-input v-model="apiCode" :placeholder="t('log.filterApi')" style="width: 240px" clearable @clear="load" />
      <el-button @click="load">{{ t('common.query') }}</el-button>
    </div>

    <el-table :data="logs" stripe>
      <el-table-column :label="t('col.time')" width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="apiCode" label="API" width="140" />
      <el-table-column prop="apiVersion" :label="t('col.version')" width="70" />
      <el-table-column prop="consumerName" :label="t('col.consumer')" width="120" />
      <el-table-column prop="clientIp" :label="t('col.ip')" width="130" />
      <el-table-column prop="responseMode" :label="t('col.mode')" width="90" />
      <el-table-column prop="responseRows" :label="t('col.rows')" width="80" />
      <el-table-column prop="responseBytes" :label="t('col.bytes')" width="90" />
      <el-table-column :label="t('col.durationSec')" width="90">
        <template #default="{ row }">{{ formatDurationSec(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column prop="status" :label="t('col.status')" width="90" />
      <el-table-column prop="errorMessage" :label="t('col.error')" min-width="160" />
    </el-table>

    <el-pagination class="pager" background layout="prev, pager, next" :total="total" :page-size="size" v-model:current-page="page" @current-change="load" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import http from '../api/http'

const { t } = useI18n()
const logs = ref<any[]>([])
const apiCode = ref('')
const page = ref(1)
const size = 20
const total = ref(0)

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
  const data = await http.get<{ content: any[]; totalElements: number }>('/admin/logs', {
    params: { apiCode: apiCode.value || undefined, page: page.value - 1, size }
  })
  logs.value = data.content
  total.value = data.totalElements
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>

<template>
  <div>
    <div class="toolbar">
      <h2>{{ t('dashboard.title') }}</h2>
      <el-input-number v-model="hours" :min="1" :max="168" />
      <el-button @click="load">{{ t('dashboard.refresh') }}</el-button>
      <el-tag type="info" size="small">{{ t('dashboard.runtimeAuto') }}</el-tag>
    </div>

    <el-card shadow="never" class="section pool-card" :header="t('dashboard.globalPool')">
      <div v-if="globalPool" class="pool-summary">
        <div class="pool-numbers">
          <span class="pool-big">{{ globalPool.inUse }} / {{ globalPool.max }}</span>
          <span class="pool-sub">{{ t('dashboard.inUse') }}</span>
        </div>
        <el-progress
          :percentage="globalPool.usagePercent ?? 0"
          :status="poolStatus(globalPool.usagePercent)"
          :stroke-width="14"
          class="pool-bar"
        />
        <div class="pool-hint">
          {{ t('dashboard.globalPoolHint') }}
          <span v-if="globalPoolConfig">（{{ t('dashboard.configMax') }}: {{ globalPoolConfig.globalMaxConnections }}）</span>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="section" :header="t('dashboard.datasourcePools')">
      <el-empty v-if="!datasourcePools.length" :description="t('dashboard.noPoolYet')" />
      <el-table v-else :data="datasourcePools" stripe size="small">
        <el-table-column prop="datasourceName" :label="t('dashboard.dsName')" min-width="140" />
        <el-table-column prop="datasourceType" :label="t('dashboard.dsType')" width="110" />
        <el-table-column :label="t('dashboard.active')" width="100">
          <template #default="{ row }">{{ row.active }} / {{ row.max }}</template>
        </el-table-column>
        <el-table-column prop="idle" :label="t('dashboard.idle')" width="80" />
        <el-table-column prop="total" :label="t('dashboard.total')" width="80" />
        <el-table-column :label="t('dashboard.usage')" min-width="160">
          <template #default="{ row }">
            <el-progress
              :percentage="row.usagePercent ?? 0"
              :status="poolStatus(row.usagePercent)"
              :stroke-width="10"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-row :gutter="16" class="cards">
      <el-col :span="8">
        <el-card shadow="never">
          <div class="metric-label">{{ t('dashboard.totalCalls') }}</div>
          <div class="metric-value">{{ stats.totalCalls ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="metric-label">{{ t('dashboard.successRate') }}</div>
          <div class="metric-value">{{ stats.successRate ?? 0 }}%</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="metric-label">{{ t('dashboard.avgDuration') }}</div>
          <div class="metric-value">{{ stats.avgDurationMs ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section">
      <el-col :span="12">
        <el-card shadow="never" :header="t('dashboard.topApis')">
          <el-table :data="stats.topApis || []" stripe size="small">
            <el-table-column prop="apiCode" label="API" />
            <el-table-column prop="count" label="Calls" width="100" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" :header="t('dashboard.byStatus')">
          <el-table :data="statusRows" stripe size="small">
            <el-table-column prop="status" label="Status" />
            <el-table-column prop="count" label="Count" width="100" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="section" :header="t('dashboard.hourly')">
      <el-table :data="stats.hourly || []" stripe size="small" max-height="320">
        <el-table-column prop="hour" label="Hour" min-width="200" />
        <el-table-column prop="count" label="Calls" width="120" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import http from '../api/http'

const { t } = useI18n()
const hours = ref(24)
const stats = ref<any>({})
const globalPool = ref<any>(null)
const globalPoolConfig = ref<any>(null)
const datasourcePools = ref<any[]>([])
let runtimeTimer: ReturnType<typeof setInterval> | null = null

const statusRows = computed(() => {
  const byStatus = stats.value.byStatus || {}
  return Object.entries(byStatus).map(([status, count]) => ({ status, count }))
})

function poolStatus(percent?: number) {
  if (percent == null) return undefined
  if (percent >= 90) return 'exception'
  if (percent >= 70) return 'warning'
  return undefined
}

function applyRuntime(data: any) {
  globalPool.value = data.globalPool ?? null
  globalPoolConfig.value = data.globalPoolConfig ?? null
  datasourcePools.value = data.datasourcePools ?? []
}

async function loadRuntime() {
  const data = await http.get('/admin/monitoring/runtime')
  applyRuntime(data)
}

async function load() {
  const data = await http.get('/admin/monitoring/dashboard', { params: { hours: hours.value } })
  stats.value = data
  applyRuntime(data)
}

onMounted(async () => {
  await load()
  runtimeTimer = setInterval(loadRuntime, 10000)
})

onUnmounted(() => {
  if (runtimeTimer) clearInterval(runtimeTimer)
})
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.cards { margin-bottom: 16px; }
.section { margin-bottom: 16px; }
.metric-label { font-size: 12px; color: #737373; }
.metric-value { font-size: 28px; font-weight: 600; margin-top: 8px; }
.pool-card { border: 1px solid #e5e5e5; }
.pool-summary { max-width: 640px; }
.pool-numbers { margin-bottom: 12px; }
.pool-big { font-size: 32px; font-weight: 700; margin-right: 8px; }
.pool-sub { font-size: 13px; color: #737373; }
.pool-bar { margin-bottom: 8px; }
.pool-hint { font-size: 12px; color: #737373; line-height: 1.5; }
</style>

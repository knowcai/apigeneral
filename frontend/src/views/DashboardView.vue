<template>
  <div v-loading="loading">
    <div class="toolbar">
      <h2>{{ t('dashboard.title') }}</h2>
      <span class="hours-label">{{ t('dashboard.hours') }}</span>
      <el-input-number v-model="hours" :min="1" :max="168" />
      <el-button @click="load">{{ t('dashboard.refresh') }}</el-button>
      <el-tag v-if="stats.scoped" type="info" size="small">{{ t('dashboard.scopedHint') }}</el-tag>
      <el-tag v-else type="info" size="small">{{ t('dashboard.runtimeAuto') }}</el-tag>
    </div>

    <el-card v-if="globalPool" shadow="never" class="section pool-card" :header="t('dashboard.globalPool')">
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
      <el-col :span="6">
        <el-card shadow="never" class="metric-card">
          <div class="metric-label">{{ t('dashboard.totalCalls') }}</div>
          <div class="metric-value">{{ stats.totalCalls ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="metric-card">
          <div class="metric-label">{{ t('dashboard.successRate') }}</div>
          <div class="metric-value">{{ stats.successRate ?? 0 }}%</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="metric-card">
          <div class="metric-label">{{ t('dashboard.avgDuration') }}</div>
          <div class="metric-value">{{ stats.avgDurationMs ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="metric-card metric-warn">
          <div class="metric-label">{{ t('dashboard.disabledThemeCalls') }}</div>
          <div class="metric-value warn">{{ stats.disabledThemeCalls ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="cards">
      <el-col :span="6">
        <el-card shadow="never" class="metric-card metric-warn">
          <div class="metric-label">{{ t('dashboard.rateLimitedCalls') }}</div>
          <div class="metric-value warn">{{ stats.rateLimitedCalls ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="metric-card metric-warn">
          <div class="metric-label">{{ t('dashboard.circuitOpenCalls') }}</div>
          <div class="metric-value warn">{{ stats.circuitOpenCalls ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section">
      <el-col :span="12">
        <el-card shadow="never" :header="t('dashboard.topRateLimited')">
          <el-empty v-if="!(stats.topRateLimitedApis || []).length" :description="t('common.noData')" />
          <el-table v-else :data="stats.topRateLimitedApis || []" stripe size="small">
            <el-table-column prop="apiCode" :label="t('dashboard.apiCodeCol')" />
            <el-table-column prop="count" :label="t('dashboard.callsCol')" width="100" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" :header="t('dashboard.circuitStates')">
          <el-empty v-if="!(stats.circuitStates || []).length" :description="t('common.noData')" />
          <el-table v-else :data="stats.circuitStates || []" stripe size="small" max-height="240">
            <el-table-column prop="apiCode" :label="t('dashboard.apiCodeCol')" min-width="120" />
            <el-table-column prop="status" :label="t('dashboard.circuitStatus')" width="100">
              <template #default="{ row }">
                <el-tag :type="circuitTagType(row.status)" size="small">{{ circuitLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('dashboard.failureRate')" width="90">
              <template #default="{ row }">{{ row.failureRatePercent }}%</template>
            </el-table-column>
            <el-table-column prop="windowFailures" :label="t('dashboard.windowFailures')" width="80" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="cards secondary-cards">
      <el-col :span="12">
        <el-card shadow="never" :header="t('dashboard.topApis')">
          <el-empty v-if="!(stats.topApis || []).length" :description="t('common.noData')" />
          <el-table v-else :data="stats.topApis || []" stripe size="small">
            <el-table-column prop="apiCode" :label="t('dashboard.apiCodeCol')" />
            <el-table-column prop="count" :label="t('dashboard.callsCol')" width="100" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" :header="t('dashboard.byStatus')">
          <el-empty v-if="!statusRows.length" :description="t('common.noData')" />
          <el-table v-else :data="statusRows" stripe size="small">
            <el-table-column prop="status" :label="t('dashboard.statusCol')" />
            <el-table-column prop="count" :label="t('dashboard.countCol')" width="100" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="section" :header="t('dashboard.apiKeyUsage')">
      <el-empty v-if="!(stats.apiKeyUsage || []).length" :description="t('common.noData')" />
      <el-table v-else :data="stats.apiKeyUsage || []" stripe size="small" max-height="320">
        <el-table-column prop="apiCode" :label="t('dashboard.apiCodeCol')" min-width="140" />
        <el-table-column prop="consumerName" :label="t('dashboard.keyNameCol')" min-width="120" />
        <el-table-column prop="consumerId" :label="t('dashboard.keyIdCol')" width="80" />
        <el-table-column prop="count" :label="t('dashboard.callsCol')" width="100" />
      </el-table>
    </el-card>

    <el-card shadow="never" class="section" :header="t('dashboard.hourly')">
      <div v-if="hourlyBars.length" class="trend-chart" role="img" :aria-label="t('dashboard.hourly')">
        <div v-for="row in hourlyBars" :key="row.hour" class="trend-bar-col" :title="`${row.hour}: ${row.count}`">
          <div class="trend-bar-track">
            <div class="trend-bar-fill" :style="{ height: row.pct + '%' }" />
          </div>
          <span class="trend-bar-label">{{ row.shortHour }}</span>
        </div>
      </div>
      <el-empty v-else :description="t('common.noData')" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import http from '../api/http'

const { t } = useI18n()
const hours = ref(24)
const loading = ref(false)
const stats = ref<any>({})
const globalPool = ref<any>(null)
const globalPoolConfig = ref<any>(null)
const datasourcePools = ref<any[]>([])
let runtimeTimer: ReturnType<typeof setInterval> | null = null

const statusRows = computed(() => {
  const byStatus = stats.value.byStatus || {}
  return Object.entries(byStatus).map(([status, count]) => ({
    status: logStatusLabel(status),
    count
  }))
})

function logStatusLabel(status: string) {
  const key = `log.status.${status}`
  const translated = t(key)
  return translated === key ? status : translated
}

function circuitLabel(status?: string) {
  if (status === 'OPEN') return t('dashboard.circuitOpen')
  if (status === 'HALF_OPEN') return t('dashboard.circuitHalfOpen')
  return t('dashboard.circuitClosed')
}

const hourlyBars = computed(() => {
  const hourly: { hour: string; count: number }[] = stats.value.hourly || []
  if (!hourly.length) return []
  const max = Math.max(...hourly.map(h => Number(h.count) || 0), 1)
  return hourly.map(h => {
    const count = Number(h.count) || 0
    const hour = String(h.hour)
  const shortHour = hour.length >= 13 ? hour.slice(11, 16) : hour
    return { hour, count, shortHour, pct: Math.round((count / max) * 100) }
  })
})

function poolStatus(percent?: number) {
  if (percent == null) return undefined
  if (percent >= 90) return 'exception'
  if (percent >= 70) return 'warning'
  return undefined
}

function circuitTagType(status?: string) {
  if (status === 'OPEN') return 'danger'
  if (status === 'HALF_OPEN') return 'warning'
  return 'info'
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
  loading.value = true
  try {
    const data = await http.get('/admin/monitoring/dashboard', { params: { hours: hours.value } })
    stats.value = data
    applyRuntime(data)
  } finally {
    loading.value = false
  }
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
.hours-label { font-size: 13px; color: var(--bw-gray-500); }
.cards { margin-bottom: 16px; }
.section { margin-bottom: 16px; }
.metric-label { font-size: 12px; color: #737373; text-transform: uppercase; letter-spacing: 0.04em; }
.metric-value { font-size: 28px; font-weight: 600; margin-top: 8px; }
.metric-value.warn { color: #b45309; }
.metric-sub { font-size: 11px; color: #737373; margin-top: 8px; line-height: 1.4; }
.metric-card { border: 1px solid #e5e5e5; }
.metric-warn { border-left: 3px solid #b45309; background: #fffbeb; }
.secondary-cards { margin-bottom: 16px; }
.pool-card { border: 1px solid #e5e5e5; }
.pool-summary { max-width: 640px; }
.pool-numbers { margin-bottom: 12px; }
.pool-big { font-size: 32px; font-weight: 700; margin-right: 8px; }
.pool-sub { font-size: 13px; color: #737373; }
.pool-bar { margin-bottom: 8px; }
.pool-hint { font-size: 12px; color: #737373; line-height: 1.5; }
.trend-chart { display: flex; align-items: flex-end; gap: 4px; height: 140px; margin-bottom: 16px; padding: 8px 4px 0; border-bottom: 1px solid #e5e5e5; overflow-x: auto; }
.trend-bar-col { flex: 1; min-width: 28px; max-width: 48px; display: flex; flex-direction: column; align-items: center; height: 100%; }
.trend-bar-track { flex: 1; width: 100%; display: flex; align-items: flex-end; justify-content: center; }
.trend-bar-fill { width: 70%; min-height: 2px; border-radius: 3px 3px 0 0; transition: height 0.3s; }
.trend-bar-label { font-size: 10px; color: #737373; margin-top: 4px; white-space: nowrap; }
.hourly-table { margin-top: 8px; }
</style>

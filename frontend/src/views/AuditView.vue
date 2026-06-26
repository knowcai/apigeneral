<template>
  <div>
    <div class="toolbar">
      <h2>{{ t('audit.title') }}</h2>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane :label="t('audit.tabAll')" name="all">
        <el-table :data="logs" stripe class="data-card">
          <el-table-column prop="createdAt" :label="t('col.time')" width="180" />
          <el-table-column prop="username" :label="t('col.operator')" width="120" />
          <el-table-column prop="action" :label="t('col.action')" width="100" />
          <el-table-column prop="resourceType" :label="t('col.resourceType')" width="140" />
          <el-table-column prop="resourceName" :label="t('col.resource')" min-width="160" />
          <el-table-column prop="detail" :label="t('col.detail')" min-width="200">
            <template #default="{ row }"><span class="detail">{{ row.detail }}</span></template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="loadAll" />
        </div>
      </el-tab-pane>

      <el-tab-pane :label="t('audit.tabKeyRotation')" name="keys">
        <div class="hint block-hint">{{ t('audit.keyRotationHint') }}</div>
        <el-table :data="keyEvents" stripe class="data-card">
          <el-table-column prop="createdAt" :label="t('col.time')" width="180" />
          <el-table-column prop="username" :label="t('col.operator')" width="120" />
          <el-table-column :label="t('audit.keyAction')" width="120">
            <template #default="{ row }">
              {{ row.action === 'CREATE' ? t('audit.keyCreate') : t('audit.keyRotate') }}
            </template>
          </el-table-column>
          <el-table-column prop="resourceName" :label="t('col.resource')" min-width="180" />
          <el-table-column :label="t('approval.title')" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="row.viaApproval ? 'warning' : 'success'">
                {{ row.viaApproval ? t('audit.keyViaApproval') : t('audit.keyDirect') }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="keyPage" :page-size="size" :total="keyTotal" layout="prev, pager, next" @current-change="loadKeys" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import http from '../api/http'

const { t } = useI18n()
const tab = ref('all')
const logs = ref<any[]>([])
const keyEvents = ref<any[]>([])
const page = ref(1)
const keyPage = ref(1)
const size = 20
const total = ref(0)
const keyTotal = ref(0)

async function loadAll() {
  const data = await http.get<any>(`/admin/audit-logs?page=${page.value - 1}&size=${size}`)
  logs.value = data.content
  total.value = data.totalElements
}

async function loadKeys() {
  const data = await http.get<any>(`/admin/audit-logs/theme-api-keys?page=${keyPage.value - 1}&size=${size}`)
  keyEvents.value = data.content
  keyTotal.value = data.totalElements
}

watch(tab, (v) => {
  if (v === 'keys' && !keyEvents.value.length) loadKeys()
})

onMounted(loadAll)
</script>

<style scoped>
.toolbar { margin-bottom: 16px; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
.detail { font-size: 12px; color: #525252; word-break: break-all; }
.hint { font-size: 12px; color: #737373; }
.block-hint { margin-bottom: 12px; padding: 10px 12px; background: #fafafa; border: 1px solid #e5e5e5; border-radius: 2px; }
</style>

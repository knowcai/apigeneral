<template>
  <div>
    <div class="toolbar">
      <h2>{{ t('audit.title') }}</h2>
    </div>
    <el-table :data="logs" stripe>
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
      <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="load" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import http from '../api/http'

const { t } = useI18n()
const logs = ref<any[]>([])
const page = ref(1)
const size = 20
const total = ref(0)

async function load() {
  const data = await http.get<any>(`/admin/audit-logs?page=${page.value - 1}&size=${size}`)
  logs.value = data.content
  total.value = data.totalElements
}

onMounted(load)
</script>

<style scoped>
.toolbar { margin-bottom: 16px; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
.detail { font-size: 12px; color: #525252; word-break: break-all; }
</style>

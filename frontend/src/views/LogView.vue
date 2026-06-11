<template>
  <div>
    <div class="toolbar">
      <h2>访问监控</h2>
      <el-input v-model="apiCode" placeholder="按 API 编码筛选" style="width: 240px" clearable @clear="load" />
      <el-button @click="load">查询</el-button>
    </div>

    <el-table :data="logs" stripe>
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column prop="apiCode" label="API" width="140" />
      <el-table-column prop="apiVersion" label="版本" width="70" />
      <el-table-column prop="consumerName" label="调用方" width="120" />
      <el-table-column prop="clientIp" label="IP" width="130" />
      <el-table-column prop="responseMode" label="模式" width="90" />
      <el-table-column prop="responseRows" label="行数" width="80" />
      <el-table-column prop="responseBytes" label="字节" width="90" />
      <el-table-column prop="durationMs" label="耗时ms" width="90" />
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column prop="errorMessage" label="错误" min-width="160" />
    </el-table>

    <el-pagination
      class="pager"
      background
      layout="prev, pager, next"
      :total="total"
      :page-size="size"
      v-model:current-page="page"
      @current-change="load"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '../api/http'

const logs = ref<any[]>([])
const apiCode = ref('')
const page = ref(1)
const size = 20
const total = ref(0)

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

<template>
  <div>
    <div class="toolbar">
      <h2>连接串管理</h2>
      <el-button type="primary" @click="openCreate">新建连接</el-button>
    </div>

    <el-table :data="list" stripe>
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="type" label="类型" width="120" />
      <el-table-column prop="host" label="Host" />
      <el-table-column prop="port" label="端口" width="80" />
      <el-table-column prop="databaseName" label="数据库" />
      <el-table-column prop="env" label="环境" width="80" />
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link @click="testConn(row)">测试</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑连接' : '新建连接'" width="680px">
      <el-form :model="form" label-width="130px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" @change="loadTemplate">
            <el-option label="Doris" value="DORIS" />
            <el-option label="ClickHouse" value="CLICKHOUSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="Host"><el-input v-model="form.host" /></el-form-item>
        <el-form-item label="端口"><el-input-number v-model="form.port" :min="1" /></el-form-item>
        <el-form-item label="数据库"><el-input v-model="form.databaseName" /></el-form-item>
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="环境"><el-input v-model="form.env" /></el-form-item>
        <el-form-item label="只读">
          <el-switch v-model="form.readonly" />
        </el-form-item>

        <el-divider content-position="left">连接参数</el-divider>

        <el-form-item label="协议">
          <el-select v-model="params.protocol" style="width: 100%">
            <el-option v-if="form.type === 'DORIS'" label="MySQL 协议" value="mysql" />
            <el-option v-if="form.type === 'CLICKHOUSE'" label="HTTP" value="http" />
            <el-option v-if="form.type === 'CLICKHOUSE'" label="Native" value="native" />
          </el-select>
        </el-form-item>
        <el-form-item label="连接池最小空闲">
          <el-input-number v-model="params.poolMinIdle" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="连接池最大连接">
          <el-input-number v-model="params.poolMaxActive" :min="1" :max="200" />
        </el-form-item>
        <el-form-item label="连接超时(ms)">
          <el-input-number v-model="params.connectTimeoutMs" :min="1000" :step="1000" />
        </el-form-item>

        <template v-if="form.type === 'DORIS'">
          <el-form-item label="查询超时(秒)">
            <el-input-number v-model="params.queryTimeoutSec" :min="1" />
          </el-form-item>
        </template>

        <template v-if="form.type === 'CLICKHOUSE'">
          <el-form-item label="启用压缩">
            <el-switch v-model="params.compress" />
          </el-form-item>
          <el-form-item label="最大线程数">
            <el-input-number v-model="params.maxThreads" :min="1" :max="64" />
          </el-form-item>
        </template>

        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'

interface Datasource {
  id?: number
  name: string
  type: string
  host: string
  port: number
  databaseName: string
  username?: string
  password?: string
  env?: string
  readonly?: boolean
  status?: string
  description?: string
  defaultParams?: Record<string, unknown>
}

interface ParamForm {
  protocol: string
  poolMinIdle: number
  poolMaxActive: number
  connectTimeoutMs: number
  queryTimeoutSec: number
  compress: boolean
  maxThreads: number
}

const list = ref<Datasource[]>([])
const visible = ref(false)
const form = reactive<Datasource>({
  name: '', type: 'DORIS', host: '127.0.0.1', port: 9030, databaseName: '', readonly: true, env: 'dev'
})
const params = reactive<ParamForm>({
  protocol: 'mysql',
  poolMinIdle: 2,
  poolMaxActive: 10,
  connectTimeoutMs: 5000,
  queryTimeoutSec: 300,
  compress: true,
  maxThreads: 4
})

function fillParamsFromBackend(raw?: Record<string, unknown>) {
  const p = raw || {}
  params.protocol = String(p.protocol ?? (form.type === 'DORIS' ? 'mysql' : 'http'))
  params.poolMinIdle = Number(p['pool.minIdle'] ?? 2)
  params.poolMaxActive = Number(p['pool.maxActive'] ?? 10)
  params.connectTimeoutMs = Number(p.connectTimeoutMs ?? 5000)
  params.queryTimeoutSec = Number(p.queryTimeoutSec ?? 300)
  params.compress = p.compress !== false
  params.maxThreads = Number(p.maxThreads ?? 4)
}

function buildDefaultParams(): Record<string, unknown> {
  const base: Record<string, unknown> = {
    protocol: params.protocol,
    'pool.minIdle': params.poolMinIdle,
    'pool.maxActive': params.poolMaxActive,
    connectTimeoutMs: params.connectTimeoutMs
  }
  if (form.type === 'DORIS') {
    base.queryTimeoutSec = params.queryTimeoutSec
  }
  if (form.type === 'CLICKHOUSE') {
    base.compress = params.compress
    base.maxThreads = params.maxThreads
  }
  return base
}

async function load() {
  list.value = await http.get('/admin/datasources')
}

async function loadTemplate() {
  const tpl = await http.get<Record<string, unknown>>(`/admin/datasources/param-template/${form.type}`)
  fillParamsFromBackend(tpl)
}

function openCreate() {
  Object.assign(form, {
    id: undefined, name: '', type: 'DORIS', host: '127.0.0.1', port: 9030,
    databaseName: '', username: '', password: '', env: 'dev', readonly: true, description: ''
  })
  loadTemplate()
  visible.value = true
}

function openEdit(row: Datasource) {
  Object.assign(form, row)
  fillParamsFromBackend(row.defaultParams)
  visible.value = true
}

async function save() {
  try {
    const payload = { ...form, defaultParams: buildDefaultParams() }
    if (form.id) {
      await http.put(`/admin/datasources/${form.id}`, payload)
    } else {
      await http.post('/admin/datasources', payload)
    }
    ElMessage.success('保存成功')
    visible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function testConn(row: Datasource) {
  try {
    await http.post(`/admin/datasources/${row.id}/test`)
    ElMessage.success('连接成功')
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function remove(row: Datasource) {
  await ElMessageBox.confirm('确认删除？', '提示')
  await http.delete(`/admin/datasources/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>

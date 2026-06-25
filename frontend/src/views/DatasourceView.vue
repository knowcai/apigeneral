<template>
  <div>
    <div class="toolbar">
      <h2>{{ t('datasource.title') }}</h2>
      <el-button v-if="auth.canEditDatasource.value" type="primary" @click="openCreate">{{ t('datasource.create') }}</el-button>
      <el-tag v-if="auth.canEditDatasource.value && !auth.isSuperAdmin.value" type="info">{{ t('datasource.needApproval') }}</el-tag>
      <el-tag v-else-if="!auth.canEditDatasource.value" type="info">{{ t('common.readonly') }}</el-tag>
    </div>

    <el-table :data="list" stripe>
      <el-table-column prop="name" :label="t('col.name')" />
      <el-table-column :label="t('col.theme')" width="120">
        <template #default="{ row }">{{ themeName(row.themeId) }}</template>
      </el-table-column>
      <el-table-column prop="type" :label="t('col.type')" width="120" />
      <el-table-column prop="host" :label="t('col.host')" />
      <el-table-column prop="port" :label="t('col.port')" width="80" />
      <el-table-column prop="databaseName" :label="t('col.database')" />
      <el-table-column prop="env" :label="t('col.env')" width="80" />
      <el-table-column prop="status" :label="t('col.status')" width="90" />
      <el-table-column :label="t('col.actions')" width="220">
        <template #default="{ row }">
          <template v-if="auth.canEditDatasource.value">
            <el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link @click="testConn(row)">{{ t('common.test') }}</el-button>
            <el-button link type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
          </template>
          <el-button v-else link @click="openEdit(row)">{{ t('common.view') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="dialogTitle" width="680px">
      <el-form :model="form" label-width="130px" :disabled="!auth.canEditDatasource.value && !!form.id">
        <el-form-item :label="t('col.name')"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="t('col.theme')">
          <el-select v-model="form.themeId" style="width: 100%">
            <el-option v-for="th in themes" :key="th.id" :label="th.name" :value="th.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('col.type')">
          <el-select v-model="form.type" @change="loadTemplate">
            <el-option v-for="dt in datasourceTypes" :key="dt.type" :label="dt.displayName" :value="dt.type" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('col.host')"><el-input v-model="form.host" /></el-form-item>
        <el-form-item :label="t('col.port')"><el-input-number v-model="form.port" :min="1" /></el-form-item>
        <el-form-item :label="t('col.database')"><el-input v-model="form.databaseName" /></el-form-item>
        <el-form-item :label="t('login.username')"><el-input v-model="form.username" /></el-form-item>
        <el-form-item :label="t('login.password')"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item :label="t('col.env')"><el-input v-model="form.env" /></el-form-item>
        <el-form-item :label="t('common.readonly')"><el-switch v-model="form.readonly" /></el-form-item>

        <el-divider content-position="left">{{ t('datasource.connParams') }}</el-divider>

        <el-form-item v-if="form.type === 'CLICKHOUSE'" :label="t('datasource.transportProtocol')">
          <el-select v-model="params.protocol" style="width: 100%">
            <el-option :label="t('datasource.protocolHttp')" value="http" />
            <el-option :label="t('datasource.protocolNative')" value="native" />
          </el-select>
          <div class="field-hint">{{ t('datasource.protocolHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('datasource.poolMinIdle')">
          <el-input-number v-model="params.poolMinIdle" :min="1" :max="100" />
        </el-form-item>
        <el-form-item :label="t('datasource.poolMaxActive')">
          <el-input-number v-model="params.poolMaxActive" :min="1" :max="200" />
        </el-form-item>
        <el-form-item :label="t('datasource.connectTimeoutMs')">
          <el-input-number v-model="params.connectTimeoutMs" :min="1000" :step="1000" />
        </el-form-item>

        <template v-if="form.type === 'TRINO'">
          <el-form-item label="Schema"><el-input v-model="params.schema" /></el-form-item>
          <el-form-item :label="t('datasource.queryTimeoutSec')">
            <el-input-number v-model="params.queryTimeoutSec" :min="1" />
          </el-form-item>
        </template>

        <template v-if="form.type === 'POSTGRES' || form.type === 'DORIS'">
          <el-form-item :label="t('datasource.queryTimeoutSec')">
            <el-input-number v-model="params.queryTimeoutSec" :min="1" />
          </el-form-item>
        </template>

        <template v-if="form.type === 'CLICKHOUSE'">
          <el-form-item :label="t('datasource.compress')"><el-switch v-model="params.compress" /></el-form-item>
          <el-form-item :label="t('datasource.maxThreads')">
            <el-input-number v-model="params.maxThreads" :min="1" :max="64" />
          </el-form-item>
        </template>

        <el-form-item :label="t('col.description')"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
        <el-button :loading="testing" @click="testFormConn">{{ t('datasource.testConn') }}</el-button>
        <el-button type="primary" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import http, { isApprovalResult } from '../api/http'
import { auth } from '../stores/auth'

const { t } = useI18n()

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
  themeId?: number
  defaultParams?: Record<string, unknown>
}

interface ParamForm {
  protocol: string
  schema: string
  poolMinIdle: number
  poolMaxActive: number
  connectTimeoutMs: number
  queryTimeoutSec: number
  compress: boolean
  maxThreads: number
}

const list = ref<Datasource[]>([])
const themes = ref<any[]>([])
const datasourceTypes = ref<{ type: string; displayName: string }[]>([])
const visible = ref(false)
const testing = ref(false)
const form = reactive<Datasource>({
  name: '', type: 'DORIS', host: '127.0.0.1', port: 9030, databaseName: '', readonly: true, env: 'dev'
})

const dialogTitle = computed(() => {
  if (!form.id) return t('datasource.create')
  return auth.canEditDatasource.value ? t('datasource.edit') : t('datasource.view')
})
const params = reactive<ParamForm>({
  protocol: 'mysql',
  schema: 'default',
  poolMinIdle: 2,
  poolMaxActive: 10,
  connectTimeoutMs: 5000,
  queryTimeoutSec: 300,
  compress: true,
  maxThreads: 4
})

function fillParamsFromBackend(raw?: Record<string, unknown>) {
  const p = raw || {}
  if (form.type === 'CLICKHOUSE') {
    params.protocol = String(p.protocol ?? 'http')
  }
  params.schema = String(p.schema ?? 'default')
  params.poolMinIdle = Number(p['pool.minIdle'] ?? 2)
  params.poolMaxActive = Number(p['pool.maxActive'] ?? 10)
  params.connectTimeoutMs = Number(p.connectTimeoutMs ?? 5000)
  params.queryTimeoutSec = Number(p.queryTimeoutSec ?? 300)
  params.compress = p.compress !== false
  params.maxThreads = Number(p.maxThreads ?? 4)
}

function buildDefaultParams(): Record<string, unknown> {
  const base: Record<string, unknown> = {
    'pool.minIdle': params.poolMinIdle,
    'pool.maxActive': params.poolMaxActive,
    connectTimeoutMs: params.connectTimeoutMs
  }
  if (form.type === 'CLICKHOUSE') {
    base.protocol = params.protocol
  }
  if (form.type === 'DORIS' || form.type === 'POSTGRES' || form.type === 'STARROCKS') {
    base.queryTimeoutSec = params.queryTimeoutSec
  }
  if (form.type === 'CLICKHOUSE') {
    base.compress = params.compress
    base.maxThreads = params.maxThreads
  }
  if (form.type === 'TRINO') {
    base.schema = params.schema
    base.queryTimeoutSec = params.queryTimeoutSec
  }
  return base
}

async function load() {
  themes.value = await http.get('/admin/themes')
  list.value = await http.get('/admin/datasources')
  datasourceTypes.value = await http.get('/admin/datasources/types')
}

function themeName(themeId?: number) {
  if (themeId == null) return '-'
  return themes.value.find(t => t.id === themeId)?.name ?? '-'
}

async function loadTemplate() {
  const tpl = await http.get<Record<string, unknown>>(`/admin/datasources/param-template/${form.type}`)
  fillParamsFromBackend(tpl)
}

function openCreate() {
  Object.assign(form, {
    id: undefined, name: '', type: 'DORIS', host: '127.0.0.1', port: 9030,
    databaseName: '', username: '', password: '', env: 'dev', readonly: true, description: '',
    themeId: themes.value[0]?.id
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
  if (!form.themeId) return ElMessage.warning(t('common.selectTheme'))
  try {
    const payload = { ...form, defaultParams: buildDefaultParams() }
    let result: unknown
    if (form.id) {
      result = await http.put(`/admin/datasources/${form.id}`, payload)
    } else {
      result = await http.post('/admin/datasources', payload)
    }
    visible.value = false
    await load()
    if (isApprovalResult(result)) {
      ElMessage.success(result.message)
    } else {
      ElMessage.success(t('common.saved'))
    }
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function testConn(row: Datasource) {
  try {
    await http.post(`/admin/datasources/${row.id}/test`)
    ElMessage.success(t('datasource.testOk'))
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function testFormConn() {
  if (!form.host?.trim()) {
    ElMessage.warning(t('datasource.fillHost'))
    return
  }
  if (!form.databaseName?.trim()) {
    ElMessage.warning(t('datasource.fillDatabase'))
    return
  }
  testing.value = true
  try {
    const payload = { ...form, defaultParams: buildDefaultParams() }
    const url = form.id ? `/admin/datasources/test?id=${form.id}` : '/admin/datasources/test'
    await http.post(url, payload)
    ElMessage.success(t('datasource.testOk'))
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    testing.value = false
  }
}

async function remove(row: Datasource) {
  await ElMessageBox.confirm(t('common.confirmDelete'), t('common.tip'))
  await http.delete(`/admin/datasources/${row.id}`)
  ElMessage.success(t('common.deleted'))
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.field-hint { font-size: 12px; color: #737373; margin-top: 4px; line-height: 1.4; }
</style>

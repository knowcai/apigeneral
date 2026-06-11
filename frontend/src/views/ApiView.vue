<template>
  <div>
    <div class="toolbar">
      <h2>API / SQL 管理</h2>
      <el-button type="primary" @click="openDef">新建 API</el-button>
    </div>

    <el-table :data="apis" stripe @row-click="selectApi">
      <el-table-column prop="apiCode" label="编码" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="theme" label="主题" />
      <el-table-column prop="updatedBy" label="修改人" width="100" />
    </el-table>

    <div v-if="currentApi" class="version-panel">
      <div class="toolbar">
        <h3>{{ currentApi.name }} - 版本列表</h3>
        <el-button type="primary" @click="openVersion">新建版本</el-button>
      </div>
      <el-table :data="versions" stripe>
        <el-table-column prop="versionNo" label="版本" width="70" />
        <el-table-column prop="responseMode" label="模式" width="90" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="updatedBy" label="修改人" width="100" />
        <el-table-column label="SQL" min-width="200">
          <template #default="{ row }">
            <span class="sql-preview">{{ row.sqlTemplate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <el-button link @click="editVersion(row)">编辑</el-button>
            <el-button link type="success" @click="publish(row)" :disabled="row.status === 'PUBLISHED'">发布</el-button>
            <el-button link @click="showEndpoint(row)">API 路径</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="defVisible" title="API 定义" width="520px">
      <el-form :model="defForm" label-width="90px">
        <el-form-item label="编码"><el-input v-model="defForm.apiCode" :disabled="!!defForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="defForm.name" /></el-form-item>
        <el-form-item label="主题"><el-input v-model="defForm.theme" placeholder="如 finance" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="defForm.description" type="textarea" /></el-form-item>
        <el-form-item label="修改人"><el-input v-model="defForm.updatedBy" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="defVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDef">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="verVisible" :title="verForm.id ? '编辑版本' : '新建版本'" width="720px">
      <el-form :model="verForm" label-width="140px">
        <el-form-item label="数据源">
          <el-select v-model="verForm.datasourceId" style="width: 100%">
            <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="响应模式">
          <el-select v-model="verForm.responseMode" @change="onModeChange">
            <el-option label="分页 PAGE" value="PAGE" />
            <el-option label="分块 CHUNK" value="CHUNK" />
            <el-option label="流式 STREAM" value="STREAM" />
          </el-select>
        </el-form-item>
        <el-form-item label="SQL 模板">
          <el-input v-model="verForm.sqlTemplate" type="textarea" :rows="5" placeholder="SELECT * FROM t WHERE dt = :dt" />
        </el-form-item>
        <el-form-item label="参数 Schema">
          <el-input v-model="paramSchemaJson" type="textarea" :rows="3" placeholder='{"dt":{"type":"string","default":"2024-01-01"}}' />
        </el-form-item>

        <el-divider content-position="left">响应配置</el-divider>

        <el-form-item label="超时(秒)">
          <el-input-number v-model="respConfig.timeoutSec" :min="5" :max="3600" />
        </el-form-item>
        <el-form-item label="IP 白名单">
          <el-input v-model="respConfig.ipWhitelistText" placeholder="多个 IP 用逗号分隔，留空表示不限制" />
        </el-form-item>
        <el-form-item label="接口 QPS 上限">
          <el-input-number v-model="respConfig.apiQps" :min="0" :max="10000" />
          <div class="hint">覆盖全局单接口限流，0 表示使用全局配置</div>
        </el-form-item>

        <template v-if="verForm.responseMode === 'PAGE'">
          <el-form-item>
            <template #label>
              <span>默认每页条数</span>
            </template>
            <el-input-number v-model="respConfig.defaultPageSize" :min="1" :max="respConfig.maxPageSize" />
            <div class="hint">行规默认 20</div>
          </el-form-item>
          <el-form-item label="单页最大条数">
            <el-input-number v-model="respConfig.maxPageSize" :min="1" :max="10000" />
            <div class="hint">公开 API 常限 100，内部数仓 API 建议 500～1000</div>
          </el-form-item>
          <el-form-item label="最大偏移量">
            <el-input-number v-model="respConfig.maxOffset" :min="1000" :max="10000000" :step="10000" />
            <div class="hint">深分页保护，建议 10 万，防止 OFFSET 过大拖垮库</div>
          </el-form-item>
        </template>

        <template v-if="verForm.responseMode === 'CHUNK'">
          <el-form-item label="默认每批条数">
            <el-input-number v-model="respConfig.chunkSize" :min="100" :max="respConfig.maxChunkSize" :step="100" />
            <div class="hint">分批拉取默认值，建议 1000</div>
          </el-form-item>
          <el-form-item label="单批最大条数">
            <el-input-number v-model="respConfig.maxChunkSize" :min="100" :max="50000" :step="500" />
            <div class="hint">单次请求上限，行规 5000～10000</div>
          </el-form-item>
          <el-form-item label="累计最大条数">
            <el-input-number v-model="respConfig.maxTotalRows" :min="10000" :max="10000000" :step="10000" />
            <div class="hint">整次分批会话总行数上限，建议 50 万</div>
          </el-form-item>
        </template>

        <template v-if="verForm.responseMode === 'STREAM'">
          <el-form-item label="每批推送条数">
            <el-input-number v-model="respConfig.streamBatchSize" :min="50" :max="5000" :step="50" />
            <div class="hint">每批 flush 行数，建议 500</div>
          </el-form-item>
          <el-form-item label="流式最大条数">
            <el-input-number v-model="respConfig.maxStreamRows" :min="1000" :max="10000000" :step="10000" />
            <div class="hint">安全阀，防止无限流，建议 10 万</div>
          </el-form-item>
          <el-form-item label="最长时长(秒)">
            <el-input-number v-model="respConfig.maxStreamDurationSec" :min="30" :max="3600" />
            <div class="hint">超时自动断开，建议 300 秒（5 分钟）</div>
          </el-form-item>
        </template>

        <el-form-item label="修改人"><el-input v-model="verForm.updatedBy" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="verVisible = false">取消</el-button>
        <el-button type="primary" @click="saveVersion">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'

interface RespConfigForm {
  timeoutSec: number
  ipWhitelistText: string
  apiQps: number
  defaultPageSize: number
  maxPageSize: number
  maxOffset: number
  chunkSize: number
  maxChunkSize: number
  maxTotalRows: number
  streamBatchSize: number
  maxStreamRows: number
  maxStreamDurationSec: number
}

const PAGE_DEFAULTS: RespConfigForm = {
  timeoutSec: 60, ipWhitelistText: '', apiQps: 0,
  defaultPageSize: 20, maxPageSize: 500, maxOffset: 100000,
  chunkSize: 1000, maxChunkSize: 10000, maxTotalRows: 500000,
  streamBatchSize: 500, maxStreamRows: 100000, maxStreamDurationSec: 300
}

const apis = ref<any[]>([])
const versions = ref<any[]>([])
const datasources = ref<any[]>([])
const currentApi = ref<any>(null)
const defVisible = ref(false)
const verVisible = ref(false)
const paramSchemaJson = ref('{}')

const defForm = reactive<any>({ apiCode: '', name: '', theme: '', description: '', updatedBy: 'admin' })
const verForm = reactive<any>({ datasourceId: null, sqlTemplate: '', responseMode: 'PAGE', updatedBy: 'admin' })
const respConfig = reactive<RespConfigForm>({ ...PAGE_DEFAULTS })

function resetRespConfig(mode: string) {
  Object.assign(respConfig, PAGE_DEFAULTS)
  if (mode === 'CHUNK') {
    respConfig.chunkSize = 1000
    respConfig.maxChunkSize = 10000
    respConfig.maxTotalRows = 500000
  } else if (mode === 'STREAM') {
    respConfig.streamBatchSize = 500
    respConfig.maxStreamRows = 100000
    respConfig.maxStreamDurationSec = 300
  }
}

function fillRespConfig(raw?: Record<string, unknown>) {
  const c = raw || {}
  respConfig.timeoutSec = Number(c.timeoutSec ?? 60)
  respConfig.ipWhitelistText = Array.isArray(c.ipWhitelist) ? (c.ipWhitelist as string[]).join(', ') : ''
  respConfig.apiQps = Number(c.apiQps ?? 0)
  respConfig.defaultPageSize = Number(c.defaultPageSize ?? 20)
  respConfig.maxPageSize = Number(c.maxPageSize ?? 500)
  respConfig.maxOffset = Number(c.maxOffset ?? 100000)
  respConfig.chunkSize = Number(c.chunkSize ?? 1000)
  respConfig.maxChunkSize = Number(c.maxChunkSize ?? 10000)
  respConfig.maxTotalRows = Number(c.maxTotalRows ?? 500000)
  respConfig.streamBatchSize = Number(c.streamBatchSize ?? 500)
  respConfig.maxStreamRows = Number(c.maxStreamRows ?? 100000)
  respConfig.maxStreamDurationSec = Number(c.maxStreamDurationSec ?? 300)
}

function buildRespConfig(): Record<string, unknown> {
  const ips = respConfig.ipWhitelistText
    .split(/[,，\s]+/)
    .map(s => s.trim())
    .filter(Boolean)
  const base: Record<string, unknown> = {
    timeoutSec: respConfig.timeoutSec,
    ipWhitelist: ips,
    apiQps: respConfig.apiQps > 0 ? respConfig.apiQps : undefined
  }
  if (verForm.responseMode === 'PAGE') {
    base.defaultPageSize = respConfig.defaultPageSize
    base.maxPageSize = respConfig.maxPageSize
    base.maxOffset = respConfig.maxOffset
  } else if (verForm.responseMode === 'CHUNK') {
    base.chunkSize = respConfig.chunkSize
    base.maxChunkSize = respConfig.maxChunkSize
    base.maxTotalRows = respConfig.maxTotalRows
  } else if (verForm.responseMode === 'STREAM') {
    base.streamBatchSize = respConfig.streamBatchSize
    base.maxStreamRows = respConfig.maxStreamRows
    base.maxStreamDurationSec = respConfig.maxStreamDurationSec
  }
  return base
}

function onModeChange(mode: string) {
  resetRespConfig(mode)
}

async function loadApis() {
  apis.value = await http.get('/admin/apis')
  datasources.value = await http.get('/admin/datasources')
}

async function selectApi(row: any) {
  currentApi.value = row
  versions.value = await http.get(`/admin/apis/${row.id}/versions`)
}

function openDef() {
  Object.assign(defForm, { id: undefined, apiCode: '', name: '', theme: '', description: '', updatedBy: 'admin' })
  defVisible.value = true
}

async function saveDef() {
  if (defForm.id) {
    await http.put(`/admin/apis/${defForm.id}`, defForm)
  } else {
    const created = await http.post('/admin/apis', defForm)
    currentApi.value = created
  }
  defVisible.value = false
  await loadApis()
  ElMessage.success('保存成功')
}

function openVersion() {
  if (!currentApi.value) return ElMessage.warning('请先选择 API')
  Object.assign(verForm, { id: undefined, datasourceId: datasources.value[0]?.id, sqlTemplate: '', responseMode: 'PAGE', updatedBy: 'admin' })
  paramSchemaJson.value = '{}'
  resetRespConfig('PAGE')
  verVisible.value = true
}

function editVersion(row: any) {
  Object.assign(verForm, row)
  paramSchemaJson.value = JSON.stringify(row.paramSchema || {}, null, 2)
  fillRespConfig(row.responseConfig)
  verVisible.value = true
}

async function saveVersion() {
  const payload = {
    ...verForm,
    paramSchema: JSON.parse(paramSchemaJson.value || '{}'),
    responseConfig: buildRespConfig()
  }
  if (verForm.id) {
    await http.put(`/admin/apis/versions/${verForm.id}`, payload)
  } else {
    await http.post(`/admin/apis/${currentApi.value.id}/versions`, payload)
  }
  verVisible.value = false
  versions.value = await http.get(`/admin/apis/${currentApi.value.id}/versions`)
  ElMessage.success('保存成功')
}

async function publish(row: any) {
  await http.post(`/admin/apis/versions/${row.id}/publish?operator=admin`)
  versions.value = await http.get(`/admin/apis/${currentApi.value.id}/versions`)
  ElMessage.success('已发布')
}

async function showEndpoint(row: any) {
  const info = await http.get<{ path: string }>(`/admin/apis/versions/${row.id}/endpoint`)
  ElMessage.info(`路径: ${info.path}`)
}

onMounted(loadApis)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.version-panel { margin-top: 24px; }
.sql-preview { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; color: #525252; }
.hint { font-size: 12px; color: #737373; margin-top: 4px; line-height: 1.4; }
</style>

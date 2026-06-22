<template>
  <div class="api-page">
    <div class="page-header">
      <h2>API / SQL 管理</h2>
      <el-button v-if="auth.canCreateApi.value" type="primary" @click="openDef">新建 API</el-button>
    </div>

    <div class="split-layout">
      <section ref="apiPanelRef" class="panel api-panel">
        <div class="panel-head">
          <span class="panel-title">API 列表</span>
          <span class="panel-meta">共 {{ apis.length }} 个</span>
        </div>
        <div class="panel-body">
          <el-table
            ref="apiTableRef"
            :data="apis"
            stripe
            highlight-current-row
            :height="apiTableHeight"
            @row-click="selectApi"
          >
            <el-table-column prop="apiCode" label="编码" min-width="140" show-overflow-tooltip />
            <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="theme" label="主题" width="90" />
            <el-table-column prop="createdBy" label="创建人" width="100" />
            <el-table-column prop="updatedBy" label="修改人" width="100" />
          </el-table>
        </div>
      </section>

      <section ref="versionPanelRef" class="panel version-panel">
        <div class="panel-head">
          <span class="panel-title">
            {{ currentApi ? `${currentApi.name} · 版本列表` : '版本列表' }}
          </span>
          <el-button
            v-if="canEditCurrent"
            type="primary"
            size="small"
            :disabled="!currentApi"
            @click="openVersion"
          >
            新建版本
          </el-button>
        </div>
        <div class="panel-body">
          <el-empty
            v-if="!currentApi"
            description="请在上方选择一个 API"
            :image-size="64"
            class="panel-empty"
          />
          <el-table
            v-else
            :data="versions"
            stripe
            :height="versionTableHeight"
          >
            <el-table-column prop="versionNo" label="版本" width="70" />
            <el-table-column prop="responseMode" label="模式" width="90" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updatedBy" label="修改人" width="100" />
            <el-table-column label="SQL" min-width="200">
              <template #default="{ row }">
                <span class="sql-preview">{{ row.sqlTemplate }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canEditCurrent" link @click.stop="editVersion(row)" :disabled="row.status === 'PUBLISHED'">编辑</el-button>
                <el-button v-if="canEditCurrent" link type="success" @click.stop="publish(row)" :disabled="row.status === 'PUBLISHED'">发布</el-button>
                <el-button link @click.stop="showEndpoint(row)" :disabled="row.status === 'DRAFT'">路径</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </div>

    <el-dialog v-model="defVisible" title="API 定义" width="520px">
      <el-form :model="defForm" label-width="90px">
        <el-form-item label="编码"><el-input v-model="defForm.apiCode" :disabled="!!defForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="defForm.name" /></el-form-item>
        <el-form-item label="主题"><el-input v-model="defForm.theme" placeholder="如 finance" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="defForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="defVisible = false">取消</el-button>
        <el-button v-if="!defForm.id || canEditCurrent" type="primary" @click="saveDef">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="verVisible" :title="verForm.id ? '编辑版本' : '新建版本'" width="720px">
      <el-form :model="verForm" label-width="140px">
        <el-form-item label="数据源">
          <el-select v-model="verForm.datasourceId" style="width: 100%">
            <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
          </el-select>
        </el-form-item>

        <div class="hint block-hint mode-hint">
          <strong>分页入参（请求方 query 参数）</strong><br />
          请求方须在 URL query 中传入（GET/POST 均如此，与 SQL 的 <code>:参数名</code> 无关）：
          <ul>
            <li><code>page</code>：页码，从 1 开始，<strong>必填</strong></li>
            <li><code>pageSize</code>：每页条数，<strong>必填</strong>，且不能超过下方「单页最大条数」</li>
          </ul>
          示例：<code>GET /api/data/v1/theme/apiCode?page=1&amp;pageSize=20&amp;id=123</code>
        </div>

        <el-form-item label="SQL 模板">
          <el-input v-model="verForm.sqlTemplate" type="textarea" :rows="5" placeholder="SELECT * FROM t WHERE id = :id AND dt = :dt" />
          <div class="hint">
            SQL 中使用 <code>:参数名</code> 引用请求方传入的参数，例如 <code>:id</code>、<code>:dt</code>。
            GET 请求通过 query 传参，POST 请求通过 JSON body 传参；未传必填参数时将报错。
          </div>
        </el-form-item>

        <el-divider content-position="left">响应配置</el-divider>

        <el-form-item label="超时(秒)">
          <el-input-number v-model="respConfig.timeoutSec" :min="5" :max="3600" />
          <div class="hint">SQL 执行超过此秒数将中断并返回错误</div>
        </el-form-item>
        <el-form-item label="IP 白名单">
          <el-input v-model="respConfig.ipWhitelistText" placeholder="多个 IP 用逗号分隔，留空表示不限制" />
        </el-form-item>
        <el-form-item label="接口 QPS 上限">
          <el-input-number v-model="respConfig.apiQps" :min="0" :max="10000" />
          <div class="hint">覆盖全局单接口限流，0 表示使用全局配置</div>
        </el-form-item>

        <el-form-item label="单页最大条数">
          <el-input-number v-model="respConfig.maxPageSize" :min="1" :max="10000" />
          <div class="hint">请求方 pageSize 不能超过此值；公开 API 建议 100，内部数仓 API 建议 500～1000</div>
        </el-form-item>
        <el-form-item label="最大偏移量">
          <el-input-number v-model="respConfig.maxOffset" :min="1000" :max="10000000" :step="10000" />
          <div class="hint">深分页保护，建议 10 万，防止 OFFSET 过大拖垮库</div>
        </el-form-item>

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
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { TableInstance } from 'element-plus'
import http from '../api/http'
import { auth } from '../stores/auth'

interface RespConfigForm {
  timeoutSec: number
  ipWhitelistText: string
  apiQps: number
  maxPageSize: number
  maxOffset: number
}

const PAGE_DEFAULTS: RespConfigForm = {
  timeoutSec: 60, ipWhitelistText: '', apiQps: 0,
  maxPageSize: 500, maxOffset: 100000
}

const apis = ref<any[]>([])
const versions = ref<any[]>([])
const datasources = ref<any[]>([])
const currentApi = ref<any>(null)
const canEditCurrent = computed(() => currentApi.value && auth.canEditApi(currentApi.value))
const defVisible = ref(false)
const verVisible = ref(false)
const defForm = reactive<any>({ apiCode: '', name: '', theme: '', description: '', updatedBy: 'admin' })
const verForm = reactive<any>({ datasourceId: null, sqlTemplate: '', responseMode: 'PAGE', updatedBy: 'admin' })
const respConfig = reactive<RespConfigForm>({ ...PAGE_DEFAULTS })

const apiPanelRef = ref<HTMLElement>()
const versionPanelRef = ref<HTMLElement>()
const apiTableRef = ref<TableInstance>()
const apiTableHeight = ref(320)
const versionTableHeight = ref(200)

const PANEL_HEAD = 44

function updateTableHeights() {
  if (apiPanelRef.value) {
    apiTableHeight.value = Math.max(160, apiPanelRef.value.clientHeight - PANEL_HEAD)
  }
  if (versionPanelRef.value) {
    versionTableHeight.value = Math.max(120, versionPanelRef.value.clientHeight - PANEL_HEAD)
  }
}

let resizeObserver: ResizeObserver | null = null

function resetRespConfig() {
  Object.assign(respConfig, PAGE_DEFAULTS)
}

function fillRespConfig(raw?: Record<string, unknown>) {
  const c = raw || {}
  respConfig.timeoutSec = Number(c.timeoutSec ?? 60)
  respConfig.ipWhitelistText = Array.isArray(c.ipWhitelist) ? (c.ipWhitelist as string[]).join(', ') : ''
  respConfig.apiQps = Number(c.apiQps ?? 0)
  respConfig.maxPageSize = Number(c.maxPageSize ?? 500)
  respConfig.maxOffset = Number(c.maxOffset ?? 100000)
}

function buildRespConfig(): Record<string, unknown> {
  const ips = respConfig.ipWhitelistText
    .split(/[,，\s]+/)
    .map(s => s.trim())
    .filter(Boolean)
  return {
    timeoutSec: respConfig.timeoutSec,
    ipWhitelist: ips,
    apiQps: respConfig.apiQps > 0 ? respConfig.apiQps : undefined,
    maxPageSize: respConfig.maxPageSize,
    maxOffset: respConfig.maxOffset
  }
}

async function loadApis() {
  apis.value = await http.get('/admin/apis')
  datasources.value = await http.get('/admin/datasources')
  await nextTick()
  if (apis.value.length === 0) {
    currentApi.value = null
    versions.value = []
    return
  }
  const prevId = currentApi.value?.id
  const target = prevId ? apis.value.find(a => a.id === prevId) : apis.value[0]
  if (target) {
    await selectApi(target, false)
    apiTableRef.value?.setCurrentRow(target)
  }
}

async function selectApi(row: any, updateHighlight = true) {
  currentApi.value = row
  versions.value = await http.get(`/admin/apis/${row.id}/versions`)
  if (updateHighlight) {
    apiTableRef.value?.setCurrentRow(row)
  }
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
  resetRespConfig()
  verVisible.value = true
}

function editVersion(row: any) {
  Object.assign(verForm, row)
  verForm.responseMode = 'PAGE'
  fillRespConfig(row.responseConfig)
  verVisible.value = true
}

async function saveVersion() {
  const payload = {
    ...verForm,
    responseMode: 'PAGE',
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
  try {
    await http.post(`/admin/apis/versions/${row.id}/publish`)
    versions.value = await http.get(`/admin/apis/${currentApi.value.id}/versions`)
    ElMessage.success(`v${row.versionNo} 已发布，旧版本已自动废弃`)
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

function statusLabel(status: string) {
  if (status === 'PUBLISHED') return '已发布'
  if (status === 'DEPRECATED') return '已废弃'
  return '草稿'
}

function statusTagType(status: string): '' | 'success' | 'info' | 'warning' {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'DEPRECATED') return 'info'
  return 'warning'
}

async function showEndpoint(row: any) {
  const info = await http.get<{ path: string }>(`/admin/apis/versions/${row.id}/endpoint`)
  ElMessage.info(`路径: ${info.path}`)
}

onMounted(async () => {
  resizeObserver = new ResizeObserver(() => updateTableHeights())
  if (apiPanelRef.value) resizeObserver.observe(apiPanelRef.value)
  if (versionPanelRef.value) resizeObserver.observe(versionPanelRef.value)
  await loadApis()
  await nextTick()
  updateTableHeights()
})

onUnmounted(() => {
  resizeObserver?.disconnect()
})
</script>

<style scoped>
.api-page {
  height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.page-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
}

.split-layout {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--bw-gray-200);
  background: var(--bw-white);
}

.api-panel {
  flex: 6;
}

.version-panel {
  flex: 4;
}

.panel-head {
  flex-shrink: 0;
  height: 44px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--bw-gray-200);
  background: var(--bw-gray-100);
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--bw-black);
}

.panel-meta {
  font-size: 12px;
  color: var(--bw-gray-500);
}

.panel-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.panel-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.el-table__body tr.current-row > td.el-table__cell) {
  background: var(--bw-gray-200) !important;
}

:deep(.el-table__body tr) {
  cursor: pointer;
}

.sql-preview {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: #525252;
}

.hint { font-size: 12px; color: #737373; margin-top: 4px; line-height: 1.4; }
.hint code { font-family: ui-monospace, monospace; color: #525252; background: #f5f5f5; padding: 0 4px; border-radius: 3px; }
.block-hint { margin-bottom: 16px; padding: 10px 12px; background: #fafafa; border-radius: 6px; }
.mode-hint { margin: -8px 0 16px 140px; max-width: calc(100% - 140px); }
.block-hint ul { margin: 8px 0 0; padding-left: 18px; }
.block-hint li { margin: 4px 0; }
</style>

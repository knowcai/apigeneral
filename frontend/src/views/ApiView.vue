<template>
  <div class="api-page">
    <div class="page-header">
      <h2>{{ t('api.title') }}</h2>
      <el-button v-if="auth.canCreateApi.value" type="primary" @click="openDef">{{ t('api.create') }}</el-button>
    </div>

    <div class="split-layout">
      <section ref="apiPanelRef" class="panel api-panel">
        <div class="panel-head">
          <span class="panel-title">{{ t('api.listTitle') }}</span>
          <span class="panel-meta">{{ t('api.total', { n: apis.length }) }}</span>
        </div>
        <div class="panel-body">
          <el-table ref="apiTableRef" :data="apis" stripe highlight-current-row :height="apiTableHeight" @row-click="selectApi">
            <el-table-column prop="apiCode" :label="t('col.code')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="name" :label="t('col.name')" min-width="120" show-overflow-tooltip />
            <el-table-column :label="t('col.theme')" width="120">
              <template #default="{ row }">{{ themeName(row.themeId) }}</template>
            </el-table-column>
            <el-table-column prop="createdBy" :label="t('api.creator')" width="100" />
            <el-table-column prop="updatedBy" :label="t('api.modifier')" width="100" />
            <el-table-column :label="t('col.actions')" width="100" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canEditApi(row)" link @click.stop="editDef(row)">{{ t('common.edit') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section ref="versionPanelRef" class="panel version-panel">
        <div class="panel-head">
          <span class="panel-title">{{ currentApi ? t('api.versionListOf', { name: currentApi.name }) : t('api.versionList') }}</span>
          <el-button v-if="canEditCurrent" type="primary" size="small" :disabled="!currentApi" @click="openVersion">{{ t('api.newVersion') }}</el-button>
        </div>
        <div class="panel-body">
          <el-empty v-if="!currentApi" :description="t('api.selectApiHint')" :image-size="64" class="panel-empty" />
          <el-table v-else :data="versions" stripe :height="versionTableHeight">
            <el-table-column prop="versionNo" :label="t('col.version')" width="70" />
            <el-table-column prop="responseMode" :label="t('col.mode')" width="90" />
            <el-table-column prop="status" :label="t('col.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updatedBy" :label="t('api.modifier')" width="100" />
            <el-table-column label="SQL" min-width="200">
              <template #default="{ row }"><span class="sql-preview">{{ row.sqlTemplate }}</span></template>
            </el-table-column>
            <el-table-column :label="t('col.actions')" width="340" fixed="right">
              <template #default="{ row }">
                <template v-if="canEditCurrent">
                  <el-tooltip :content="editDisabledReason(row)" :disabled="!editDisabledReason(row)" placement="top">
                    <span class="action-btn-wrap">
                      <el-button link :disabled="!canEditVersion(row)" @click.stop="editVersion(row)">{{ t('common.edit') }}</el-button>
                    </span>
                  </el-tooltip>
                  <el-tooltip :content="publishDisabledReason(row)" :disabled="!publishDisabledReason(row)" placement="top">
                    <span class="action-btn-wrap">
                      <el-button link type="success" :disabled="!canPublishVersion(row)" @click.stop="publish(row)">{{ t('api.publish') }}</el-button>
                    </span>
                  </el-tooltip>
                  <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click.stop="suspendVersion(row)">{{ t('api.suspend') }}</el-button>
                  <el-button v-if="row.status === 'SUSPENDED'" link type="primary" @click.stop="resumeVersion(row)">{{ t('api.resume') }}</el-button>
                </template>
                <el-tooltip :content="t('api.draftNoPath')" :disabled="row.status !== 'DRAFT'" placement="top">
                  <span class="action-btn-wrap">
                    <el-button link @click.stop="showEndpoint(row)" :disabled="row.status === 'DRAFT'">{{ t('api.endpoint') }}</el-button>
                  </span>
                </el-tooltip>
                <el-button link @click.stop="showDoc(row)">{{ t('api.doc') }}</el-button>
                <el-button v-if="canEditCurrent" link type="primary" @click.stop="openTest(row)">{{ t('api.testRunBtn') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </div>

    <el-dialog v-model="defVisible" :title="t('api.defDialog')" width="520px">
      <el-form :model="defForm" label-width="90px">
        <el-form-item :label="t('col.code')"><el-input v-model="defForm.apiCode" :disabled="!!defForm.id" /></el-form-item>
        <el-form-item :label="t('col.name')"><el-input v-model="defForm.name" /></el-form-item>
        <el-form-item :label="t('col.theme')">
          <el-select v-model="defForm.themeId" style="width: 100%" :disabled="!!defForm.id">
            <el-option v-for="th in themes" :key="th.id" :label="th.name" :value="th.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('col.description')"><el-input v-model="defForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="defVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-if="!defForm.id || canEditCurrent" type="primary" @click="saveDef">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="verVisible" :title="verForm.id ? t('api.verEdit') : t('api.verCreate')" width="720px">
      <el-form :model="verForm" label-width="140px">
        <el-form-item :label="t('api.datasource')">
          <el-select v-model="verForm.datasourceId" style="width: 100%">
            <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
          </el-select>
        </el-form-item>

        <div class="hint block-hint mode-hint">
          <strong>{{ t('api.pageParamsTitle') }}</strong><br />
          {{ t('api.pageParamsDesc') }}
          <ul>
            <li><code>page</code> — {{ t('api.pageRule') }}</li>
            <li><code>pageSize</code> — {{ t('api.pageSizeRule') }}</li>
          </ul>
          {{ t('api.pageExample') }}
        </div>

        <el-form-item :label="t('api.sqlTemplate')">
          <el-input v-model="verForm.sqlTemplate" type="textarea" :rows="5" :placeholder="t('api.sqlPlaceholder')" />
          <div class="hint">{{ t('api.sqlHint') }}</div>
        </el-form-item>

        <el-divider content-position="left">{{ t('api.respSection') }}</el-divider>

        <el-form-item :label="t('api.timeoutSec')">
          <el-input-number v-model="respConfig.timeoutSec" :min="5" :max="3600" />
          <div class="hint">{{ t('api.timeoutHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('api.apiQps')">
          <el-input-number v-model="respConfig.apiQps" :min="0" :max="10000" />
          <div class="hint">{{ t('api.apiQpsHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('api.maxPageSize')">
          <el-input-number v-model="respConfig.maxPageSize" :min="1" :max="10000" />
          <div class="hint">{{ t('api.maxPageSizeHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('api.maxOffset')">
          <el-input-number v-model="respConfig.maxOffset" :min="1000" :max="10000000" :step="10000" />
          <div class="hint">{{ t('api.maxOffsetHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('api.updatedBy')"><el-input v-model="verForm.updatedBy" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="verVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveVersion">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="docVisible" :title="t('api.docTitle')" width="640px">
      <el-descriptions v-if="apiDoc" :column="1" border size="small">
        <el-descriptions-item :label="t('col.name')">{{ apiDoc.apiName }}</el-descriptions-item>
        <el-descriptions-item :label="t('api.docPath')"><code>{{ apiDoc.path }}</code></el-descriptions-item>
        <el-descriptions-item :label="t('api.docMethod')">{{ apiDoc.method }}</el-descriptions-item>
        <el-descriptions-item :label="t('api.docAuth')">{{ apiDoc.authHint }}</el-descriptions-item>
        <el-descriptions-item :label="t('api.docPageParams')">{{ apiDoc.pageParams }}</el-descriptions-item>
        <el-descriptions-item :label="t('col.status')">{{ apiDoc.status }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="apiDoc" class="doc-sql">
        <div class="doc-label">{{ t('api.docSql') }}</div>
        <pre>{{ apiDoc.sqlTemplate }}</pre>
      </div>
      <template #footer>
        <el-button @click="docVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testVisible" :title="t('api.testTitle')" width="720px">
      <div class="hint block-hint">{{ t('api.testHint') }}</div>
      <el-form label-width="100px">
        <el-form-item :label="t('api.testParams')">
          <el-input v-model="testParamsJson" type="textarea" :rows="4" placeholder='{"id": 1, "dt": "2024-01-01"}' />
        </el-form-item>
      </el-form>
      <pre v-if="testResult" class="doc-sql">{{ testResult }}</pre>
      <template #footer>
        <el-button @click="testVisible = false">{{ t('common.close') }}</el-button>
        <el-button type="primary" :loading="testLoading" @click="runTest">{{ t('api.testRun') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import http, { isApprovalResult } from '../api/http'
import { auth } from '../stores/auth'

const { t } = useI18n()

interface RespConfigForm {
  timeoutSec: number
  apiQps: number
  maxPageSize: number
  maxOffset: number
}

const PAGE_DEFAULTS: RespConfigForm = { timeoutSec: 60, apiQps: 0, maxPageSize: 500, maxOffset: 100000 }

const apis = ref<any[]>([])
const themes = ref<any[]>([])
const themeIds = computed(() => themes.value.map(th => th.id))
const versions = ref<any[]>([])
const datasources = ref<any[]>([])
const currentApi = ref<any>(null)
const canEditCurrent = computed(() => currentApi.value && canEditApi(currentApi.value))
const defVisible = ref(false)
const verVisible = ref(false)
const docVisible = ref(false)
const testVisible = ref(false)
const testLoading = ref(false)
const testParamsJson = ref('{}')
const testResult = ref('')
const testVersionId = ref<number | null>(null)
const apiDoc = ref<any>(null)
const defForm = reactive<any>({ apiCode: '', name: '', themeId: null, description: '' })
const verForm = reactive<any>({ datasourceId: null, sqlTemplate: '', responseMode: 'PAGE', updatedBy: 'admin' })
const respConfig = reactive<RespConfigForm>({ ...PAGE_DEFAULTS })

const apiPanelRef = ref<HTMLElement>()
const versionPanelRef = ref<HTMLElement>()
const apiTableRef = ref<TableInstance>()
const apiTableHeight = ref(320)
const versionTableHeight = ref(200)
const PANEL_HEAD = 44

function updateTableHeights() {
  if (apiPanelRef.value) apiTableHeight.value = Math.max(160, apiPanelRef.value.clientHeight - PANEL_HEAD)
  if (versionPanelRef.value) versionTableHeight.value = Math.max(120, versionPanelRef.value.clientHeight - PANEL_HEAD)
}

let resizeObserver: ResizeObserver | null = null

function resetRespConfig() { Object.assign(respConfig, PAGE_DEFAULTS) }

function fillRespConfig(raw?: Record<string, unknown>) {
  const c = raw || {}
  respConfig.timeoutSec = Number(c.timeoutSec ?? 60)
  respConfig.apiQps = Number(c.apiQps ?? 0)
  respConfig.maxPageSize = Number(c.maxPageSize ?? 500)
  respConfig.maxOffset = Number(c.maxOffset ?? 100000)
}

function buildRespConfig(): Record<string, unknown> {
  return {
    timeoutSec: respConfig.timeoutSec,
    apiQps: respConfig.apiQps > 0 ? respConfig.apiQps : undefined,
    maxPageSize: respConfig.maxPageSize,
    maxOffset: respConfig.maxOffset
  }
}

function canEditApi(row: any) { return auth.canEditApi(row, themeIds.value) }

function themeName(themeId?: number) {
  if (themeId == null) return '-'
  return themes.value.find(th => th.id === themeId)?.name ?? '-'
}

async function loadApis() {
  themes.value = await http.get('/admin/themes')
  apis.value = await http.get('/admin/apis')
  datasources.value = await http.get('/admin/datasources')
  await nextTick()
  if (apis.value.length === 0) { currentApi.value = null; versions.value = []; return }
  const prevId = currentApi.value?.id
  const target = prevId ? apis.value.find(a => a.id === prevId) : apis.value[0]
  if (target) { await selectApi(target, false); apiTableRef.value?.setCurrentRow(target) }
}

async function selectApi(row: any, updateHighlight = true) {
  currentApi.value = row
  versions.value = await http.get(`/admin/apis/${row.id}/versions`)
  if (updateHighlight) apiTableRef.value?.setCurrentRow(row)
}

function openDef() {
  Object.assign(defForm, { id: undefined, apiCode: '', name: '', themeId: themes.value[0]?.id ?? null, description: '' })
  defVisible.value = true
}

function editDef(row: any) {
  Object.assign(defForm, { id: row.id, apiCode: row.apiCode, name: row.name, themeId: row.themeId, description: row.description })
  defVisible.value = true
}

async function saveDef() {
  if (!defForm.themeId) return ElMessage.warning(t('common.selectTheme'))
  try {
    let result: unknown
    if (defForm.id) result = await http.put(`/admin/apis/${defForm.id}`, defForm)
    else {
      result = await http.post('/admin/apis', defForm)
      if (!isApprovalResult(result)) currentApi.value = result
    }
    defVisible.value = false
    await loadApis()
    ElMessage.success(isApprovalResult(result) ? result.message : t('common.saved'))
  } catch (e: any) { ElMessage.error(e.message) }
}

function currentOperator() { return auth.state.user?.username || 'admin' }

function openVersion() {
  if (!currentApi.value) return ElMessage.warning(t('api.needSelectApi'))
  Object.assign(verForm, { id: undefined, datasourceId: datasources.value[0]?.id, sqlTemplate: '', responseMode: 'PAGE', updatedBy: currentOperator() })
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
  const sql = verForm.sqlTemplate?.trim()
  if (!sql) return ElMessage.warning(t('api.needSql'))
  if (!/^(SELECT|WITH|SHOW|DESC|EXPLAIN)\b/i.test(sql)) return ElMessage.warning(t('api.sqlPrefix'))
  const payload = { ...verForm, sqlTemplate: sql, responseMode: 'PAGE', responseConfig: buildRespConfig() }
  try {
    let result: unknown
    if (verForm.id) result = await http.put(`/admin/apis/versions/${verForm.id}`, payload)
    else result = await http.post(`/admin/apis/${currentApi.value.id}/versions`, payload)
    verVisible.value = false
    if (isApprovalResult(result)) ElMessage.success(result.message)
    else { versions.value = await http.get(`/admin/apis/${currentApi.value.id}/versions`); ElMessage.success(t('common.saved')) }
  } catch (e: any) { ElMessage.error(e.message) }
}

async function publish(row: any) {
  try {
    const result = await http.post(`/admin/apis/versions/${row.id}/publish`)
    versions.value = await http.get(`/admin/apis/${currentApi.value.id}/versions`)
    ElMessage.success(isApprovalResult(result) ? result.message : t('api.publishedOk', { ver: row.versionNo }))
  } catch (e: any) { ElMessage.error(e.message) }
}

async function suspendVersion(row: any) {
  try {
    await ElMessageBox.confirm(t('api.suspendConfirm', { ver: row.versionNo }), t('api.suspendTitle'), { type: 'warning', confirmButtonText: t('api.suspend'), cancelButtonText: t('common.cancel') })
    const result = await http.post(`/admin/apis/versions/${row.id}/suspend`)
    if (isApprovalResult(result)) ElMessage.success(result.message)
    else { versions.value = await http.get(`/admin/apis/${currentApi.value.id}/versions`); ElMessage.success(t('api.suspendOk', { ver: row.versionNo })) }
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e.message || t('common.operationFailed')) }
}

async function resumeVersion(row: any) {
  try {
    await ElMessageBox.confirm(t('api.resumeConfirm', { ver: row.versionNo }), t('api.resumeTitle'), { type: 'info', confirmButtonText: t('api.resume'), cancelButtonText: t('common.cancel') })
    const result = await http.post(`/admin/apis/versions/${row.id}/resume`)
    if (isApprovalResult(result)) ElMessage.success(result.message)
    else { versions.value = await http.get(`/admin/apis/${currentApi.value.id}/versions`); ElMessage.success(t('api.resumeOk', { ver: row.versionNo })) }
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e.message || t('common.operationFailed')) }
}

function canEditVersion(row: { status: string }) { return row.status === 'DRAFT' }

function editDisabledReason(row: { status: string }) {
  if (row.status === 'PUBLISHED') return t('api.editDisabledPublished')
  if (row.status === 'SUSPENDED') return t('api.editDisabledSuspended')
  if (row.status === 'DEPRECATED') return t('api.editDisabledDeprecated')
  return ''
}

function canPublishVersion(row: { status: string }) { return row.status === 'DRAFT' }

function publishDisabledReason(row: { status: string }) {
  if (row.status === 'PUBLISHED') return t('api.publishDisabledPublished')
  if (row.status === 'SUSPENDED') return t('api.publishDisabledSuspended')
  if (row.status === 'DEPRECATED') return t('api.publishDisabledDeprecated')
  return ''
}

function statusLabel(status: string) {
  if (status === 'PUBLISHED') return t('status.published')
  if (status === 'SUSPENDED') return t('status.suspended')
  if (status === 'DEPRECATED') return t('status.deprecated')
  return t('status.draft')
}

function statusTagType(status: string): '' | 'success' | 'info' | 'warning' | 'danger' {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'SUSPENDED') return 'warning'
  if (status === 'DEPRECATED') return 'info'
  return 'warning'
}

async function showDoc(row: any) {
  apiDoc.value = await http.get(`/admin/apis/versions/${row.id}/doc`)
  docVisible.value = true
}

function openTest(row: any) {
  testVersionId.value = row.id
  testParamsJson.value = '{}'
  testResult.value = ''
  testVisible.value = true
}

async function runTest() {
  if (!testVersionId.value) return
  let params: Record<string, unknown> = {}
  try { params = JSON.parse(testParamsJson.value || '{}') } catch { return ElMessage.warning(t('api.testParamsInvalid')) }
  testLoading.value = true
  try {
    const result = await http.post(`/admin/apis/versions/${testVersionId.value}/test`, params)
    testResult.value = JSON.stringify(result, null, 2)
    ElMessage.success(t('api.testDone'))
  } catch (e: any) { ElMessage.error(e.message) } finally { testLoading.value = false }
}

async function showEndpoint(row: any) {
  const info = await http.get<{ path: string }>(`/admin/apis/versions/${row.id}/endpoint`)
  ElMessage.info(`${t('api.path')}: ${info.path}`)
}

onMounted(async () => {
  resizeObserver = new ResizeObserver(() => updateTableHeights())
  if (apiPanelRef.value) resizeObserver.observe(apiPanelRef.value)
  if (versionPanelRef.value) resizeObserver.observe(versionPanelRef.value)
  await loadApis()
  await nextTick()
  updateTableHeights()
})

onUnmounted(() => { resizeObserver?.disconnect() })
</script>

<style scoped>
.api-page { height: calc(100vh - 64px); display: flex; flex-direction: column; min-height: 0; }
.page-header { flex-shrink: 0; display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; }
.split-layout { flex: 1; min-height: 0; display: flex; flex-direction: column; gap: 12px; }
.panel { min-height: 0; display: flex; flex-direction: column; border: 1px solid var(--bw-gray-200); background: var(--bw-white); }
.api-panel { flex: 6; }
.version-panel { flex: 4; }
.panel-head { flex-shrink: 0; height: 44px; padding: 0 16px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--bw-gray-200); background: var(--bw-gray-100); }
.panel-title { font-size: 13px; font-weight: 600; color: var(--bw-black); }
.panel-meta { font-size: 12px; color: var(--bw-gray-500); }
.panel-body { flex: 1; min-height: 0; overflow: hidden; }
.panel-empty { height: 100%; display: flex; align-items: center; justify-content: center; }
:deep(.el-table__body tr.current-row > td.el-table__cell) { background: var(--bw-gray-200) !important; }
:deep(.el-table__body tr) { cursor: pointer; }
.sql-preview { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; color: #525252; }
.action-btn-wrap { display: inline-block; vertical-align: middle; }
.hint { font-size: 12px; color: #737373; margin-top: 4px; line-height: 1.4; }
.hint code { font-family: ui-monospace, monospace; color: #525252; background: #f5f5f5; padding: 0 4px; border-radius: 3px; }
.block-hint { margin-bottom: 16px; padding: 10px 12px; background: #fafafa; border-radius: 6px; }
.mode-hint { margin: -8px 0 16px 140px; max-width: calc(100% - 140px); }
.block-hint ul { margin: 8px 0 0; padding-left: 18px; }
.block-hint li { margin: 4px 0; }
.doc-label { margin: 16px 0 8px; font-weight: 600; font-size: 13px; }
.doc-sql pre { background: #fafafa; border: 1px solid #e5e5e5; padding: 12px; border-radius: 6px; font-size: 12px; white-space: pre-wrap; }
</style>

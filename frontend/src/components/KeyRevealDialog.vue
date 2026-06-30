<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-alert type="error" :closable="false" show-icon :title="warningTitle" class="key-alert" />
    <el-input class="key-box mono-input" :model-value="apiKey" readonly>
      <template #append><el-button @click="copy">{{ copyLabel }}</el-button></template>
    </el-input>
    <template #footer>
      <el-button type="primary" @click="$emit('update:modelValue', false)">{{ closeLabel }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { copyToClipboard } from '../utils/clipboard'

const props = defineProps<{
  modelValue: boolean
  apiKey: string
  title: string
  warningTitle: string
  copyLabel: string
  closeLabel: string
  copiedMessage?: string
}>()

defineEmits<{ 'update:modelValue': [boolean] }>()

async function copy() {
  await copyToClipboard(props.apiKey, props.copiedMessage || 'OK')
}
</script>

<style scoped>
.key-box { margin-top: 12px; }
.key-alert { margin-bottom: 12px; }
.mono-input :deep(.el-input__inner) { font-family: ui-monospace, monospace; }
</style>

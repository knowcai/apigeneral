import { ElMessage } from 'element-plus'

export async function copyToClipboard(text: string, successMessage = 'OK'): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(successMessage)
    return true
  } catch {
    ElMessage.error('Copy failed')
    return false
  }
}

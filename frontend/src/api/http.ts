import axios, { type AxiosRequestConfig } from 'axios'
import { auth } from '../stores/auth'
import { i18n } from '../locales'

const instance = axios.create({ baseURL: '', withCredentials: true })

instance.interceptors.request.use((config) => {
  const token = auth.getToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function t(key: string, fallback: string) {
  const translated = i18n.global.t(key)
  return translated === key ? fallback : translated
}

instance.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && body.code === 202) {
      return { __approval: true, message: body.message || t('approval.submitted', 'Submitted for approval') }
    }
    if (body && body.code !== 0) {
      return Promise.reject(new Error(body.message || t('common.operationFailed', 'Request failed')))
    }
    return body.data
  },
  async (err) => {
    const status = err.response?.status
    const body = err.response?.data
    if (status === 401) {
      await auth.clear()
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    }
    const message = body?.message || err.message || t('common.operationFailed', 'Request failed')
    return Promise.reject(new Error(message))
  }
)

const http = {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.get(url, config) as Promise<T>
  },
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.post(url, data, config) as Promise<T>
  },
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.put(url, data, config) as Promise<T>
  },
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.delete(url, config) as Promise<T>
  },
  async download(url: string, filename: string): Promise<void> {
    const token = auth.getToken()
    const res = await fetch(url, {
      credentials: 'include',
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    if (!res.ok) {
      let message = t('common.operationFailed', 'Request failed')
      try {
        const body = await res.json()
        if (body?.message) message = body.message
      } catch {
        // ignore non-json body
      }
      throw new Error(message)
    }
    const blob = await res.blob()
    const objectUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = objectUrl
    a.download = filename
    a.click()
    URL.revokeObjectURL(objectUrl)
  }
}

export default http

export function isApprovalResult(result: unknown): result is { __approval: true; message: string } {
  return !!result && typeof result === 'object' && '__approval' in result
}

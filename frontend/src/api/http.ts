import axios, { type AxiosRequestConfig } from 'axios'
import { auth } from '../stores/auth'

const instance = axios.create({ baseURL: '', withCredentials: true })

instance.interceptors.request.use((config) => {
  const token = auth.getToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && body.code === 202) {
      return { __approval: true, message: body.message || '已提交审批' }
    }
    if (body && body.code !== 0) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  },
  (err) => {
    const status = err.response?.status
    const body = err.response?.data
    if (status === 401) {
      auth.clear()
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    }
    const message = body?.message || err.message || '请求失败'
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
  }
}

export default http

export function isApprovalResult(result: unknown): result is { __approval: true; message: string } {
  return !!result && typeof result === 'object' && '__approval' in result
}

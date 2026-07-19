import axios from 'axios'

export class ApiClientError extends Error {
  constructor(message, code = 'NETWORK_ERROR', requestId = '') {
    super(message)
    this.name = 'ApiClientError'
    this.code = code
    this.requestId = requestId
  }
}

export const http = axios.create({
  baseURL: '/api',
  timeout: 90_000,
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const body = error.response?.data
    if (body?.code) {
      return Promise.reject(new ApiClientError(
        body.message || '请求未能完成',
        body.code,
        body.requestId || error.response?.headers?.['x-request-id'] || '',
      ))
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new ApiClientError('请求等待时间过长，请稍后重试', 'UPSTREAM_TIMEOUT'))
    }
    return Promise.reject(new ApiClientError('暂时无法连接服务，请检查网络后重试'))
  },
)


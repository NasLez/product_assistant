import axios from 'axios'

const authenticationProblemListeners = new Set()

const statusCodes = {
  401: 'AUTHENTICATION_REQUIRED',
  403: 'ACCESS_DENIED',
  429: 'AUTH_RATE_LIMITED',
}

const statusMessages = {
  401: '登录状态已失效，请重新登录',
  403: '安全校验未通过，请刷新页面后重试',
  429: '请求过于频繁，请稍后再试',
}

export class ApiClientError extends Error {
  constructor(message, code = 'NETWORK_ERROR', requestId = '', options = {}) {
    super(message)
    this.name = 'ApiClientError'
    this.code = code
    this.requestId = requestId
    this.status = options.status || 0
    this.definitive = Boolean(options.definitive)
  }
}

export function onAuthenticationProblem(listener) {
  authenticationProblemListeners.add(listener)
  return () => authenticationProblemListeners.delete(listener)
}

function notifyAuthenticationProblem(problem) {
  authenticationProblemListeners.forEach((listener) => listener(problem))
}

export const http = axios.create({
  baseURL: '/api',
  timeout: 90_000,
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status || 0
    const body = error.response?.data
    const code = body?.code || statusCodes[status]
    const requestId = body?.requestId || error.response?.headers?.['x-request-id'] || ''

    if ((status === 401 || status === 403) && !error.config?.skipAuthNotification) {
      notifyAuthenticationProblem({ status, code, requestId })
    }

    if (code) {
      return Promise.reject(new ApiClientError(
        body?.message || statusMessages[status] || '请求未能完成',
        code,
        requestId,
        { status, definitive: true },
      ))
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new ApiClientError(
        '请求等待时间过长，请稍后重试',
        'UPSTREAM_TIMEOUT',
        requestId,
      ))
    }
    return Promise.reject(new ApiClientError(
      '暂时无法连接服务，请检查网络后重试',
      'NETWORK_ERROR',
      requestId,
    ))
  },
)

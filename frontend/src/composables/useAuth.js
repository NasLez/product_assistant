import { computed, ref } from 'vue'
import { fetchUserSession, loginUser, logoutUser, registerUser } from '../api/auth'
import { ApiClientError, onAuthenticationProblem } from '../api/http'
import { createAnonymousSession, normalizeUserSession } from '../model/userSession'

const phase = ref('idle')
const session = ref(createAnonymousSession())
const error = ref(null)
const isSubmitting = ref(false)
let initializationPromise = null

const isReady = computed(() => phase.value === 'ready')
const isAuthenticated = computed(() => session.value.authenticated)
const remainingPoints = computed(() => session.value.points)

function asClientError(cause, fallbackMessage) {
  return cause instanceof ApiClientError
    ? cause
    : new ApiClientError(cause?.message || fallbackMessage)
}

function applySession(value) {
  session.value = normalizeUserSession(value)
  phase.value = 'ready'
  return session.value
}

async function refreshSession({ background = false } = {}) {
  if (!background) phase.value = 'loading'
  try {
    return applySession(await fetchUserSession())
  } catch (cause) {
    if (!background) {
      error.value = asClientError(cause, '无法确认登录状态')
      session.value = createAnonymousSession()
      phase.value = 'ready'
    }
    throw cause
  }
}

function initialize() {
  if (initializationPromise) return initializationPromise
  error.value = null
  initializationPromise = refreshSession()
    .catch(() => session.value)
    .finally(() => {
      initializationPromise = null
    })
  return initializationPromise
}

async function authenticate(command, credentials, fallbackMessage) {
  if (isSubmitting.value) return session.value

  isSubmitting.value = true
  error.value = null
  try {
    const commandSession = normalizeUserSession(await command(credentials.email, credentials.password))
    if (commandSession.authenticated) session.value = commandSession
    return await refreshSession({ background: true })
  } catch (cause) {
    const normalized = asClientError(cause, fallbackMessage)
    error.value = normalized
    throw normalized
  } finally {
    phase.value = 'ready'
    isSubmitting.value = false
  }
}

function login(credentials) {
  return authenticate(loginUser, credentials, '登录失败，请稍后重试')
}

function register(credentials) {
  return authenticate(registerUser, credentials, '注册失败，请稍后重试')
}

async function logout() {
  if (isSubmitting.value) return

  isSubmitting.value = true
  error.value = null
  try {
    await logoutUser()
    session.value = createAnonymousSession()
    await refreshSession({ background: true })
  } catch (cause) {
    const normalized = asClientError(cause, '退出失败，请稍后重试')
    error.value = normalized
    throw normalized
  } finally {
    phase.value = 'ready'
    isSubmitting.value = false
  }
}

function clearError() {
  error.value = null
}

function updateRemainingPoints(points) {
  if (points == null) return
  const next = Number(points)
  if (!session.value.authenticated || !Number.isFinite(next) || next < 0) return
  session.value = { ...session.value, points: Math.trunc(next) }
}

const stopAuthenticationProblemListener = onAuthenticationProblem(({ status }) => {
  if (status === 401) {
    session.value = createAnonymousSession()
    phase.value = 'ready'
    return
  }
  if (status === 403) {
    void refreshSession({ background: true }).catch(() => {})
  }
})

if (import.meta.hot) {
  import.meta.hot.dispose(stopAuthenticationProblemListener)
}

export function useAuth() {
  return {
    phase,
    session,
    error,
    isReady,
    isAuthenticated,
    isSubmitting,
    remainingPoints,
    initialize,
    refreshSession,
    login,
    register,
    logout,
    clearError,
    updateRemainingPoints,
  }
}

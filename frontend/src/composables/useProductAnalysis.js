import { computed, ref } from 'vue'
import { createProductAnalysis } from '../api/productAnalysis'
import { ApiClientError } from '../api/http'
import { useAuth } from './useAuth'

const retryWithSameKeyCodes = new Set(['REQUEST_IN_PROGRESS', 'INTERNAL_ERROR'])

export function useProductAnalysis() {
  const status = ref('idle')
  const result = ref(null)
  const error = ref(null)
  const { remainingPoints, updateRemainingPoints } = useAuth()

  let idempotencyKey = ''
  let idempotencyUrl = ''

  const isLoading = computed(() => status.value === 'loading')
  const hasResult = computed(() => status.value === 'success' && Boolean(result.value))

  function ensureIdempotencyKey(amazonUrl) {
    if (!idempotencyKey || idempotencyUrl !== amazonUrl) {
      idempotencyKey = globalThis.crypto.randomUUID()
      idempotencyUrl = amazonUrl
    }
    return idempotencyKey
  }

  function clearIdempotencyKey() {
    idempotencyKey = ''
    idempotencyUrl = ''
  }

  async function submit(amazonUrl) {
    if (isLoading.value) return

    if (remainingPoints.value <= 0) {
      error.value = new ApiClientError(
        '剩余积分不足',
        'INSUFFICIENT_POINTS',
        '',
        { status: 402, definitive: true },
      )
      status.value = 'error'
      return
    }

    const normalizedUrl = amazonUrl.trim()
    status.value = 'loading'
    error.value = null
    try {
      const submission = await createProductAnalysis(normalizedUrl, ensureIdempotencyKey(normalizedUrl))
      result.value = submission.result
      updateRemainingPoints(submission.remainingPoints)
      clearIdempotencyKey()
      status.value = 'success'
    } catch (cause) {
      const normalized = cause instanceof ApiClientError
        ? cause
        : new ApiClientError(cause?.message || '分析失败，请稍后重试')
      error.value = normalized
      if (normalized.definitive && !retryWithSameKeyCodes.has(normalized.code)) {
        clearIdempotencyKey()
      }
      status.value = 'error'
    }
  }

  function invalidateSubmission() {
    if (!isLoading.value) clearIdempotencyKey()
  }

  function resetError() {
    if (status.value === 'error') status.value = result.value ? 'success' : 'idle'
    error.value = null
  }

  function reset() {
    status.value = 'idle'
    result.value = null
    error.value = null
    clearIdempotencyKey()
  }

  return {
    status,
    result,
    error,
    isLoading,
    hasResult,
    submit,
    invalidateSubmission,
    resetError,
    reset,
  }
}

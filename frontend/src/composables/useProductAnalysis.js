import { computed, ref } from 'vue'
import { createProductAnalysis } from '../api/productAnalysis'
import { ApiClientError } from '../api/http'

export function useProductAnalysis() {
  const status = ref('idle')
  const result = ref(null)
  const error = ref(null)

  const isLoading = computed(() => status.value === 'loading')
  const hasResult = computed(() => status.value === 'success' && result.value)

  async function submit(amazonUrl) {
    if (isLoading.value) return

    status.value = 'loading'
    error.value = null
    try {
      result.value = await createProductAnalysis(amazonUrl)
      status.value = 'success'
    } catch (cause) {
      error.value = cause instanceof ApiClientError
        ? cause
        : new ApiClientError(cause?.message || '分析失败，请稍后重试')
      status.value = 'error'
    }
  }

  function resetError() {
    if (status.value === 'error') status.value = result.value ? 'success' : 'idle'
    error.value = null
  }

  return { status, result, error, isLoading, hasResult, submit, resetError }
}


import { http } from './http'
import { normalizeAnalysisSubmission, normalizeProductAnalysis } from '../model/productAnalysis'

export async function createProductAnalysis(amazonUrl, idempotencyKey) {
  const response = await http.post(
    '/v1/product-analyses',
    { amazonUrl },
    { headers: { 'X-Idempotency-Key': idempotencyKey } },
  )
  return normalizeAnalysisSubmission(response.data?.data)
}

export async function getProductAnalysis(id) {
  const response = await http.get(`/v1/product-analyses/${id}`)
  return normalizeProductAnalysis(response.data?.data)
}

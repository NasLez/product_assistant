import { http } from './http'
import { normalizeProductAnalysis } from '../model/productAnalysis'

export async function createProductAnalysis(amazonUrl) {
  const response = await http.post('/v1/product-analyses', { amazonUrl })
  return normalizeProductAnalysis(response.data?.data)
}

export async function getProductAnalysis(id) {
  const response = await http.get(`/v1/product-analyses/${id}`)
  return normalizeProductAnalysis(response.data?.data)
}


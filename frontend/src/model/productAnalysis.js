const asArray = (value) => Array.isArray(value) ? value : []

export function normalizeProductAnalysis(value) {
  if (!value || typeof value !== 'object') {
    throw new Error('服务返回了无法识别的分析结果')
  }

  const product = value.product || {}
  const analysis = value.analysis || {}
  return {
    id: value.id,
    source: value.source || 'LIVE',
    product: {
      amazonDomain: product.amazonDomain || '',
      asin: product.asin || '',
      title: product.title || '未命名商品',
      brand: product.brand || '',
      categoryPath: product.categoryPath || '',
      price: product.price || null,
      mainImageUrl: product.mainImageUrl || '',
      features: asArray(product.features),
      specifications: asArray(product.specifications),
    },
    analysis: {
      targetUsers: asArray(analysis.targetUsers),
      useCases: asArray(analysis.useCases),
      painPoints: asArray(analysis.painPoints),
      coreSellingPoints: asArray(analysis.coreSellingPoints),
      videoScript: analysis.videoScript || '',
    },
  }
}


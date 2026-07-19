<script setup>
defineProps({ product: { type: Object, required: true } })

function priceText(price) {
  if (!price) return '暂无价格'
  if (price.display) return price.display
  if (price.amount == null) return '暂无价格'
  return [price.currency, price.amount].filter(Boolean).join(' ')
}
</script>

<template>
  <section class="product-summary dossier-card">
    <div class="section-kicker">Product dossier</div>
    <div class="product-summary__grid">
      <div class="product-summary__visual">
        <img v-if="product.mainImageUrl" :src="product.mainImageUrl" :alt="product.title" referrerpolicy="no-referrer" />
        <div v-else class="product-summary__placeholder">NO<br />IMAGE</div>
        <span class="product-summary__asin">ASIN / {{ product.asin || '暂无' }}</span>
      </div>

      <div class="product-summary__content">
        <div class="product-summary__meta">
          <span>{{ product.brand || '品牌暂无' }}</span>
          <span>{{ product.amazonDomain || 'Amazon' }}</span>
        </div>
        <h2>{{ product.title }}</h2>
        <div class="product-summary__facts">
          <div>
            <small>当前价格</small>
            <strong>{{ priceText(product.price) }}</strong>
          </div>
          <div>
            <small>商品品类</small>
            <strong>{{ product.categoryPath || '暂无品类信息' }}</strong>
          </div>
        </div>
      </div>
    </div>

    <div class="product-summary__details">
      <div>
        <h3>核心功能</h3>
        <ul v-if="product.features.length" class="fact-list">
          <li v-for="feature in product.features" :key="feature">{{ feature }}</li>
        </ul>
        <p v-else class="empty-copy">商品页面暂未提供核心功能。</p>
      </div>
      <div>
        <h3>关键规格</h3>
        <dl v-if="product.specifications.length" class="spec-list">
          <template v-for="item in product.specifications" :key="`${item.name}-${item.value}`">
            <dt>{{ item.name }}</dt>
            <dd>{{ item.value }}</dd>
          </template>
        </dl>
        <p v-else class="empty-copy">商品页面暂未提供规格。</p>
      </div>
    </div>
  </section>
</template>


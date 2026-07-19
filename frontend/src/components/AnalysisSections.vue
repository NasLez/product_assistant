<script setup>
defineProps({ analysis: { type: Object, required: true } })
</script>

<template>
  <section class="insight-section">
    <header class="section-heading">
      <div>
        <span class="section-kicker">Strategic read</span>
        <h2>从参数走向真实使用</h2>
      </div>
      <p>以下判断由商品事实推导，卖点保留对应依据。</p>
    </header>

    <div class="insight-grid">
      <article class="insight-card insight-card--users">
        <span class="insight-card__number">01</span>
        <h3>目标用户</h3>
        <ul><li v-for="item in analysis.targetUsers" :key="item">{{ item }}</li></ul>
      </article>
      <article class="insight-card insight-card--scenes">
        <span class="insight-card__number">02</span>
        <h3>使用场景</h3>
        <ul><li v-for="item in analysis.useCases" :key="item">{{ item }}</li></ul>
      </article>
      <article class="insight-card insight-card--pain">
        <span class="insight-card__number">03</span>
        <h3>用户痛点</h3>
        <ul><li v-for="item in analysis.painPoints" :key="item">{{ item }}</li></ul>
      </article>
    </div>

    <div class="selling-points dossier-card">
      <div class="selling-points__title">
        <span class="section-kicker">Evidence-backed</span>
        <h3>核心卖点与依据</h3>
      </div>
      <div class="selling-points__list">
        <article v-for="(point, index) in analysis.coreSellingPoints" :key="`${point.claim}-${index}`">
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          <div>
            <h4>{{ point.claim }}</h4>
            <p><b>依据</b>{{ point.evidence }}</p>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>


<script setup>
const steps = [
  { index: '01', title: '识别商品', detail: '解析站点与 ASIN' },
  { index: '02', title: '整理事实', detail: '读取结构化商品数据' },
  { index: '03', title: '生成洞察', detail: '提炼用户、场景与文案' },
]
</script>

<template>
  <section class="loading-steps" aria-live="polite" aria-label="分析处理中">
    <div class="loading-steps__beam"></div>
    <div v-for="(step, index) in steps" :key="step.index" class="loading-step" :style="{ '--delay': `${index * 0.65}s` }">
      <span class="loading-step__index">{{ step.index }}</span>
      <div>
        <strong>{{ step.title }}</strong>
        <p>{{ step.detail }}</p>
      </div>
      <span class="loading-step__pulse"></span>
    </div>
    <p class="loading-steps__note">外部数据与 AI 分析通常需要数秒，请保持页面打开。</p>
  </section>
</template>


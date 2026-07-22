<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import AnalysisForm from './components/AnalysisForm.vue'
import LoadingSteps from './components/LoadingSteps.vue'
import ProductSummary from './components/ProductSummary.vue'
import AnalysisSections from './components/AnalysisSections.vue'
import VideoScriptCard from './components/VideoScriptCard.vue'
import ErrorPanel from './components/ErrorPanel.vue'
import AuthGate from './components/AuthGate.vue'
import UserToolbar from './components/UserToolbar.vue'
import { useAuth } from './composables/useAuth'
import { useProductAnalysis } from './composables/useProductAnalysis'

const amazonUrl = ref('')

const {
  session,
  error: accountError,
  isReady,
  isAuthenticated,
  isSubmitting: isAccountSubmitting,
  remainingPoints,
  initialize,
  logout,
  clearError: clearAccountError,
} = useAuth()

const {
  result,
  error: analysisError,
  isLoading,
  hasResult,
  submit,
  invalidateSubmission,
  resetError: resetAnalysisError,
  reset: resetAnalysis,
} = useProductAnalysis()

const visibleError = computed(() => analysisError.value || (isAuthenticated.value ? accountError.value : null))

onMounted(() => {
  void initialize()
})

watch(amazonUrl, () => invalidateSubmission())
watch(isAuthenticated, (authenticated) => {
  if (!authenticated) {
    amazonUrl.value = ''
    resetAnalysis()
  }
})

function runAnalysis() {
  submit(amazonUrl.value.trim())
}

function dismissError() {
  if (analysisError.value) resetAnalysisError()
  else clearAccountError()
}

async function handleLogout() {
  try {
    await logout()
    amazonUrl.value = ''
    resetAnalysis()
  } catch {
    // useAuth 已统一转换并展示退出错误。
  }
}
</script>

<template>
  <div class="app-shell">
    <div class="paper-grid"></div>
    <header class="topbar">
      <a class="brand" href="#top" aria-label="商品解构室首页">
        <span class="brand__seal">拆</span>
        <span><b>商品解构室</b><small>PRODUCT INTELLIGENCE DESK</small></span>
      </a>
      <UserToolbar
        v-if="isReady && isAuthenticated"
        :email="session.email"
        :points="remainingPoints"
        :loading="isAccountSubmitting"
        @logout="handleLogout"
      />
      <span v-else class="topbar__edition">EDITION / 2026</span>
    </header>

    <main id="top">
      <section class="hero" :class="{ 'hero--auth': isReady && !isAuthenticated }">
        <div class="hero__eyebrow"><span></span> AI PRODUCT ANALYSIS ASSISTANT</div>
        <h1>别只看参数，<br /><em>看懂一个产品。</em></h1>
        <p class="hero__lead">粘贴 Amazon 商品链接，把凌乱的页面信息整理成商品档案、真实使用洞察和一段能直接开口的短视频文案。</p>

        <div v-if="!isReady" class="session-loading" role="status" aria-live="polite">
          <span></span>
          <strong>正在确认安全会话</strong>
        </div>

        <AuthGate v-else-if="!isAuthenticated" />

        <template v-else>
          <div class="hero__form-card">
            <AnalysisForm
              v-model="amazonUrl"
              :loading="isLoading"
              :remaining-points="remainingPoints"
              @submit="runAnalysis"
            />
          </div>
          <div class="hero__footnote">
            <span>01 / 商品事实</span><span>02 / 用户洞察</span><span>03 / 口播文案</span>
          </div>
        </template>
      </section>

      <template v-if="isAuthenticated">
        <ErrorPanel v-if="visibleError" :error="visibleError" @dismiss="dismissError" />
        <LoadingSteps v-if="isLoading" />

        <div v-if="hasResult" class="result-stack">
          <div class="result-ribbon">
            <span>ANALYSIS COMPLETE</span>
            <span>来源 / {{ result.source }}</span>
            <span>记录 / #{{ result.id }}</span>
          </div>
          <ProductSummary :product="result.product" />
          <AnalysisSections :analysis="result.analysis" />
          <VideoScriptCard :script="result.analysis.videoScript" />
        </div>
      </template>
    </main>

    <footer class="footer">
      <span>商品解构室</span>
      <p>结论由公开商品数据与 AI 生成，请以实际商品页面为准。</p>
    </footer>
  </div>
</template>

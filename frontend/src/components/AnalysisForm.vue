<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  remainingPoints: { type: Number, default: 0 },
})

const emit = defineEmits(['update:modelValue', 'submit'])
const touched = ref(false)

const value = computed({
  get: () => props.modelValue,
  set: (next) => emit('update:modelValue', next),
})

const hasPoints = computed(() => props.remainingPoints > 0)

const validationMessage = computed(() => {
  if (!touched.value) return ''
  if (!value.value.trim()) return '请粘贴一个 Amazon 商品链接'
  if (!value.value.trim().startsWith('https://')) return '链接需要以 https:// 开头'
  return ''
})

function submit() {
  touched.value = true
  if (!validationMessage.value && !props.loading && hasPoints.value) emit('submit')
}
</script>

<template>
  <form class="analysis-form" @submit.prevent="submit">
    <div class="analysis-form__label-row">
      <label for="amazon-url">Amazon 商品链接</label>
      <span>每次成功分析消耗 1 积分 · 当前 {{ remainingPoints }} 积分</span>
    </div>
    <div class="analysis-form__control" :class="{ 'is-error': validationMessage }">
      <el-input
        id="amazon-url"
        v-model="value"
        size="large"
        clearable
        :disabled="loading"
        placeholder="https://www.amazon.com/dp/B073JYC4XM"
        aria-label="Amazon 商品链接"
        @blur="touched = true"
      />
      <el-button
        class="analysis-form__button"
        type="primary"
        size="large"
        native-type="submit"
        :loading="loading"
        :disabled="!hasPoints"
      >
        {{ loading ? '正在解构' : hasPoints ? '开始解构 · 1 积分' : '积分不足' }}
      </el-button>
    </div>
    <p v-if="validationMessage" class="analysis-form__error">{{ validationMessage }}</p>
    <p v-else-if="!hasPoints" class="analysis-form__error">剩余积分为 0，暂时无法提交新的商品分析。</p>
    <p v-else class="analysis-form__hint">不会直接抓取你提交的页面，链接只用于识别站点与 ASIN。</p>

    <section class="analysis-form__support" aria-label="支持的 Amazon 商品链接格式">
      <div class="analysis-form__support-heading">
        <strong>支持的链接格式</strong>
        <span>仅支持 HTTPS；ASIN 必须是 10 位字母或数字；www. 可以省略</span>
      </div>

      <div class="analysis-form__support-grid">
        <div>
          <b>商品路径</b>
          <div class="analysis-form__formats">
            <code>/dp/B073JYC4XM</code>
            <code>/gp/product/B073JYC4XM</code>
            <code>/product/B073JYC4XM</code>
          </div>
        </div>
        <div>
          <b>Amazon 站点</b>
          <p>
            amazon.com、amazon.ca、amazon.com.mx、amazon.com.br、amazon.co.uk、amazon.de、amazon.fr、
            amazon.it、amazon.es、amazon.nl、amazon.se、amazon.pl、amazon.com.be、amazon.co.jp、
            amazon.in、amazon.com.au、amazon.sg、amazon.ae、amazon.sa、amazon.com.tr、amazon.eg
          </p>
        </div>
      </div>

      <p class="analysis-form__support-note">
        支持商品标题出现在 <code>/dp/</code> 前，以及 <code>?th=1&amp;psc=1</code> 等查询参数；
        不支持 amzn.to 短链、HTTP 链接或其他 Amazon 子域名。
      </p>
    </section>
  </form>
</template>

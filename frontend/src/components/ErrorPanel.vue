<script setup>
const props = defineProps({ error: { type: Object, required: true } })
defineEmits(['dismiss'])

const actions = {
  INVALID_AMAZON_URL: '请检查链接是否为 HTTPS Amazon 商品页，并包含 10 位 ASIN。',
  RAINFOREST_BUSY: '当前已有商品请求正在处理，稍等片刻后再试。',
  UPSTREAM_QUOTA_EXCEEDED: '外部服务额度不足，请联系部署者检查账户。',
  UPSTREAM_TIMEOUT: '外部服务响应较慢，稍后重试通常可以恢复。',
  DEEPSEEK_AUTHENTICATION_FAILED: '请检查后端 DEEPSEEK_API_KEY，修改 .env 后需要重新创建后端容器使配置生效。',
  DEEPSEEK_INVALID_REQUEST: '请检查 DEEPSEEK_MODEL 与 DeepSeek 接口配置，当前支持 deepseek-v4-pro 或 deepseek-v4-pro。',
  PRODUCT_DATA_INCOMPLETE: '该商品页面缺少必要信息，可以换一个公开商品链接。',
  NETWORK_ERROR: '检查网络连接或确认服务已经启动。',
}
</script>

<template>
  <section class="error-panel" role="alert">
    <div class="error-panel__mark">!</div>
    <div>
      <span>分析没有完成</span>
      <h3>{{ error.message }}</h3>
      <p>{{ actions[error.code] || '请稍后重试；如果问题持续存在，请将请求编号提供给维护人员。' }}</p>
      <small v-if="error.requestId">请求编号：{{ error.requestId }}</small>
    </div>
    <el-button text @click="$emit('dismiss')">关闭</el-button>
  </section>
</template>

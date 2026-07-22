<script setup>
const props = defineProps({ error: { type: Object, required: true } })
defineEmits(['dismiss'])

const actions = {
  INVALID_IDEMPOTENCY_KEY: '请刷新页面后重新提交，浏览器将生成新的安全请求键。',
  INVALID_REGISTRATION: '请检查邮箱格式，密码需为 12–72 个 UTF-8 字节。',
  AUTHENTICATION_REQUIRED: '登录状态已失效，请重新登录。',
  INVALID_CREDENTIALS: '邮箱或密码错误。',
  INSUFFICIENT_POINTS: '剩余积分为 0，无法发起新的分析。',
  ACCESS_DENIED: '安全校验未通过，页面已尝试刷新会话令牌；请手动重试，本次请求不会自动重放。',
  EMAIL_ALREADY_REGISTERED: '该邮箱已注册，请切换到登录。',
  REQUEST_IN_PROGRESS: '相同请求正在处理，稍候重试会继续使用原请求键，不会重复扣费。',
  REQUEST_ALREADY_FAILED: '该请求已失败并退还积分，请重新提交。',
  AUTH_RATE_LIMITED: '登录或注册过于频繁，请稍后再试。',
  ENCRYPTION_ERROR: '服务端无法安全处理口播文案，本次分析不会扣除积分。',
  INTERNAL_ERROR: '请求结果暂时无法确认；再次提交会复用原请求键，服务端不会重复扣费。',
  INVALID_AMAZON_URL: '请检查链接是否为 HTTPS Amazon 商品页，并包含 10 位 ASIN。',
  RAINFOREST_BUSY: '当前已有商品请求正在处理，稍等片刻后再试。',
  UPSTREAM_QUOTA_EXCEEDED: '外部服务额度不足，请联系部署者检查账户。',
  UPSTREAM_TIMEOUT: '外部服务响应较慢，稍后重试通常可以恢复。',
  DEEPSEEK_AUTHENTICATION_FAILED: '请检查后端 DEEPSEEK_API_KEY，修改 .env 后需要重新创建后端容器使配置生效。',
  DEEPSEEK_INVALID_REQUEST: '请检查后端 DEEPSEEK_MODEL 与 DeepSeek 接口配置。',
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

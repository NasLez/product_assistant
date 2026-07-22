<script setup>
import { computed, ref } from 'vue'
import { useAuth } from '../composables/useAuth'

const { error, isSubmitting, isAuthenticated, login, register, clearError } = useAuth()

const mode = ref('login')
const email = ref('')
const password = ref('')
const touched = ref(false)
const notice = ref('')

const normalizedEmail = computed(() => email.value.normalize('NFKC').trim().toLocaleLowerCase('en-US'))
const passwordBytes = computed(() => new TextEncoder().encode(password.value).length)

const validationMessage = computed(() => {
  if (!touched.value) return ''
  if (!normalizedEmail.value) return '请输入邮箱'
  if (normalizedEmail.value.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/u.test(normalizedEmail.value)) {
    return '请输入有效的邮箱地址'
  }
  if (passwordBytes.value < 12 || passwordBytes.value > 72) {
    return '密码需为 12–72 个 UTF-8 字节'
  }
  return ''
})

const errorMessage = computed(() => {
  if (!error.value) return ''
  const messages = {
    INVALID_CREDENTIALS: '邮箱或密码错误',
    INVALID_REGISTRATION: '邮箱或密码格式不符合要求',
    EMAIL_ALREADY_REGISTERED: '该邮箱已注册，请直接登录',
    AUTH_RATE_LIMITED: '尝试次数过多，请稍后再试',
    ACCESS_DENIED: '安全校验未通过，请刷新页面后再试',
  }
  return messages[error.value.code] || error.value.message
})

function handleFieldInput() {
  notice.value = ''
  if (error.value) clearError()
}

function switchMode(nextMode) {
  if (isSubmitting.value) return
  mode.value = nextMode
  password.value = ''
  touched.value = false
  notice.value = ''
  clearError()
}

async function submit() {
  touched.value = true
  notice.value = ''
  if (validationMessage.value || isSubmitting.value) return

  const credentials = {
    email: normalizedEmail.value,
    password: password.value,
  }

  try {
    if (mode.value === 'login') {
      await login(credentials)
    } else {
      await register(credentials)
      if (!isAuthenticated.value) {
        mode.value = 'login'
        notice.value = '注册成功，请使用新账号登录'
      }
    }
  } catch {
    // 通用错误由 useAuth 统一转换，表单不保留异常详情。
  } finally {
    password.value = ''
  }
}
</script>

<template>
  <section class="auth-gate" aria-labelledby="auth-title">
    <div class="auth-gate__intro">
      <span class="section-kicker">SECURE ACCESS</span>
      <h2 id="auth-title">{{ mode === 'login' ? '登录后开始分析' : '创建你的分析账号' }}</h2>
      <p>
        {{ mode === 'login'
          ? '会话使用安全 Cookie 保护，密码不会由页面保存。'
          : '注册即赠 10 积分；每次成功分析消耗 1 积分。' }}
      </p>
    </div>

    <div class="auth-gate__card">
      <div class="auth-gate__tabs" role="tablist" aria-label="账号入口">
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'login'"
          :class="{ 'is-active': mode === 'login' }"
          :disabled="isSubmitting"
          @click="switchMode('login')"
        >
          登录
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'register'"
          :class="{ 'is-active': mode === 'register' }"
          :disabled="isSubmitting"
          @click="switchMode('register')"
        >
          注册
        </button>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <label for="auth-email">邮箱</label>
        <el-input
          id="auth-email"
          v-model="email"
          type="email"
          autocomplete="email"
          maxlength="254"
          :disabled="isSubmitting"
          placeholder="name@example.com"
          @input="handleFieldInput"
          @blur="touched = true"
        />

        <label for="auth-password">密码</label>
        <el-input
          id="auth-password"
          v-model="password"
          type="password"
          show-password
          :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
          :disabled="isSubmitting"
          placeholder="12–72 个 UTF-8 字节"
          @input="handleFieldInput"
          @blur="touched = true"
        />

        <p v-if="validationMessage" class="auth-form__message is-error">{{ validationMessage }}</p>
        <p v-else-if="errorMessage" class="auth-form__message is-error" role="alert">
          {{ errorMessage }}
          <small v-if="error?.requestId">请求编号：{{ error.requestId }}</small>
        </p>
        <p v-else-if="notice" class="auth-form__message is-success" role="status">{{ notice }}</p>
        <p v-else class="auth-form__message">服务端仅保存 BCrypt 密码哈希。</p>

        <el-button
          class="auth-form__submit"
          type="primary"
          native-type="submit"
          :loading="isSubmitting"
        >
          {{ mode === 'login' ? '安全登录' : '注册并领取 10 积分' }}
        </el-button>
      </form>
    </div>
  </section>
</template>

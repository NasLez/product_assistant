<script setup>
import { computed } from 'vue'
import PointsBadge from './PointsBadge.vue'
import { maskEmail } from '../model/userSession'

const props = defineProps({
  email: { type: String, default: '' },
  points: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
})

defineEmits(['logout'])

const maskedEmail = computed(() => maskEmail(props.email))
</script>

<template>
  <div class="user-toolbar" aria-label="用户信息">
    <span class="user-toolbar__identity" :title="maskedEmail">
      <small>ACCOUNT</small>
      <strong>{{ maskedEmail }}</strong>
    </span>
    <PointsBadge :points="points" />
    <el-button
      class="user-toolbar__logout"
      text
      :loading="loading"
      @click="$emit('logout')"
    >
      退出
    </el-button>
  </div>
</template>

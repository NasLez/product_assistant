<script setup>
import { computed } from 'vue'

const props = defineProps({
  points: { type: Number, default: 0 },
})

const safePoints = computed(() => Math.max(0, Math.trunc(Number(props.points) || 0)))
</script>

<template>
  <span
    class="points-badge"
    :class="{ 'points-badge--empty': safePoints === 0 }"
    :aria-label="`剩余 ${safePoints} 积分`"
  >
    <small>剩余积分</small>
    <strong>{{ safePoints }}</strong>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({ script: { type: String, default: '' } })
const characterCount = computed(() => Array.from(props.script).length)

async function copyScript() {
  try {
    await navigator.clipboard.writeText(props.script)
    ElMessage.success('口播文案已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择文案')
  }
}
</script>

<template>
  <section class="script-card">
    <div class="script-card__header">
      <div>
        <span class="section-kicker">Short-form script</span>
        <h2>一段可以直接开口的文案</h2>
      </div>
      <span class="script-card__count" :class="{ 'is-over': characterCount > 150 }">{{ characterCount }} / 150</span>
    </div>
    <blockquote>{{ script || '暂无口播文案' }}</blockquote>
    <div class="script-card__footer">
      <span>开头钩子 · 中文口播 · 商品事实约束</span>
      <el-button type="primary" plain :disabled="!script" @click="copyScript">复制文案</el-button>
    </div>
  </section>
</template>


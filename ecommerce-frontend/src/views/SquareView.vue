<template>
  <AppLayout>
    <div class="sq-page">
      <h2 class="sq-title">广场</h2>

      <!-- 发布框 -->
      <div class="sq-publish">
        <el-input
          v-model="draft"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="分享点什么吧..."
          class="sq-input"
        />
        <div class="sq-publish-actions">
          <el-button type="primary" class="sq-submit" :loading="publishing" :disabled="!draft.trim()" @click="onPublish">
            发布
          </el-button>
        </div>
      </div>

      <!-- 动态列表 -->
      <div v-if="loading" class="sq-loading">加载中...</div>
      <el-empty v-else-if="posts.length === 0" description="还没有动态，来发第一条吧" />
      <div v-else class="sq-list">
        <div v-for="p in posts" :key="p.id" class="sq-card">
          <div class="sq-card-header">
            <span class="sq-avatar">{{ p.username.charAt(0) }}</span>
            <div class="sq-meta">
              <p class="sq-username">{{ p.username }}</p>
              <p class="sq-time">{{ p.createTime }}</p>
            </div>
          </div>
          <p class="sq-content">{{ p.content }}</p>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import AppLayout from '@/layouts/AppLayout.vue'
import { publishPost, getFeed } from '@/api/feed'
import type { FeedPost } from '@/api/feed'

const draft = ref('')
const publishing = ref(false)
const loading = ref(true)
const posts = ref<FeedPost[]>([])

async function fetchPosts() {
  loading.value = true
  try {
    posts.value = await getFeed(1, 50)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '动态加载失败')
  } finally {
    loading.value = false
  }
}

async function onPublish() {
  publishing.value = true
  try {
    await publishPost(draft.value)
    ElMessage.success('发布成功')
    draft.value = ''
    await fetchPosts()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '发布失败')
  } finally {
    publishing.value = false
  }
}

onMounted(fetchPosts)
</script>

<style scoped>
.sq-page { width: 100%; max-width: 900px; margin: 0 auto; padding: 40px 24px 88px; }
.sq-title { font-family: var(--font-display); font-size: clamp(1.5rem, 3vw, 2.25rem); font-weight: 400; line-height: 1.5; color: var(--color-text-primary); margin: 0 0 24px; }
.sq-publish { background: rgba(255,255,255,0.6); border: 1px solid var(--color-border); border-radius: 16px; padding: 16px; margin-bottom: 20px; backdrop-filter: blur(8px); }
.sq-input :deep(.el-textarea__inner) { background: #fff; border-radius: 8px; font-family: 'Inter', sans-serif; }
.sq-publish-actions { display: flex; justify-content: flex-end; margin-top: 12px; }
.sq-submit { --el-button-bg-color: var(--color-accent); --el-button-border-color: var(--color-accent); border-radius: 8px; }
.sq-loading { text-align: center; padding: 60px 0; color: var(--color-text-secondary); }
.sq-list { display: flex; flex-direction: column; gap: 12px; }
.sq-card { background: var(--color-bg-raised); border: 1px solid var(--color-border-subtle); border-radius: var(--radius-lg); padding: 24px; }
.sq-card-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.sq-avatar { width: 36px; height: 36px; border-radius: 50%; background: var(--color-accent); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 15px; font-weight: 600; flex-shrink: 0; }
.sq-meta { min-width: 0; }
.sq-username { font-size: 13px; font-weight: 600; color: var(--color-text-primary); margin: 0; }
.sq-time { font-size: 11px; color: var(--color-text-muted); margin: 2px 0 0; }
.sq-content { font-size: 14px; color: var(--color-text-primary); line-height: 1.6; margin: 0; white-space: pre-wrap; word-break: break-word; }
</style>

<template>
  <!-- 品类筛选：胶囊 chip，和区块标题同排（跟随设计 demo，不再是吸顶条） -->
  <div class="filters" role="group" aria-label="按分类筛选">
    <button
      v-for="cat in categories"
      :key="cat.id ?? 'all'"
      type="button"
      class="chip"
      :aria-pressed="selectedId === cat.id"
      @click="select(cat.id)"
    >
      {{ cat.name }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { getCategories, type Category } from '@/api/product'

const props = defineProps<{
  modelValue: number | null
}>()

const emit = defineEmits<{
  change: [categoryId: number | null]
  'update:modelValue': [categoryId: number | null]
}>()

const categories = ref<(Category & { id: number | null })[]>([])
const selectedId = ref<number | null>(props.modelValue ?? null)

// 外部选中值变化（如 URL 参数/路由回退）时同步高亮
watch(() => props.modelValue, (v) => {
  selectedId.value = v
})

onMounted(async () => {
  try {
    const list = await getCategories()
    categories.value = [
      { id: null, name: '全部' } as any,
      ...list,
    ]
  } catch {
    // 接口失败时至少保留「全部」，页面不至于空白
    categories.value = [{ id: null, name: '全部' } as any]
  }
})

function select(id: number | null) {
  selectedId.value = id
  emit('update:modelValue', id)
  emit('change', id)
}
</script>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.chip {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: transparent;
  color: var(--color-text-secondary);
  font-family: var(--font-body);
  font-size: 15px;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.chip:hover {
  border-color: var(--color-border-strong);
  color: var(--color-text-primary);
}

.chip[aria-pressed='true'] {
  background: var(--color-bg-inverted);
  border-color: var(--color-bg-inverted);
  color: var(--color-bg-overlay);
}
</style>

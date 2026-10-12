<template>
  <AppLayout>
    <div class="mp-page">
      <div class="mp-header">
        <h2 class="mp-title">商品管理</h2>
        <el-button type="primary" class="mp-add-btn" @click="openDialog()">+ 新增商品</el-button>
      </div>

      <!-- 商品列表 -->
      <div v-if="loading" class="mp-loading">加载中...</div>
      <el-empty v-else-if="products.length === 0" description="还没有商品，点击右上角新增">
        <el-button type="primary" @click="openDialog()">新增商品</el-button>
      </el-empty>
      <div v-else class="mp-list">
        <div v-for="p in products" :key="p.id" class="mp-card">
          <div class="mp-card-img">
            <img v-if="firstImage(p.images)" :src="firstImage(p.images)" class="mp-img" alt="" loading="lazy" />
            <span v-else>{{ p.name.charAt(0) }}</span>
          </div>
          <div class="mp-card-info">
            <p class="mp-card-name">{{ p.name }}</p>
            <p class="mp-card-price">¥{{ Number(p.price).toFixed(2) }} · 库存 {{ p.stock }}</p>
            <p class="mp-card-status" :class="p.status === 1 ? 'on' : 'off'">
              {{ p.status === 1 ? '已上架' : '已下架' }}
            </p>
          </div>
          <div class="mp-card-actions">
            <el-button v-if="p.status === 1" text size="small" type="warning" @click="onToggleStatus(p, 0)">下架</el-button>
            <el-button v-else text size="small" type="success" @click="onToggleStatus(p, 1)">上架</el-button>
            <el-button text size="small" @click="openDialog(p)">编辑</el-button>
            <el-button text type="danger" size="small" @click="onDelete(p)">删除</el-button>
          </div>
        </div>
      </div>

      <!-- 新增/编辑弹窗 -->
      <el-dialog v-model="showDialog" :title="form.id ? '编辑商品' : '新增商品'" width="90%" max-width="480px">
        <el-form label-position="top">
          <el-form-item label="商品名称" required>
            <el-input v-model="form.name" placeholder="请输入商品名称" />
          </el-form-item>
          <el-form-item label="商品规格 SKU" required>
            <div class="sku-editor">
              <div v-for="(sku, index) in form.skus" :key="index" class="sku-row">
                <el-input v-model="sku.skuName" maxlength="100" placeholder="规格名，如 黑色/M" />
                <el-input-number v-model="sku.price" :min="0.01" :precision="2" :step="1" placeholder="价格" />
                <el-input-number v-model="sku.stock" :min="0" :step="1" placeholder="库存" />
                <el-button
                  text
                  type="danger"
                  :disabled="form.skus.length <= 1"
                  @click="removeSku(index)"
                >删除</el-button>
              </div>
              <el-button text type="primary" @click="addSku">+ 添加规格</el-button>
            </div>
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="form.categoryId" placeholder="选择分类" clearable style="width:100%">
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="商品图片">
            <input
              ref="imageInput"
              class="image-input"
              type="file"
              multiple
              accept="image/jpeg,image/png,image/webp,image/gif"
              @change="onImageSelected"
            />
            <div class="image-editor">
              <div v-for="(url, index) in form.imageUrls" :key="url" class="image-preview-box">
                <img :src="url" alt="商品图片预览" class="image-preview" />
                <span v-if="index === 0" class="main-image-badge">主图</span>
                <button
                  type="button"
                  class="image-remove"
                  title="移除图片"
                  aria-label="移除图片"
                  :disabled="uploadingImage"
                  @click="removeImage(index)"
                >移除</button>
              </div>
              <button
                v-if="form.imageUrls.length < 9"
                type="button"
                class="image-picker"
                :disabled="uploadingImage"
                @click="imageInput?.click()"
              >
                <span class="picker-symbol">{{ uploadingImage ? '…' : '+' }}</span>
                <span>{{ uploadingImage ? '正在上传' : '添加图片' }}</span>
              </button>
            </div>
            <span class="image-hint">最多 9 张，第一张为主图；支持 JPG、PNG、WEBP、GIF，单张最大 5MB</span>
          </el-form-item>
          <el-form-item label="商品描述">
            <el-input v-model="form.description" type="textarea" :rows="3" placeholder="商品描述" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showDialog = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '@/layouts/AppLayout.vue'
import {
  listMerchantProducts,
  addProduct,
  updateProduct,
  deleteProduct,
  updateProductStatus,
  listMerchantCategories,
  uploadProductImage,
  getProductById,
} from '@/api/product'
import type { Product, ProductSku, Category } from '@/api/product'
import { parseProductImages, serializeProductImages, firstProductImage } from '@/utils/productImages'

const loading = ref(true)
const saving = ref(false)
const products = ref<Product[]>([])
const categories = ref<Category[]>([])
const showDialog = ref(false)
const imageInput = ref<HTMLInputElement | null>(null)
const uploadingImage = ref(false)

const emptySku = (): ProductSku => ({ skuName: '默认规格', price: 0, stock: 0 })
const emptyForm = () => ({
  id: 0,
  name: '',
  price: 0,
  stock: 0,
  description: '',
  images: '',
  imageUrls: [] as string[],
  categoryId: null as number | null,
  skus: [emptySku()],
})
const form = reactive(emptyForm())

// 商品图片：单 URL / JSON 数组 / 逗号分隔，取第一张
function firstImage(raw?: string | null): string {
  return firstProductImage(raw)
}

async function fetchProducts() {
  loading.value = true
  try {
    products.value = await listMerchantProducts({ pageNum: 1, pageSize: 100 })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '商品加载失败')
  } finally {
    loading.value = false
  }
}

async function fetchCategories() {
  try {
    categories.value = await listMerchantCategories()
  } catch {
    // 分类加载失败不阻塞页面
  }
}

async function openDialog(p?: Product) {
  if (p) {
    let detail = p
    try {
      detail = await getProductById(p.id)
    } catch {
      // 详情失败时使用列表数据，至少允许编辑基础信息
    }
    Object.assign(form, {
      id: detail.id,
      name: detail.name,
      price: Number(detail.price),
      stock: detail.stock,
      description: detail.description || '',
      images: detail.images || '',
      imageUrls: parseProductImages(detail.images),
      categoryId: detail.categoryId,
      skus: (detail.skus?.length ? detail.skus : [{
        skuName: '默认规格',
        price: Number(detail.price),
        stock: detail.stock,
      }]).map((sku) => ({
        id: sku.id,
        skuName: sku.skuName,
        price: Number(sku.price),
        stock: sku.stock,
        image: sku.image,
        status: sku.status,
      })),
    })
  } else {
    Object.assign(form, emptyForm())
  }
  showDialog.value = true
}

function addSku() {
  form.skus.push(emptySku())
}

function removeSku(index: number) {
  if (form.skus.length <= 1) return
  form.skus.splice(index, 1)
}

async function onImageSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (files.length === 0) return
  const remain = 9 - form.imageUrls.length
  if (remain <= 0) { ElMessage.warning('最多上传 9 张图片'); return }
  const selected = files.slice(0, remain)
  if (files.length > remain) ElMessage.warning(`最多再上传 ${remain} 张，已忽略多余文件`)
  uploadingImage.value = true
  let uploaded = 0
  try {
    for (const file of selected) {
      if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
        ElMessage.warning(`${file.name} 格式不支持，已跳过`)
        continue
      }
      if (file.size > 5 * 1024 * 1024) {
        ElMessage.warning(`${file.name} 超过 5MB，已跳过`)
        continue
      }
      form.imageUrls.push(await uploadProductImage(file))
      uploaded++
    }
    if (uploaded > 0) {
      form.images = serializeProductImages(form.imageUrls)
      ElMessage.success(`已上传 ${uploaded} 张图片`)
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '图片上传失败')
  } finally {
    uploadingImage.value = false
  }
}

function removeImage(index: number) {
  form.imageUrls.splice(index, 1)
  form.images = serializeProductImages(form.imageUrls)
}

async function onSave() {
  if (uploadingImage.value) { ElMessage.warning('图片还在上传，请稍候'); return }
  if (!form.name.trim()) { ElMessage.warning('请输入商品名称'); return }
  const invalidSku = form.skus.find((sku) => !sku.skuName.trim() || !sku.price || sku.price <= 0 || sku.stock < 0)
  if (invalidSku) { ElMessage.warning('请填写完整且合法的 SKU 规格'); return }
  const skuNames = form.skus.map((sku) => sku.skuName.trim())
  if (new Set(skuNames).size !== skuNames.length) { ElMessage.warning('规格名称不能重复'); return }
  form.images = serializeProductImages(form.imageUrls)
  form.price = Math.min(...form.skus.map((sku) => Number(sku.price)))
  form.stock = form.skus.reduce((sum, sku) => sum + Number(sku.stock), 0)
  saving.value = true
  try {
    if (form.id) {
      await updateProduct(form.id, {
        name: form.name,
        price: form.price,
        stock: form.stock,
        description: form.description,
        images: form.images,
        categoryId: form.categoryId,
        skus: form.skus,
      })
      ElMessage.success('修改成功')
    } else {
      await addProduct({
        name: form.name,
        price: form.price,
        stock: form.stock,
        description: form.description,
        images: form.images,
        categoryId: form.categoryId,
        skus: form.skus,
      })
      ElMessage.success('新增成功（默认下架，可在数据库上架）')
    }
    showDialog.value = false
    await fetchProducts()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onDelete(p: Product) {
  try {
    await ElMessageBox.confirm(`确定删除「${p.name}」吗？`, '提示')
    await deleteProduct(p.id)
    ElMessage.success('已删除')
    await fetchProducts()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.response?.data?.message || e?.message || '删除失败')
    }
  }
}

async function onToggleStatus(p: Product, status: 0 | 1) {
  try {
    await updateProductStatus(p.id, status)
    ElMessage.success(status === 1 ? '已上架' : '已下架')
    await fetchProducts()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '操作失败')
  }
}

onMounted(() => {
  fetchProducts()
  fetchCategories()
})

</script>

<style scoped>
.mp-page { width: 100%; max-width: 900px; margin: 0 auto; padding: 40px 24px 88px; }
.mp-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.mp-title { font-family: var(--font-display); font-size: clamp(1.5rem, 3vw, 2.25rem); font-weight: 400; line-height: 1.5; color: var(--color-text-primary); margin: 0; }
.mp-add-btn { --el-button-bg-color: var(--color-accent); --el-button-border-color: var(--color-accent); border-radius: 8px; }
.mp-loading { text-align: center; padding: 60px 0; color: var(--color-text-secondary); }
.mp-list { display: flex; flex-direction: column; gap: 12px; }
.mp-card { display: flex; align-items: center; gap: 16px; border: 1px solid var(--color-border-subtle); border-radius: var(--radius-lg); padding: 16px; background: var(--color-bg-raised); }
.mp-card-img { width: 56px; height: 56px; border-radius: 10px; background: var(--color-bg-raised); display: flex; align-items: center; justify-content: center; font-size: 22px; color: var(--color-accent-soft); flex-shrink: 0; overflow: hidden; }
.mp-img { width: 100%; height: 100%; object-fit: cover; }
.mp-card-info { flex: 1; min-width: 0; }
.mp-card-name { font-size: 14px; font-weight: 600; color: var(--color-text-primary); margin: 0 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mp-card-price { font-size: 12px; color: var(--color-text-secondary); margin: 0 0 4px; }
.mp-card-status { font-size: 11px; margin: 0; }
.mp-card-status.on { color: var(--color-success); }
.mp-card-status.off { color: var(--color-error); }
.mp-card-actions { display: flex; flex-direction: column; gap: 2px; flex-shrink: 0; }
.sku-editor { display: flex; width: 100%; flex-direction: column; gap: 8px; }
.sku-row {
  display: grid;
  grid-template-columns: minmax(120px, 1.4fr) minmax(110px, 0.8fr) minmax(100px, 0.7fr) auto;
  gap: 8px;
  align-items: center;
}
.image-input { display: none; }
.image-editor { display: flex; flex-wrap: wrap; gap: 10px; width: 100%; }
.image-picker, .image-preview-box { width: 120px; height: 120px; border: 1px dashed var(--color-border); border-radius: 8px; background: var(--color-bg-overlay); }
.image-picker { display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 8px; color: var(--color-text-secondary); cursor: pointer; }
.image-picker:hover { border-color: var(--color-success); color: var(--color-success); }
.image-picker:disabled { cursor: wait; opacity: .65; }
.picker-symbol { font-size: 28px; line-height: 1; }
.image-preview-box { position: relative; overflow: hidden; }
.image-preview { width: 100%; height: 100%; object-fit: cover; }
.main-image-badge { position: absolute; top: 6px; left: 6px; padding: 2px 6px; border-radius: 4px; background: var(--color-accent); color: #fff; font-size: 10px; }
.image-remove { position: absolute; right: 6px; bottom: 6px; padding: 4px 7px; border: 0; border-radius: 4px; background: #242424bd; color: #fff; font-size: 11px; cursor: pointer; }
.image-remove:disabled { opacity: .55; cursor: wait; }
.image-hint { display: block; margin-top: 6px; color: var(--color-text-muted); font-size: 12px; }
</style>

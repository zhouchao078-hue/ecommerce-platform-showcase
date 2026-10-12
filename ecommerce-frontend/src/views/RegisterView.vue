<template>
  <div class="register-page">
    <div class="register-card">
      <h2 class="register-title">创建账号</h2>
      <p class="register-subtitle">注册后即可开始购物</p>
      <p v-if="errorMsg" class="error-text">{{ errorMsg }}</p>
      <p v-if="successMsg" class="success-text">{{ successMsg }}</p>
      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleRegister" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" size="large" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" size="large" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码（至少6位）" size="large" show-password />
        </el-form-item>
        <el-button type="primary" size="large" class="register-btn" :loading="loading" @click="handleRegister">
          注册
        </el-button>
      </el-form>
      <p class="login-link">
        已有账号？<router-link to="/login">去登录</router-link>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { register } from '@/api/auth'

const router = useRouter()
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const formRef = ref<FormInstance>()
const form = reactive({ username: '', phone: '', password: '' })

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度 3~32 个字符', trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度至少 6 位', trigger: 'blur' },
  ],
}

async function handleRegister() {
  // 先过前端校验，不通过则提示（不发请求）
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await register({ username: form.username, password: form.password, phone: form.phone })
    successMsg.value = '注册成功，正在跳转登录...'
    setTimeout(() => router.push('/login'), 1500)
  } catch (err: any) {
    errorMsg.value = err?.response?.data?.message || err?.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 24px;
  /* 和登录页统一为暖色氛围，去掉原来的薄荷绿渐变 */
  background:
    radial-gradient(ellipse 70% 55% at 12% 0%, rgba(217, 119, 87, 0.09) 0%, transparent 58%),
    radial-gradient(ellipse 60% 50% at 92% 30%, rgba(120, 140, 93, 0.07) 0%, transparent 60%),
    var(--color-bg-base);
}
.register-card { width: 100%; max-width: 420px; padding: 40px; border-radius: var(--radius-lg); background: var(--color-bg-overlay); border: 1px solid var(--color-border-subtle); box-shadow: var(--shadow-elevated); }
.register-title { margin: 0 0 8px; font-family: var(--font-display); font-size: 26px; font-weight: 400; text-align: center; color: var(--color-text-primary); }
.register-subtitle { margin: 0 0 24px; color: var(--color-text-secondary); text-align: center; }
.register-btn { width: 100%; margin-top: 8px; }
.error-text { color: var(--color-error); text-align: center; font-size: 14px; margin: 0 0 16px; }
.success-text { color: var(--color-success); text-align: center; font-size: 14px; margin: 0 0 16px; }
.login-link { text-align: center; margin-top: 20px; color: var(--color-text-secondary); font-size: 14px; }
.login-link a { color: var(--color-accent-dark); text-decoration: none; }
</style>

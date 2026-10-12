<template>
  <div class="login">
    <!-- ═══════════ 左：编辑叙事分栏 ═══════════ -->
    <section class="login__left">
      <router-link to="/dashboard" class="login__brand">STORE<span>.</span></router-link>

      <h1 class="login__title">把日常用品，<br />挑成<em>值得久留</em>的东西。</h1>

      <div class="login__scene">
        <LoginScene :form-state="formState" />
      </div>

      <p class="login__greet">
        <span class="login__greet-mark" aria-hidden="true">
          <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
            <path d="M8 2l1.6 4L14 7.6 9.6 9.2 8 13.2 6.4 9.2 2 7.6l4.4-1.6z" fill="currentColor" />
          </svg>
        </span>
        <span>你好，我是小店的助手。要我带你进去吗？</span>
      </p>
    </section>

    <!-- ═══════════ 右：表单 ═══════════ -->
    <section class="login__right">
      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="rules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <div class="login-form__head">
          <h2 class="login-form__title">欢迎回来</h2>
          <p class="login-form__sub">登录后可以查看订单、购物车和你的专属推荐。</p>
        </div>

        <div class="login-form__body">
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="用户名 / 手机号"
              size="large"
              autocomplete="username"
              @focus="onUsernameFocus"
              @blur="onInputBlur"
              @keyup.enter="handleLogin"
              @input="onInputChange"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="密码"
              size="large"
              show-password
              autocomplete="current-password"
              @focus="onPasswordFocus"
              @blur="onInputBlur"
              @keyup.enter="handleLogin"
              @input="onInputChange"
            />
          </el-form-item>

          <p v-if="errorMsg" class="login-form__error" role="alert">
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <circle cx="8" cy="8" r="6.5" stroke="currentColor" stroke-width="1.5" />
              <path d="M8 5v4M8 11h.01" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
            <span>{{ errorMsg }}</span>
          </p>

          <el-button
            type="primary"
            size="large"
            native-type="submit"
            class="login-form__submit"
            :loading="formState === 'submitting'"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </div>

        <div class="login-form__alt">
          <span>还没有账号？<router-link to="/register">立即注册</router-link></span>
        </div>
      </el-form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import LoginScene from '@/assets/animations/LoginScene.vue'

const router = useRouter()
const authStore = useAuthStore()

type SceneState = 'idle' | 'focus-username' | 'focus-password' | 'submitting' | 'success' | 'error'

const formState = ref<SceneState>('idle')
const formRef = ref<FormInstance>()
const errorMsg = ref('')

const loginForm = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名或手机号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
}

function onUsernameFocus() {
  errorMsg.value = ''
  formState.value = 'focus-username'
}

function onPasswordFocus() {
  errorMsg.value = ''
  formState.value = 'focus-password'
}

function onInputBlur() {
  if (formState.value === 'focus-username' || formState.value === 'focus-password') {
    formState.value = 'idle'
  }
}

// 重新输入就清掉旧报错，不让用户对着过期提示继续改
function onInputChange() {
  if (errorMsg.value) {
    errorMsg.value = ''
    if (formState.value === 'error') formState.value = 'idle'
  }
}

async function handleLogin() {
  if (!formRef.value) return
  if (formState.value === 'submitting') return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  formState.value = 'submitting'
  errorMsg.value = ''

  try {
    await authStore.login({
      username: loginForm.username,
      password: loginForm.password,
    })

    formState.value = 'success'

    // 成功动画只给 1 秒，不要让人盯着一屏彩纸干等
    setTimeout(() => {
      router.push('/dashboard')
    }, 1000)
  } catch (err: any) {
    formState.value = 'error'
    errorMsg.value = err?.response?.data?.message || err?.message || '登录失败，请重试'

    setTimeout(() => {
      if (formState.value === 'error') formState.value = 'idle'
    }, 2500)
  }
}
</script>

<style scoped>
/* ======================== 布局：1.618 非对称分栏 ======================== */
.login {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr;
  background: var(--color-bg-base);
}

@media (min-width: 960px) {
  .login {
    grid-template-columns: 1.618fr 1fr;
  }
}

.login__left {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 32px;
  padding: 40px 32px 48px;
  background:
    radial-gradient(ellipse 65% 50% at 18% 6%, rgba(217, 119, 87, 0.1) 0%, transparent 58%),
    radial-gradient(ellipse 55% 45% at 88% 72%, rgba(120, 140, 93, 0.08) 0%, transparent 62%),
    var(--color-bg-base);
  overflow: hidden;
}

@media (min-width: 960px) {
  .login__left {
    padding: 48px 64px 64px;
  }
}

.login__brand {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.22em;
  color: var(--color-text-primary);
  text-decoration: none;
  white-space: nowrap;
}

.login__brand span {
  color: var(--color-accent);
}

.login__title {
  font-family: 'Lora', 'Songti SC', 'STSong', 'SimSun', Georgia, serif;
  font-size: clamp(1.75rem, 3.4vw, 2.75rem);
  font-weight: 400;
  line-height: 1.4;
  color: var(--color-text-primary);
  /* 用 em 而不是 ch：ch 按父元素字号算，中文标题会被压成竖条 */
  max-width: 13em;
  word-break: keep-all;
  overflow-wrap: break-word;
}

.login__title em {
  font-style: italic;
  color: var(--color-accent);
}

.login__scene {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}

.login__greet {
  display: flex;
  align-items: center;
  gap: 12px;
  align-self: flex-start;
  max-width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 999px;
  background: var(--color-bg-overlay);
  font-size: 15px;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.login__greet-mark {
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: rgba(217, 119, 87, 0.12);
  color: var(--color-accent);
}

/* ======================== 右栏 ======================== */
.login__right {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 32px 64px;
  background: var(--color-bg-raised);
  border-top: 1px solid var(--color-border-subtle);
}

@media (min-width: 960px) {
  .login__right {
    padding: 64px 48px;
    border-top: none;
    border-left: 1px solid var(--color-border-subtle);
  }
}

.login-form {
  width: 100%;
  max-width: 380px;
}

.login-form__head {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 32px;
}

.login-form__title {
  font-family: 'Lora', 'Songti SC', 'STSong', 'SimSun', Georgia, serif;
  font-size: 24px;
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-text-primary);
}

.login-form__sub {
  font-size: 15px;
  line-height: 1.75;
  color: var(--color-text-secondary);
}

.login-form__body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Element Plus 输入框：改回 8px 圆角，聚焦用橙色环 */
.login-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.login-form :deep(.el-input__wrapper) {
  padding: 4px 16px;
  border-radius: 8px;
  background: var(--color-bg-overlay);
  box-shadow: 0 0 0 1px var(--color-border) inset;
  transition: box-shadow 0.15s ease;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--color-border-strong) inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1.5px var(--color-accent) inset, 0 0 0 3px rgba(217, 119, 87, 0.15);
}

.login-form :deep(.el-input__inner) {
  height: 40px;
  font-size: 16px;
  color: var(--color-text-primary);
}

.login-form__error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 16px;
  border-left: 3px solid var(--color-error);
  border-radius: 4px;
  background: rgba(192, 69, 58, 0.08);
  font-size: 15px;
  line-height: 1.6;
  color: var(--color-error);
}

.login-form__error svg {
  flex-shrink: 0;
  margin-top: 4px;
}

.login-form__submit {
  width: 100%;
  height: 48px;
  border-radius: 8px;
  font-size: 16px;
  letter-spacing: 0.08em;
  /* 把主色收在本页，不动全局 Element Plus 主题 */
  --el-button-bg-color: var(--color-accent);
  --el-button-border-color: var(--color-accent);
  --el-button-hover-bg-color: var(--color-accent-dark);
  --el-button-hover-border-color: var(--color-accent-dark);
  --el-button-active-bg-color: var(--color-accent-dark);
  --el-button-active-border-color: var(--color-accent-dark);
}

.login-form__alt {
  margin-top: 24px;
  font-size: 15px;
  color: var(--color-text-secondary);
  text-align: center;
}

.login-form__alt a {
  color: var(--color-accent-dark);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color 0.15s ease;
}

.login-form__alt a:hover {
  border-bottom-color: var(--color-accent-dark);
}

/* ======================== 响应式 ======================== */
@media (max-width: 959px) {
  /* 移动端保留小人场景，压成一条横向小场景，而不是直接隐藏 */
  .login__left {
    padding-bottom: 32px;
  }
  .login__scene {
    min-height: 170px;
  }
  .login__greet {
    align-self: stretch;
  }
}

@media (max-width: 480px) {
  .login__left {
    padding: 32px 20px 24px;
  }
  .login__right {
    padding: 32px 20px 48px;
  }
  .login__title {
    font-size: 26px;
  }
  .login__scene {
    min-height: 150px;
  }
  .login__greet {
    font-size: 14px;
  }
}
</style>

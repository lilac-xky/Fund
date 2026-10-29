<template>
  <div class="app-page">
    <div class="app-atmos">
      <div class="app-atmos__a" />
      <div class="app-atmos__b" />
    </div>

    <main class="app-login">
      <div class="app-login__card">
        <div class="app-login__brand">
          <span class="app-login__glyph">
            <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="1.9">
              <circle cx="12" cy="12" r="8.6" />
              <path d="m15.2 8.8-2 4.4-4.4 2 2-4.4z" fill="#fff" stroke="none" />
            </svg>
          </span>
          <div>
            <div class="app-login__name">基策罗盘</div>
            <div class="app-login__en">FUND COMPASS</div>
          </div>
        </div>

        <h1 class="app-login__h1">登录</h1>
        <p class="app-login__lede">
          单层登录即是全部防线。用户名与口令都在服务器的 <code>config/secrets.env</code> 里，
          不存数据库、不进 Git、也不会通过任何接口回显。
        </p>

        <AppAlert v-if="!checking && !configured" kind="warn" title="尚未配置登录凭据">
          请在服务器的 <code>config/secrets.env</code> 里填写 <code>AUTH_USERNAME</code> 与
          <code>AUTH_PASSWORD</code> 后重启应用。
        </AppAlert>

        <form class="app-login__form" @submit.prevent="onSubmit">
          <AppField label="用户名">
            <AppInput
              ref="userInput"
              :model-value="username"
              :numeric="false"
              inputmode="text"
              placeholder="config/secrets.env 里的 AUTH_USERNAME"
              autocomplete="username"
              @update:model-value="username = $event"
            />
          </AppField>

          <AppField label="口令" :error="error">
            <AppInput
              :model-value="password"
              type="password"
              :numeric="false"
              inputmode="text"
              placeholder="AUTH_PASSWORD"
              autocomplete="current-password"
              @update:model-value="password = $event"
              @enter="onSubmit"
            />
          </AppField>

          <div class="app-login__ops">
            <AppButton
              variant="primary"
              size="lg"
              type="submit"
              :disabled="submitting || !configured"
            >
              {{ submitting ? '登录中…' : '登录' }}
            </AppButton>
            <span v-if="lockedHint" class="app-login__hint">{{ lockedHint }}</span>
          </div>
        </form>

        <p class="app-login__foot">
          连续输错 {{ maxNote }} 次会临时锁定。会话有效期内无需重复登录，过期后自动回到本页。
        </p>
      </div>
    </main>

    <MessageHost />
  </div>
</template>

<script setup lang="ts">
/**
 * 登录页：单层登录入口，未配置凭据时明确提示去改密钥文件而不是让人反复试错
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppAlert from '@/ui/AppAlert.vue'
import AppButton from '@/ui/AppButton.vue'
import AppField from '@/ui/AppField.vue'
import AppInput from '@/ui/AppInput.vue'
import MessageHost from '@/ui/MessageHost.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/** 用户名 */
const username = ref('')

/** 口令 */
const password = ref('')

/** 登录失败原因，就地展示在口令字段下 */
const error = ref('')

/** 用户名输入框引用，进页面自动聚焦 */
const userInput = ref<InstanceType<typeof AppInput> | null>(null)

/** 是否在查询登录状态 */
const checking = computed(() => auth.checking)

/** 凭据是否已配置 */
const configured = computed(() => auth.configured)

/** 登录中 */
const submitting = computed(() => auth.submitting)

/** 锁定与失败提示 */
const lockedHint = ref('')

/** 阈值说明，与后端默认配置保持一致 */
const maxNote = '5'

/**
 * 提交登录，成功后回跳来源页
 */
async function onSubmit(): Promise<void> {
  if (!configured.value) {
    return
  }
  if (!username.value.trim() || !password.value) {
    error.value = '用户名与口令都不能为空'
    return
  }
  error.value = ''
  lockedHint.value = ''
  try {
    await auth.login(username.value.trim(), password.value)
    // 登录后跳到来源页，没有来源页就回总览
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/overview'
    await router.replace(redirect)
  } catch (caught) {
    const message = caught instanceof Error ? caught.message : '登录失败，请稍后再试'
    if (message.includes('锁定')) {
      lockedHint.value = message
    } else {
      error.value = message
    }
    // 口令错一次就清空，避免浏览器留在框里被旁人看到
    password.value = ''
  }
}

onMounted(async () => {
  await auth.refresh()
  if (auth.authenticated) {
    await router.replace('/overview')
    return
  }
  userInput.value?.$el?.focus?.()
})
</script>

<style scoped>
.app-page {
  min-height: 100vh;
  background: var(--canvas);
}

.app-atmos {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.app-atmos__a,
.app-atmos__b {
  position: absolute;
  filter: blur(22px);
}

.app-atmos__a {
  top: -320px;
  left: -128px;
  width: 960px;
  height: 540px;
  background: radial-gradient(ellipse at center, var(--atmos-a), transparent 72%);
}

.app-atmos__b {
  top: -300px;
  right: -200px;
  width: 840px;
  height: 520px;
  background: radial-gradient(ellipse at center, var(--atmos-b), transparent 72%);
}

.app-login {
  position: relative;
  z-index: 2;
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 32px 20px;
}

.app-login__card {
  width: min(440px, 100%);
  padding: 30px 32px 26px;
  border: 1px solid var(--hair);
  border-radius: 18px;
  background: var(--s1);
  box-shadow: 0 24px 64px -32px rgba(0, 0, 0, 0.35);
}

.app-login__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 22px;
}

.app-login__glyph {
  display: grid;
  place-items: center;
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: linear-gradient(150deg, #d9764c, #a8502a);
  box-shadow: 0 5px 16px -6px rgb(180 80 43 / 80%);
}

.app-login__glyph svg {
  width: 16px;
  height: 16px;
}

.app-login__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--t1);
}

.app-login__en {
  font-size: 9.5px;
  letter-spacing: 0.11em;
  color: var(--t4);
}

.app-login__h1 {
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.03em;
  color: var(--t1);
}

.app-login__lede {
  margin-top: 8px;
  margin-bottom: 18px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--t3);
}

.app-login__lede code,
:deep(code) {
  padding: 1px 4px;
  border-radius: 3px;
  background: var(--s3);
  font-size: 11px;
}

.app-login__form {
  margin-top: 8px;
}

.app-login__ops {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 0 4px;
}

.app-login__hint {
  font-size: 11px;
  line-height: 1.5;
  color: var(--warn);
}

.app-login__foot {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--hair);
  font-size: 10.5px;
  line-height: 1.65;
  color: var(--t4);
}
</style>

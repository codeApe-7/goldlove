<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import * as api from '@/api'
import AppIcon from '@/components/AppIcon.vue'
import BrandMark from '@/components/BrandMark.vue'
import { isPhoneValid, validateRegistrationForm } from '@/validators/registration'
import type { AuthorizationDocumentView } from '@/types'

const auth = useAuthStore()
const form = reactive({ phone: '', password: '', confirmPassword: '' })
const agreed = ref(false)
const loading = ref(false)
const error = ref('')
const documentExpanded = ref(false)
const authorizationDocument = ref<AuthorizationDocumentView | null>(null)

const phoneValid = computed(() => isPhoneValid(form.phone))
const canSubmit = computed(
  () => agreed.value && phoneValid.value && !loading.value && form.password !== '',
)

function toast(message: string, icon: 'none' | 'success' = 'none'): void {
  uni.showToast({ title: message, icon })
}

onMounted(async () => {
  try {
    authorizationDocument.value = await api.currentAuthorizationDocument()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '授权书加载失败'
  }
})

async function submit(): Promise<void> {
  const invalid = validateRegistrationForm({ ...form, agreed: agreed.value })
  if (invalid) {
    error.value = invalid
    return
  }
  loading.value = true
  error.value = ''
  try {
    await auth.register(
      form.phone.trim(),
      form.password,
      form.confirmPassword,
      authorizationDocument.value?.version ?? '',
    )
    toast('注册成功', 'success')
    // 注册完直接进档案页，没有授权关卡也没有付费门槛。
    uni.switchTab({ url: '/pages/profile/index' })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '注册失败'
  } finally {
    loading.value = false
  }
}

function backToLogin(): void {
  uni.redirectTo({ url: '/pages/auth/index' })
}
</script>

<template>
  <view class="register-page archive-page">
    <view class="brand-hero">
      <BrandMark light />
      <text class="brand-title">创建账号</text>
      <text class="brand-sub">免费注册 · 免费建档</text>
    </view>

    <view class="surface">
      <label class="field-group">
        <text>手机号</text>
        <input v-model="form.phone" class="field" type="number" maxlength="11" placeholder="请输入手机号" />
      </label>
      <label class="field-group">
        <text>设置密码</text>
        <input v-model="form.password" class="field" type="password" placeholder="12 至 128 位，含字母和数字" />
      </label>
      <label class="field-group">
        <text>确认密码</text>
        <input v-model="form.confirmPassword" class="field" type="password" placeholder="请再次输入密码" />
      </label>

      <view class="agreement">
        <view class="checkbox" :class="{ checked: agreed }" @tap="agreed = !agreed">
          <text v-if="agreed">✓</text>
        </view>
        <text class="agreement-text">
          我已阅读并同意
          <text class="link" @tap="documentExpanded = !documentExpanded">《{{ authorizationDocument?.title ?? '授权书' }}》</text>
        </text>
      </view>
      <scroll-view v-if="documentExpanded" class="document" scroll-y>
        <text class="document-body">{{ authorizationDocument?.content }}</text>
      </scroll-view>

      <text v-if="error" class="error">{{ error }}</text>

      <button class="archive-button-primary submit" :disabled="!canSubmit" @tap="submit">
        {{ loading ? '正在创建账号' : '免费注册' }}
      </button>
      <text class="back" @tap="backToLogin">已有账号？返回登录</text>

      <view class="notice">
        <AppIcon name="lock" :size="16" />
        <view>
          <strong>注册与建档均免费</strong>
          <text>资料仅用于档案匹配；会员为可选的增值服务，可随时在「我的」中升级。</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.register-page {
  min-height: 100vh;
  margin: 0 auto;
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
  background: #ffffff;
}
.brand-hero {
  height: 360rpx;
  padding-top: calc(80rpx + env(safe-area-inset-top));
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #15181c;
  color: #ffffff;
}
.brand-title {
  margin-top: 18rpx;
  color: #ead4a7;
  font-family: "Songti SC", serif;
  font-size: 38rpx;
  letter-spacing: 7rpx;
}
.brand-sub {
  margin-top: 14rpx;
  color: #c2aa7d;
  font-size: 22rpx;
  letter-spacing: 6rpx;
}
.surface {
  position: relative;
  z-index: 2;
  margin: -92rpx 28rpx 0;
  padding: 32rpx 26rpx 24rpx;
  border: 1rpx solid #e5e3df;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 18rpx 48rpx rgba(13, 13, 15, 0.09);
}
.field-group {
  display: block;
  margin-bottom: 24rpx;
}
.field-group > text {
  display: block;
  margin-bottom: 10rpx;
  font-size: 23rpx;
  font-weight: 600;
}
.field {
  width: 100%;
  height: 76rpx;
  padding: 0 22rpx;
  border: 1rpx solid #dfddd9;
  border-radius: 10rpx;
  font-size: 25rpx;
}
.agreement {
  margin-bottom: 18rpx;
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
}
.checkbox {
  flex-shrink: 0;
  width: 32rpx;
  height: 32rpx;
  margin-top: 2rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid #c9c7c2;
  border-radius: 6rpx;
  color: #ffffff;
  font-size: 20rpx;
}
.checkbox.checked {
  border-color: #0d0d0f;
  background: #0d0d0f;
}
.agreement-text {
  flex: 1;
  color: #55565a;
  font-size: 21rpx;
  line-height: 1.5;
}
.link {
  color: #0d0d0f;
  font-weight: 600;
  text-decoration: underline;
}
.document {
  max-height: 400rpx;
  margin-bottom: 18rpx;
  padding: 18rpx;
  border: 1rpx solid #e5e3df;
  border-radius: 12rpx;
  background: #faf9f7;
}
.document-body {
  color: #55565a;
  font-size: 20rpx;
  line-height: 1.7;
  white-space: pre-wrap;
}
.error {
  display: block;
  margin-bottom: 14rpx;
  color: #b3261e;
  font-size: 21rpx;
}
.submit {
  width: 100%;
}
.back {
  display: block;
  margin-top: 14rpx;
  color: #85868a;
  text-align: center;
  font-size: 20rpx;
  text-decoration: underline;
}
.notice {
  margin-top: 22rpx;
  padding: 18rpx;
  display: flex;
  gap: 14rpx;
  border: 1rpx solid #e5e3df;
  border-radius: 12rpx;
}
.notice strong,
.notice text { display: block; }
.notice strong { font-size: 21rpx; }
.notice text { margin-top: 5rpx; color: #929397; font-size: 19rpx; line-height: 1.5; }
@media screen and (min-width: 431px) {
  .register-page { max-width: 430px; }
}
</style>

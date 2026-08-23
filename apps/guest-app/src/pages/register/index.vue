<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import * as api from '@/api'
import BrandMark from '@/components/BrandMark.vue'
import AppButton from '@/components/AppButton.vue'
import AppInput from '@/components/AppInput.vue'
import AppCheckbox from '@/components/AppCheckbox.vue'
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
      <text class="brand-sub">注册 · 建档</text>
    </view>

    <view class="surface">
      <view class="field-group">
        <text class="field-label">手机号</text>
        <AppInput v-model="form.phone" type="number" placeholder="请输入手机号" :maxlength="11" />
      </view>
      <view class="field-group">
        <text class="field-label">设置密码</text>
        <AppInput
          v-model="form.password"
          type="password"
          placeholder="12 至 128 位，含字母和数字"
        />
      </view>
      <view class="field-group">
        <text class="field-label">确认密码</text>
        <AppInput v-model="form.confirmPassword" type="password" placeholder="请再次输入密码" />
      </view>

      <view class="agreement">
        <AppCheckbox v-model="agreed">
          我已阅读并同意
          <text class="link" @tap.stop="documentExpanded = !documentExpanded">
            《{{ authorizationDocument?.title ?? '档案与直播内容授权书' }}》
          </text>
        </AppCheckbox>
      </view>
      <scroll-view v-if="documentExpanded" class="document" scroll-y>
        <text class="document-body">{{ authorizationDocument?.content }}</text>
      </scroll-view>

      <text v-if="error" class="error">{{ error }}</text>

      <AppButton block :disabled="!canSubmit" @tap="submit">
        {{ loading ? '正在创建账号' : '注册' }}
      </AppButton>
      <text class="back" @tap="backToLogin">已有账号？返回登录</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.register-page {
  min-height: 100vh;
  margin: 0 auto;
  padding-bottom: calc(#{$ds-space-8} + env(safe-area-inset-bottom));
  background: $ds-white;
}
.brand-hero {
  height: 360rpx;
  padding-top: calc(80rpx + env(safe-area-inset-top));
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #15181c;
  color: $ds-white;
}
.brand-title {
  margin-top: $ds-space-2;
  color: #ead4a7;
  font-family: $ds-font-serif;
  font-size: 44rpx;
  letter-spacing: 7rpx;
}
.brand-sub {
  margin-top: $ds-space-2;
  @include ds-caption;
  color: #c2aa7d;
  letter-spacing: 6rpx;
}
.surface {
  position: relative;
  z-index: 2;
  margin: -92rpx $ds-space-4 0;
  padding: $ds-space-5 $ds-space-5 $ds-space-4;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-md;
  background: $ds-white;
  box-shadow: $ds-shadow-deep;
}
.field-group {
  margin-bottom: $ds-space-4;
}
.field-label {
  display: block;
  margin-bottom: $ds-space-2;
  @include ds-body-2;
  font-weight: 600;
}
.agreement {
  margin-bottom: $ds-space-3;
}
.link {
  color: $ds-ink;
  font-weight: 600;
  text-decoration: underline;
}
.document {
  max-height: 400rpx;
  margin-bottom: $ds-space-3;
  padding: $ds-space-3;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-sm;
  background: #faf9f7;
}
.document-body {
  @include ds-caption;
  color: $ds-graphite;
  line-height: 38rpx;
  white-space: pre-wrap;
}
.error {
  display: block;
  margin-bottom: $ds-space-2;
  @include ds-caption;
  color: $ds-error;
}
.back {
  display: block;
  margin-top: $ds-space-3;
  @include ds-caption;
  color: $ds-gray;
  text-align: center;
  text-decoration: underline;
}
@media screen and (min-width: 431px) {
  .register-page { max-width: $ds-viewport-max; }
}
</style>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import BrandMark from '@/components/BrandMark.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppInput from '@/components/AppInput.vue'

const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ phone: '', password: '' })

function toast(message: string, icon: 'none' | 'success' = 'none'): void {
  uni.showToast({ title: message, icon })
}

async function submit(): Promise<void> {
  if (!form.phone.trim() || !form.password) {
    toast('请填写手机号与密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.phone.trim(), form.password)
    uni.switchTab({ url: '/pages/profile/index' })
  } catch (error) {
    toast(error instanceof Error ? error.message : '操作失败')
  } finally {
    loading.value = false
  }
}

function goRegister(): void {
  uni.navigateTo({ url: '/pages/register/index' })
}
</script>

<template>
  <view class="auth-page">
    <view class="brand-hero">
      <view class="contour-lines" aria-hidden="true"><text v-for="n in 6" :key="n" /></view>
      <BrandMark light />
      <text class="brand-title">gold 智能档案库</text>
      <text class="brand-sub">真实 · 严谨 · 安全 · 专属</text>
    </view>
    <view class="auth-surface">
      <view class="form-body">
        <view class="field-group">
          <text class="field-label">手机号</text>
          <AppInput v-model="form.phone" type="number" placeholder="请输入手机号" :maxlength="11" />
        </view>
        <view class="field-group">
          <text class="field-label">密码</text>
          <AppInput v-model="form.password" type="password" placeholder="请输入密码" />
        </view>
        <AppButton block :disabled="loading" @tap="submit">
          {{ loading ? '登录中' : '登录' }}
        </AppButton>
        <text class="register-entry" @tap="goRegister">还没有账号？注册建档</text>
      </view>
      <view class="privacy-note"><AppIcon name="lock" :size="16" /><view><strong>我们将严格保护您的隐私与数据安全</strong><text>所有信息仅用于档案匹配，经授权后方可使用。</text></view></view>
    </view>
    <view class="service-footer"><text>如有问题，请联系客服</text><text>☎ 400-888-5200</text><text>服务时间 9:00–21:00</text></view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.auth-page {
  min-height: 100vh;
  margin: 0 auto;
  padding-bottom: calc(#{$ds-space-7} + env(safe-area-inset-bottom));
  background: $ds-white;
}
.brand-hero {
  position: relative;
  height: 510rpx;
  padding-top: calc(94rpx + env(safe-area-inset-top));
  display: flex;
  flex-direction: column;
  align-items: center;
  overflow: hidden;
  background: #15181c;
  color: $ds-white;
}
.contour-lines {
  position: absolute;
  inset: -40rpx -100rpx auto;
  height: 420rpx;
  opacity: 0.12;
  transform: rotate(-10deg);
}
.contour-lines text {
  position: absolute;
  left: 10%;
  top: 10%;
  width: 80%;
  height: 74%;
  border: 2rpx solid #c7c9cc;
  border-radius: 46% 54% 58% 42%;
}
.contour-lines text:nth-child(2) { inset: 18% 16%; width: 68%; height: 62%; }
.contour-lines text:nth-child(3) { inset: 26% 23%; width: 54%; height: 49%; }
.contour-lines text:nth-child(4) { inset: 34% 30%; width: 40%; height: 36%; }
.contour-lines text:nth-child(5) { inset: 42% 37%; width: 26%; height: 23%; }
.contour-lines text:nth-child(6) { inset: 49% 44%; width: 12%; height: 12%; }
.brand-title,
.brand-sub { position: relative; }
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
.auth-surface {
  position: relative;
  z-index: 2;
  margin: -92rpx $ds-space-4 0;
  overflow: hidden;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-md;
  background: $ds-white;
  box-shadow: $ds-shadow-deep;
}
.form-body {
  padding: $ds-space-5 $ds-space-5 $ds-space-4;
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
.register-entry {
  display: block;
  margin-top: $ds-space-4;
  @include ds-body-2;
  color: $ds-ink;
  text-align: center;
  font-weight: 600;
  text-decoration: underline;
}
.privacy-note {
  margin: 0 $ds-space-3 $ds-space-3;
  padding: $ds-space-3;
  display: flex;
  gap: $ds-space-2;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-sm;
  color: $ds-graphite;
}
.privacy-note strong,
.privacy-note text { display: block; }
.privacy-note strong { @include ds-body-2; }
.privacy-note text {
  margin-top: $ds-space-1;
  @include ds-caption;
  color: $ds-gray;
  line-height: 34rpx;
}
.service-footer {
  margin-top: $ds-space-8;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $ds-space-1;
  @include ds-caption;
  color: $ds-gray;
}
.service-footer text:nth-child(2) { color: $ds-graphite; }
@media screen and (min-width: 431px) {
  .auth-page { max-width: $ds-viewport-max; }
}
</style>

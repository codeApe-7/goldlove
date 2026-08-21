<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import BrandMark from '@/components/BrandMark.vue'
import AppIcon from '@/components/AppIcon.vue'

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
      <text class="brand-title">婚恋智能档案库</text>
      <text class="brand-sub">真实 · 严谨 · 安全 · 专属</text>
    </view>
    <view class="auth-surface">
      <view class="form-body">
        <label class="field-group">
          <text>手机号</text>
          <input v-model="form.phone" class="field" type="number" placeholder="请输入手机号" maxlength="11" />
        </label>
        <label class="field-group">
          <text>密码</text>
          <input v-model="form.password" class="field" type="password" placeholder="请输入密码" />
        </label>
        <button class="archive-button-primary submit" :disabled="loading" @tap="submit">
          {{ loading ? '登录中' : '登录' }}
        </button>
        <text class="register-entry" @tap="goRegister">还没有账号？免费注册建档</text>
      </view>
      <view class="privacy-note"><AppIcon name="lock" :size="16" /><view><strong>我们将严格保护您的隐私与数据安全</strong><text>所有信息仅用于档案匹配，经授权后方可使用。</text></view></view>
    </view>
    <view class="service-footer"><text>如有问题，请联系客服</text><text>☎ 400-888-5200</text><text>服务时间 9:00–21:00</text></view>
  </view>
</template>

<style lang="scss" scoped>
.auth-page {
  min-height: 100vh;
  margin: 0 auto;
  padding-bottom: calc(32rpx + env(safe-area-inset-bottom));
  background: #ffffff;
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
  color: #ffffff;
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
.auth-surface {
  position: relative;
  z-index: 2;
  margin: -92rpx 28rpx 0;
  overflow: hidden;
  border: 1rpx solid #e5e3df;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 18rpx 48rpx rgba(13, 13, 15, 0.09);
}
.form-body {
  padding: 32rpx 32rpx 28rpx;
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
.submit {
  margin-top: 8rpx;
}
.register-entry {
  display: block;
  margin-top: 20rpx;
  color: #0d0d0f;
  text-align: center;
  font-size: 21rpx;
  font-weight: 600;
  text-decoration: underline;
}
.privacy-note {
  margin: 0 20rpx 20rpx;
  padding: 20rpx;
  display: flex;
  gap: 14rpx;
  border: 1rpx solid #e5e3df;
  border-radius: 12rpx;
  color: #55565a;
}
.privacy-note strong,
.privacy-note text { display: block; }
.privacy-note strong { font-size: 21rpx; }
.privacy-note text { margin-top: 5rpx; color: #929397; font-size: 19rpx; line-height: 1.5; }
.service-footer {
  margin-top: 54rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9rpx;
  color: #8b8c90;
  font-size: 20rpx;
}
.service-footer text:nth-child(2) { color: #4a4b4f; }
@media screen and (min-width: 431px) {
  .auth-page { max-width: 430px; }
}
</style>

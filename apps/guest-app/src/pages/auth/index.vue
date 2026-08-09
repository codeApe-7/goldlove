<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const mode = ref<'activate' | 'login'>('login')
const loading = ref(false)
const form = reactive({ phone: '', credential: '', password: '' })

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
    if (mode.value === 'activate') {
      await auth.activate(form.phone.trim(), form.credential.trim(), form.password)
      toast('激活成功', 'success')
    } else {
      await auth.login(form.phone.trim(), form.password)
    }
    uni.switchTab({ url: '/pages/profile/index' })
  } catch (error) {
    toast(error instanceof Error ? error.message : '操作失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <view class="page auth-page">
    <view class="brand">
      <text class="brand-title">婚恋智能档案库</text>
      <text class="brand-sub">让每一段认真开始的关系都有据可循</text>
    </view>
    <view class="mode-tabs">
      <text :class="['tab', mode === 'login' ? 'active' : '']" @tap="mode = 'login'">登录</text>
      <text :class="['tab', mode === 'activate' ? 'active' : '']" @tap="mode = 'activate'">
        激活
      </text>
    </view>
    <view class="card">
      <input v-model="form.phone" class="field" type="number" placeholder="手机号" />
      <input
        v-if="mode === 'activate'"
        v-model="form.credential"
        class="field"
        type="text"
        placeholder="初始凭证"
      />
      <input v-model="form.password" class="field" type="password" placeholder="密码" />
      <button class="submit" :disabled="loading" @tap="submit">
        {{ mode === 'activate' ? '激活并登录' : '登录' }}
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.auth-page {
  min-height: 100vh;
  padding: 80rpx 40rpx;
  background: linear-gradient(160deg, #f7f5f2 0%, #f3e3e7 60%, #e8cfd6 100%);
}
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 60rpx;
}
.brand-title {
  font-size: 44rpx;
  font-weight: 700;
  color: #46323a;
}
.brand-sub {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #8b7a80;
}
.mode-tabs {
  display: flex;
  gap: 24rpx;
  margin-bottom: 24rpx;
}
.tab {
  padding: 12rpx 32rpx;
  border-radius: 999rpx;
  background: #ffffff;
  color: #8b7a80;
  &.active {
    background: #b4556d;
    color: #ffffff;
  }
}
.card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 40rpx;
}
.field {
  height: 88rpx;
  border-bottom: 2rpx solid #f0e6ea;
  margin-bottom: 24rpx;
  font-size: 30rpx;
}
.submit {
  margin-top: 16rpx;
  background: #b4556d;
  color: #ffffff;
  border-radius: 999rpx;
}
</style>

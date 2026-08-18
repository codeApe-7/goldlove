<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { usePaymentStore } from '@/stores/payment'
import { useAuthStore } from '@/stores/auth'
import AppIcon from '@/components/AppIcon.vue'

const payment = usePaymentStore()
const auth = useAuthStore()
const form = reactive({ phone: '', password: '', confirmPassword: '' })
const loading = ref(false)
const error = ref('')

const ready = computed(() => payment.readyToRegister)
const canSubmit = computed(
  () => ready.value && !loading.value && form.phone.trim() !== '' && form.password !== '',
)

function toast(message: string, icon: 'none' | 'success' = 'none'): void {
  uni.showToast({ title: message, icon })
}

onMounted(() => {
  payment.pending = payment.pending ?? null
  if (!ready.value) {
    error.value = '没有可用的注册凭证，请重新完成支付'
  }
})

function validate(): string | null {
  if (!/^\d{11}$/.test(form.phone.trim().replace(/[\s-]/g, ''))) {
    return '请输入 11 位手机号'
  }
  if (form.password.length < 12 || form.password.length > 128) {
    return '密码需为 12 至 128 位'
  }
  if (!/[A-Za-z]/.test(form.password) || !/\d/.test(form.password)) {
    return '密码需同时包含字母和数字'
  }
  if (form.password !== form.confirmPassword) {
    return '两次输入的密码不一致'
  }
  return null
}

async function submit(): Promise<void> {
  const invalid = validate()
  if (invalid) {
    error.value = invalid
    return
  }
  loading.value = true
  error.value = ''
  try {
    const session = await payment.register(form.phone.trim(), form.password)
    auth.session = session
    toast('账号创建成功', 'success')
    // 建档注册即 VIP；接着走已有的授权书与档案流程。
    uni.redirectTo({ url: '/pages/consent/index' })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '注册失败'
  } finally {
    loading.value = false
  }
}

function backToPayment(): void {
  uni.redirectTo({ url: '/pages/payment/index' })
}
</script>

<template>
  <view class="register-page archive-page">
    <view class="header">
      <text class="title">完成注册</text>
      <text class="subtitle">支付已完成，填写手机号与密码即可建立账号</text>
    </view>

    <view class="surface">
      <view v-if="!ready" class="notice warn">
        <AppIcon name="lock" :size="16" />
        <view>
          <strong>注册凭证不可用</strong>
          <text>注册令牌一次性且短时有效。请返回支付页重新领取。</text>
        </view>
      </view>

      <label class="field-group">
        <text>手机号</text>
        <input
          v-model="form.phone"
          class="field"
          type="number"
          maxlength="11"
          placeholder="登录账号，请输入本人手机号"
        />
      </label>
      <label class="field-group">
        <text>设置密码</text>
        <input v-model="form.password" class="field" type="password" placeholder="12 至 128 位，含字母和数字" />
      </label>
      <label class="field-group">
        <text>确认密码</text>
        <input v-model="form.confirmPassword" class="field" type="password" placeholder="请再次输入密码" />
      </label>

      <text v-if="error" class="error">{{ error }}</text>

      <button class="archive-button-primary submit" :disabled="!canSubmit" @tap="submit">
        {{ loading ? '正在创建账号' : '创建账号' }}
      </button>
      <text v-if="!ready" class="back" @tap="backToPayment">返回支付页</text>

      <view class="notice">
        <AppIcon name="lock" :size="16" />
        <view>
          <strong>手机号将加密存储</strong>
          <text>注册成功即成为 VIP 会员；随后阅读并同意授权书，再完善档案资料。</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.register-page {
  min-height: 100vh;
  margin: 0 auto;
  padding: calc(40rpx + env(safe-area-inset-top)) 28rpx calc(40rpx + env(safe-area-inset-bottom));
}
.header {
  margin-bottom: 24rpx;
}
.title {
  display: block;
  color: #0d0d0f;
  font-family: "Songti SC", serif;
  font-size: 40rpx;
  letter-spacing: 4rpx;
}
.subtitle {
  display: block;
  margin-top: 10rpx;
  color: #85868a;
  font-size: 21rpx;
}
.surface {
  padding: 28rpx 26rpx 24rpx;
  border: 1rpx solid #e5e3df;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 18rpx 48rpx rgba(13, 13, 15, 0.06);
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
.notice.warn {
  margin-top: 0;
  margin-bottom: 22rpx;
  border-color: #e6d5b8;
  background: #fdf8ee;
}
.notice strong,
.notice text { display: block; }
.notice strong { font-size: 21rpx; }
.notice text { margin-top: 5rpx; color: #929397; font-size: 19rpx; line-height: 1.5; }
@media screen and (min-width: 431px) {
  .register-page { max-width: 430px; }
}
</style>

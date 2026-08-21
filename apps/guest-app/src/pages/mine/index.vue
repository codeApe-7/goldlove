<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useVipPaymentStore } from '@/stores/payment'
import * as api from '@/api'
import AppIcon from '@/components/AppIcon.vue'
import SectionCard from '@/components/SectionCard.vue'
import ArchiveTabBar from '@/components/ArchiveTabBar.vue'
import type { AuthorizationDocumentView } from '@/types'

const auth = useAuthStore()
const vip = useVipPaymentStore()

const authorizationDocument = ref<AuthorizationDocumentView | null>(null)
const documentExpanded = ref(false)
const documentLoading = ref(false)

/** 注册时勾选的那份授权书，标题以后端下发的为准。 */
const authorizationTitle = computed(
  () => authorizationDocument.value?.title ?? '档案与直播内容授权书',
)

const TIER_LABEL: Record<string, string> = {
  FREE: '普通用户',
  VIP: 'VIP 会员',
  SVIP: 'SVIP 会员',
}

onShow(async () => {
  try {
    await vip.loadMembership()
  } catch {
    // 未登录/会话失效由 request 统一处理
  }
})

function goVip(): void {
  uni.navigateTo({ url: '/pages/vip/index' })
}

async function logout(): Promise<void> {
  await auth.logout()
  uni.reLaunch({ url: '/pages/auth/index' })
}

async function toggleAuthorizationDocument(): Promise<void> {
  if (documentExpanded.value) {
    documentExpanded.value = false
    return
  }
  documentLoading.value = true
  try {
    authorizationDocument.value ??= await api.currentAuthorizationDocument()
    documentExpanded.value = true
  } catch {
    uni.showToast({ title: '授权书加载失败', icon: 'none' })
  } finally {
    documentLoading.value = false
  }
}

function unavailable(): void {
  uni.showToast({ title: '功能暂未开放', icon: 'none' })
}
</script>

<template>
  <view class="archive-page mine-page">
    <view class="identity-card">
      <view class="avatar-mark"><AppIcon name="user" :size="28" /></view>
      <view class="identity-copy"><strong>婚恋档案用户</strong><text class="archive-tabular">账号 ID：{{ auth.session?.accountId ?? '—' }}</text></view>
    </view>

    <SectionCard>
      <view class="authorization-row" @tap="goVip">
        <view><text>当前等级</text><strong>{{ TIER_LABEL[vip.tier] ?? '普通用户' }}</strong></view>
        <view><text>{{ vip.isVip ? '会员权益已生效' : '升级解锁会员权益' }}</text><AppIcon name="chevron" :size="18" /></view>
      </view>
    </SectionCard>

    <SectionCard>
      <view class="menu-row upgrade" @tap="goVip">
        <AppIcon name="authorization" :size="18" />
        <text>{{ vip.isVip ? '会员中心' : '升级 VIP 会员' }}</text>
        <AppIcon name="chevron" :size="18" />
      </view>
    </SectionCard>

    <SectionCard>
      <view class="menu-row" @tap="unavailable"><AppIcon name="shield" :size="18" /><text>隐私政策</text><AppIcon name="chevron" :size="18" /></view>
      <view class="menu-row" @tap="toggleAuthorizationDocument">
        <AppIcon name="document" :size="18" />
        <text>{{ authorizationTitle }}</text>
        <small v-if="documentLoading">加载中</small>
        <view class="chevron" :class="{ expanded: documentExpanded }"><AppIcon name="chevron" :size="18" /></view>
      </view>
      <scroll-view v-if="documentExpanded" class="document" scroll-y>
        <text class="document-body">{{ authorizationDocument?.content }}</text>
      </scroll-view>
      <view class="menu-row" @tap="unavailable"><AppIcon name="account" :size="18" /><text>账户与安全</text><AppIcon name="chevron" :size="18" /></view>
    </SectionCard>

    <SectionCard>
      <view class="menu-row" @tap="unavailable"><AppIcon name="help" :size="18" /><text>帮助与反馈</text><AppIcon name="chevron" :size="18" /></view>
      <view class="menu-row" @tap="unavailable"><AppIcon name="headset" :size="18" /><text>联系客服</text><small>400-888-5200</small><AppIcon name="chevron" :size="18" /></view>
    </SectionCard>

    <button class="logout" @tap="logout">退出登录</button>
    <ArchiveTabBar current="mine" />
  </view>
</template>

<style lang="scss" scoped>
.mine-page { padding-top: 24rpx; }
.menu-row.upgrade text { color: #0d0d0f; font-weight: 600; }
.identity-card {
  min-height: 158rpx;
  margin-bottom: 22rpx;
  padding: 28rpx;
  display: flex;
  align-items: center;
  gap: 22rpx;
  border-radius: 22rpx;
  background: linear-gradient(145deg, #171a1e, #222529);
  color: #ffffff;
  box-shadow: 0 16rpx 32rpx rgba(13, 13, 15, 0.12);
}
.avatar-mark {
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid rgba(255,255,255,.18);
  border-radius: 50%;
  background: rgba(255,255,255,.08);
  color: #dbc28f;
}
.identity-copy strong,
.identity-copy text { display: block; }
.identity-copy strong { font-size: 28rpx; }
.identity-copy text { margin-top: 8rpx; color: #a9aaae; font-size: 21rpx; }
.authorization-row {
  min-height: 102rpx;
  padding: 18rpx 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.authorization-row > view:first-child text,
.authorization-row > view:first-child strong { display: block; }
.authorization-row > view:first-child text { color: #85868a; font-size: 20rpx; }
.authorization-row > view:first-child strong { margin-top: 5rpx; font-size: 25rpx; }
.authorization-row > view:last-child { display: flex; align-items: center; color: #8a8b8f; font-size: 19rpx; }
.menu-row {
  min-height: 82rpx;
  padding: 0 20rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  border-bottom: 1rpx solid #edebe7;
  color: #444549;
}
.menu-row:last-child { border-bottom: 0; }
.menu-row > text:nth-child(2) { flex: 1; font-size: 23rpx; }
.menu-row small { color: #8a8b8f; font-size: 19rpx; }
.menu-row > :last-child { color: #9a9b9e; }
.chevron {
  display: flex;
  align-items: center;
  transition: transform .18s ease;
}
.chevron.expanded { transform: rotate(90deg); }
.document {
  max-height: 460rpx;
  padding: 18rpx 20rpx;
  border-bottom: 1rpx solid #edebe7;
  background: #faf9f7;
}
.document-body {
  color: #55565a;
  font-size: 20rpx;
  line-height: 1.7;
  white-space: pre-wrap;
}
.logout {
  height: 80rpx;
  margin-top: 12rpx;
  border: 0;
  background: transparent;
  color: #dc2626;
  font-size: 24rpx;
  line-height: 80rpx;
}
</style>

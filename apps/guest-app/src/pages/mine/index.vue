<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { setDocumentTitle } from '@/utils/documentTitle'
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
  setDocumentTitle('我的')
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
      <view class="identity-copy"><strong>档案用户</strong><text class="archive-tabular">账号 ID：{{ auth.session?.accountId ?? '—' }}</text></view>
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
@use '@/styles/tokens.scss' as *;

.mine-page { padding-top: $ds-space-3; }
.menu-row.upgrade text { color: $ds-ink; font-weight: 600; }
.identity-card {
  min-height: 178rpx;
  margin-bottom: $ds-space-3;
  padding: $ds-space-4;
  display: flex;
  align-items: center;
  gap: $ds-space-3;
  border-radius: $ds-radius-md;
  background: linear-gradient(145deg, #171a1e, #222529);
  color: $ds-white;
  box-shadow: $ds-shadow-deep;
}
.avatar-mark {
  width: 96rpx;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: $ds-hairline solid rgba(255, 255, 255, 0.18);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  color: #dbc28f;
}
.identity-copy strong,
.identity-copy text { display: block; }
.identity-copy strong { @include ds-h3; }
.identity-copy text {
  margin-top: $ds-space-1;
  @include ds-caption;
  color: #a9aaae;
}
.authorization-row {
  min-height: 118rpx;
  padding: $ds-space-3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $ds-space-3;
}
.authorization-row > view:first-child text,
.authorization-row > view:first-child strong { display: block; }
.authorization-row > view:first-child text {
  @include ds-caption;
  color: $ds-gray;
}
.authorization-row > view:first-child strong {
  margin-top: $ds-space-1;
  @include ds-body-1;
}
.authorization-row > view:last-child {
  display: flex;
  align-items: center;
  @include ds-caption;
  color: $ds-gray;
}
.menu-row {
  min-height: $ds-control-height;
  padding: 0 $ds-space-3;
  display: flex;
  align-items: center;
  gap: $ds-space-2;
  border-bottom: $ds-hairline solid #edebe7;
  color: $ds-graphite;
}
.menu-row:last-child { border-bottom: 0; }
.menu-row > text:nth-child(2) {
  flex: 1;
  @include ds-body-2;
}
.menu-row small {
  @include ds-caption;
  color: $ds-gray;
}
.menu-row > :last-child { color: $ds-gray; }
.chevron {
  display: flex;
  align-items: center;
  transition: transform $ds-transition;
}
.chevron.expanded { transform: rotate(90deg); }
.document {
  max-height: 460rpx;
  padding: $ds-space-3;
  border-bottom: $ds-hairline solid #edebe7;
  background: #faf9f7;
}
.document-body {
  @include ds-caption;
  color: $ds-graphite;
  line-height: 38rpx;
  white-space: pre-wrap;
}
.logout {
  height: $ds-control-height;
  margin-top: $ds-space-2;
  border: 0;
  background: transparent;
  @include ds-body-2;
  color: $ds-error-ink;
  line-height: $ds-control-height;
}
</style>

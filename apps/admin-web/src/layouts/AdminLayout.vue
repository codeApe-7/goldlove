<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, CreditCard, DataBoard, Folder, Key, Tickets } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import BrandMark from '@/components/BrandMark.vue'

const router = useRouter()
const auth = useAuthStore()

/**
 * 侧边栏按职责分三组（规范图 1.1 是单层列表，但那张图的菜单里没有支付订单与激活码，
 * 又列了三个本产品还没有后端的项）。这里只放真实存在的页面，用分组标题把层次补上。
 */
const menuGroups = [
  {
    title: '数据',
    items: [
      { path: '/dashboard', label: '工作台', icon: DataBoard },
      { path: '/profiles', label: '档案管理', icon: Folder },
    ],
  },
  {
    title: '运营',
    items: [
      { path: '/payment-orders', label: '支付订单', icon: CreditCard },
      { path: '/activation-codes', label: '激活码', icon: Key },
    ],
  },
  {
    title: '配置',
    items: [{ path: '/field-definitions', label: '字段配置', icon: Tickets }],
  },
]

async function confirmLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '退出', { type: 'warning' })
  } catch {
    return
  }
  await auth.logout()
  await router.push({ name: 'login' })
}

function showNotifications(): void {
  ElMessage.info('通知功能暂未开放')
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="180px" class="aside">
      <div class="logo">
        <BrandMark compact light />
        <div>
          <strong>婚恋智能档案库</strong>
          <span>管理后台</span>
        </div>
      </div>
      <el-menu
        :default-active="router.currentRoute.value.path"
        router
        background-color="transparent"
      >
        <el-menu-item-group v-for="group in menuGroups" :key="group.title" :title="group.title">
          <el-menu-item v-for="menu in group.items" :key="menu.path" :index="menu.path">
            <el-icon><component :is="menu.icon" /></el-icon>
            <span>{{ menu.label }}</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header" height="56px">
        <span class="spacer" />
        <el-button class="notification" text circle aria-label="查看通知" @click="showNotifications">
          <el-icon><Bell /></el-icon>
        </el-button>
        <span class="header-divider" />
        <span class="avatar">{{ auth.session?.displayName?.slice(0, 1) || '管' }}</span>
        <span class="admin-name">{{ auth.session?.displayName || '管理员' }}</span>
        <el-button link class="logout" @click="confirmLogout">退出</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
<style scoped>
.layout {
  min-width: 1100px;
  height: 100%;
}
.aside {
  background: var(--ds-sidebar);
  color: #ffffff;
  overflow: hidden;
}
.logo {
  height: 72px;
  padding: 15px 14px;
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.logo strong,
.logo span {
  display: block;
  white-space: nowrap;
}
.logo strong {
  font-size: var(--ds-caption-size);
  font-weight: 600;
  letter-spacing: 0.04em;
}
.logo span {
  margin-top: var(--ds-space-1);
  color: #a9aaad;
  font-size: 10px;
  letter-spacing: 0.18em;
}
.aside :deep(.el-menu) {
  padding: var(--ds-space-2);
  border-right: none;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--ds-sidebar-text);
  --el-menu-active-color: var(--ds-sidebar-active-text);
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
}
.aside :deep(.el-menu-item-group__title) {
  padding: var(--ds-space-3) 10px 6px;
  color: #6b6d73;
  font-size: 11px;
  letter-spacing: 0.1em;
}
.aside :deep(.el-menu-item) {
  height: 40px;
  margin-bottom: 2px;
  padding-left: 10px !important;
  border-radius: var(--ds-radius-control);
  font-size: var(--ds-body-size);
}
.aside :deep(.el-menu-item.is-active) {
  position: relative;
  background: rgba(29, 78, 216, 0.16);
}
/* 选中态的竖条用主色（原来是品牌金）；金色只留给 BrandMark 的标识本身 */
.aside :deep(.el-menu-item.is-active::before) {
  position: absolute;
  left: 0;
  width: 2px;
  height: 18px;
  border-radius: 999px;
  background: var(--ds-primary);
  content: '';
}
.header {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--ds-surface);
  border-bottom: 1px solid var(--ds-line);
}
.spacer {
  flex: 1;
}
.notification {
  color: var(--ds-text-secondary);
}
.header-divider {
  width: 1px;
  height: 20px;
  background: var(--ds-line);
}
.avatar {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--ds-primary);
  color: #ffffff;
  font-size: var(--ds-caption-size);
  font-weight: 600;
}
.admin-name {
  color: var(--ds-text);
  font-size: var(--ds-body-size);
  font-weight: 500;
}
.logout {
  color: var(--ds-text-muted);
  font-size: var(--ds-body-size);
}
</style>

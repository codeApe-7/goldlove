<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, DataBoard, DocumentChecked, Postcard, Tickets } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import BrandMark from '@/components/BrandMark.vue'

const router = useRouter()
const auth = useAuthStore()

const menus = [
  { path: '/dashboard', label: '工作台', icon: DataBoard },
  { path: '/guests/register', label: '访客登记', icon: Postcard },
  { path: '/field-definitions', label: '字段配置', icon: Tickets },
  { path: '/reviews', label: '审核管理', icon: DocumentChecked },
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
    <el-aside width="164px" class="aside">
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
        <el-menu-item v-for="menu in menus" :key="menu.path" :index="menu.path">
          <el-icon><component :is="menu.icon" /></el-icon>
          <span>{{ menu.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header" height="52px">
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
  min-width: 960px;
  height: 100%;
}
.aside {
  background: #171a1e;
  color: #ffffff;
  overflow: hidden;
}
.logo {
  height: 72px;
  padding: 15px 13px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.logo strong,
.logo span {
  display: block;
  white-space: nowrap;
}
.logo strong {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.logo span {
  margin-top: 4px;
  color: #a9aaad;
  font-size: 10px;
  letter-spacing: 0.18em;
}
.aside :deep(.el-menu) {
  padding: 12px 8px;
  border-right: none;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #b8b9bc;
  --el-menu-active-color: #ffffff;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
}
.aside :deep(.el-menu-item) {
  height: 42px;
  margin-bottom: 4px;
  border-radius: 6px;
  font-size: 13px;
}
.aside :deep(.el-menu-item.is-active) {
  position: relative;
  background: rgba(255, 255, 255, 0.1);
}
.aside :deep(.el-menu-item.is-active::before) {
  position: absolute;
  left: 0;
  width: 2px;
  height: 18px;
  border-radius: 999px;
  background: var(--archive-gold);
  content: '';
}
.header {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #ffffff;
  border-bottom: 1px solid var(--archive-line);
}
.spacer {
  flex: 1;
}
.notification {
  color: #4e4f53;
}
.header-divider {
  width: 1px;
  height: 20px;
  background: var(--archive-line);
}
.avatar {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--archive-ink);
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
}
.admin-name {
  color: var(--archive-ink);
  font-size: 12px;
  font-weight: 600;
}
.logout {
  color: var(--archive-muted);
  font-size: 12px;
}
@media (max-width: 1100px) {
  .layout {
    min-width: 900px;
  }
}
</style>

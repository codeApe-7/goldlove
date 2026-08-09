<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { DataBoard, DocumentChecked, Postcard, Tickets } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

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
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">婚恋档案库</div>
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
      <el-header class="header">
        <span class="spacer" />
        <span class="admin-name">{{ auth.session?.displayName }}</span>
        <el-button link type="danger" @click="confirmLogout">退出</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: var(--love-deep);
  color: #f5ecef;
}
.logo {
  padding: 20px 18px;
  font-size: 18px;
  font-weight: 600;
  color: #f7e6ea;
}
.aside :deep(.el-menu) {
  border-right: none;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #dcc9ce;
  --el-menu-active-color: #ffffff;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
}
.aside :deep(.el-menu-item.is-active) {
  background: var(--love-rose);
}
.header {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #ffffff;
  border-bottom: 1px solid #eee2e6;
}
.spacer {
  flex: 1;
}
.admin-name {
  color: var(--love-deep);
}
</style>

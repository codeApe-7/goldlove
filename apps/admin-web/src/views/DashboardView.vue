<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dashboardStats } from '@/api/admin'
import type { AdminDashboardStats } from '@/types'

const stats = ref<AdminDashboardStats | null>(null)
const loading = ref(false)

const cards = [
  { key: 'pendingReviews', label: '待审核', hint: '等待管理员处理的提交' },
  { key: 'todayRegistrations', label: '今日登记', hint: '今天新登记的访客' },
  { key: 'todayReviews', label: '今日审核', hint: '今天完成的审核' },
  { key: 'totalProfiles', label: '累计建档', hint: '已建立的全部档案' },
] as const

onMounted(async () => {
  loading.value = true
  try {
    stats.value = await dashboardStats()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载统计失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <h2 class="page-title">工作台</h2>
    <el-row :gutter="16" v-loading="loading">
      <el-col v-for="card in cards" :key="card.key" :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-value tabular">{{ stats?.[card.key] ?? 0 }}</div>
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-hint">{{ card.hint }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 18px;
  color: var(--love-deep);
}
.stat-card {
  text-align: center;
}
.stat-value {
  font-size: 34px;
  font-weight: 600;
  color: var(--love-rose);
}
.stat-label {
  margin-top: 6px;
  font-size: 15px;
  color: #3b3034;
}
.stat-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #9a8b90;
}
</style>

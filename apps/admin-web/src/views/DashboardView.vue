<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dashboardStats } from '@/api/admin'
import type { AdminDashboardStats } from '@/types'
import PageHeader from '@/components/PageHeader.vue'

const stats = ref<AdminDashboardStats | null>(null)
const loading = ref(false)

const cards = [
  { key: 'pendingReviews', label: '待审核', hint: '等待处理的档案' },
  { key: 'todayRegistrations', label: '今日登记', hint: '今日新登记访客' },
  { key: 'todayReviews', label: '今日审核', hint: '今日完成审核' },
  { key: 'totalProfiles', label: '累计建档', hint: '总档案数量' },
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
  <div class="dashboard-page">
    <PageHeader title="工作台" description="欢迎使用婚恋智能档案库管理后台，实时掌握审核与建档情况。" />
    <div v-loading="loading" class="stats-grid">
      <article v-for="card in cards" :key="card.key" class="stat-card">
        <span class="stat-label">{{ card.label }}</span>
        <strong class="stat-value tabular">{{ stats?.[card.key] ?? 0 }}</strong>
        <span class="stat-hint">{{ card.hint }}</span>
      </article>
    </div>
    <section class="dashboard-lower">
      <div class="archive-panel workflow-panel">
        <div class="panel-header">
          <div>
            <h2>今日工作概览</h2>
            <p>根据当前实时统计生成</p>
          </div>
          <span class="live-dot">实时</span>
        </div>
        <div class="workflow-row">
          <div><strong class="tabular">{{ stats?.todayRegistrations ?? 0 }}</strong><span>新增登记</span></div>
          <i />
          <div><strong class="tabular">{{ stats?.pendingReviews ?? 0 }}</strong><span>等待审核</span></div>
          <i />
          <div><strong class="tabular">{{ stats?.todayReviews ?? 0 }}</strong><span>完成审核</span></div>
        </div>
      </div>
      <aside class="archive-panel sla-panel">
        <span class="sla-eyebrow">审核提醒</span>
        <strong class="tabular">{{ stats?.pendingReviews ?? 0 }}</strong>
        <p>份档案当前待处理</p>
        <el-button type="primary" @click="$router.push('/reviews')">进入审核管理</el-button>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.stat-card {
  min-height: 126px;
  padding: 17px 18px;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--archive-line);
  border-radius: 8px;
  background: #ffffff;
}
.stat-value {
  margin-top: 14px;
  color: var(--archive-ink);
  font-size: 30px;
  line-height: 1;
  font-weight: 600;
}
.stat-label {
  color: #3f4044;
  font-size: 13px;
  font-weight: 600;
}
.stat-hint {
  margin-top: auto;
  color: #949599;
  font-size: 11px;
}
.dashboard-lower {
  margin-top: 16px;
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(220px, 0.7fr);
  gap: 16px;
}
.workflow-panel,
.sla-panel {
  min-height: 230px;
  padding: 20px;
}
.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.panel-header h2 {
  margin: 0;
  font-size: 15px;
}
.panel-header p {
  margin: 6px 0 0;
  color: var(--archive-muted);
  font-size: 11px;
}
.live-dot {
  color: var(--archive-success);
  font-size: 11px;
}
.workflow-row {
  height: 150px;
  display: flex;
  align-items: center;
  justify-content: space-around;
}
.workflow-row div {
  min-width: 100px;
  text-align: center;
}
.workflow-row strong,
.workflow-row span {
  display: block;
}
.workflow-row strong {
  font-size: 28px;
  font-weight: 600;
}
.workflow-row span {
  margin-top: 8px;
  color: var(--archive-muted);
  font-size: 12px;
}
.workflow-row i {
  width: 64px;
  height: 1px;
  background: var(--archive-line);
}
.sla-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.sla-eyebrow {
  color: var(--archive-muted);
  font-size: 12px;
}
.sla-panel strong {
  margin-top: 12px;
  font-size: 42px;
  font-weight: 600;
}
.sla-panel p {
  margin: 6px 0 18px;
  color: var(--archive-muted);
  font-size: 12px;
}
@media (max-width: 1100px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>

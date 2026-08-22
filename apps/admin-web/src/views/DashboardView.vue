<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dashboardStats } from '@/api/admin'
import { amountLabel } from '@/utils/presentation'
import type { AdminDashboardStats } from '@/types'
import PageHeader from '@/components/PageHeader.vue'

const stats = ref<AdminDashboardStats | null>(null)
const loading = ref(false)

const cards = [
  { key: 'totalAccounts', label: '注册用户', hint: '累计注册账号' },
  { key: 'todayRegistrations', label: '今日注册', hint: '今日新增账号' },
  { key: 'totalProfiles', label: '累计建档', hint: '已创建的档案' },
  { key: 'completedProfiles', label: '资料完善', hint: '必填项与头像齐全' },
  { key: 'vipMembers', label: 'VIP 会员', hint: '付费或兑码升级' },
  { key: 'svipMembers', label: 'SVIP 会员', hint: '累计付费达标自动升级' },
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
    <PageHeader title="工作台" subtitle="欢迎使用婚恋智能档案库管理后台，实时掌握注册、建档与会员情况。" />
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
          <div><strong class="tabular">{{ stats?.todayRegistrations ?? 0 }}</strong><span>今日注册</span></div>
          <i />
          <div><strong class="tabular">{{ stats?.completedProfiles ?? 0 }}</strong><span>资料完善</span></div>
          <i />
          <div>
            <strong class="tabular">{{ amountLabel(stats?.todayPaidAmountMinor ?? 0) }}</strong>
            <span>今日收款</span>
          </div>
        </div>
      </div>
      <aside class="archive-panel sla-panel">
        <span class="sla-eyebrow">档案总量</span>
        <strong class="tabular">{{ stats?.totalProfiles ?? 0 }}</strong>
        <p>份档案，保存即可见</p>
        <el-button type="primary" @click="$router.push('/profiles')">进入档案管理</el-button>
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
  border: 1px solid var(--ds-line);
  border-radius: var(--ds-radius-card);
  background: var(--ds-surface);
  box-shadow: var(--ds-shadow-card);
}
.stat-value {
  margin-top: 14px;
  color: var(--ds-text);
  font-size: var(--ds-h1-size);
  line-height: 1;
  font-weight: var(--ds-h1-weight);
}
.stat-label {
  color: var(--ds-text-secondary);
  font-size: var(--ds-body-size);
  font-weight: 500;
}
.stat-hint {
  margin-top: auto;
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
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
  font-size: var(--ds-h3-size);
  font-weight: var(--ds-h3-weight);
}
.panel-header p {
  margin: 6px 0 0;
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
.live-dot {
  color: var(--ds-success);
  font-size: var(--ds-caption-size);
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
  font-size: var(--ds-h1-size);
  font-weight: var(--ds-h1-weight);
}
.workflow-row span {
  margin-top: var(--ds-space-2);
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
.workflow-row i {
  width: 64px;
  height: 1px;
  background: var(--ds-line);
}
.sla-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.sla-eyebrow {
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
.sla-panel strong {
  margin-top: 12px;
  font-size: 42px;
  font-weight: var(--ds-h1-weight);
}
.sla-panel p {
  margin: 6px 0 18px;
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
@media (max-width: 1100px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>

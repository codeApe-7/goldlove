<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listReviews } from '@/api/admin'
import type { ProfileReviewListItem } from '@/types'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'

const router = useRouter()
const items = ref<ProfileReviewListItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const filters = reactive<Record<string, string | undefined>>({
  status: 'PENDING',
  deadline: undefined,
  profileNo: undefined,
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listReviews({
      page: page.value,
      size,
      ...filters,
    })
    items.value = result.items
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function openDetail(row: ProfileReviewListItem): void {
  void router.push({ name: 'review-detail', params: { id: String(row.revisionId) } })
}

async function selectStatus(status: string | undefined): Promise<void> {
  filters.status = status
  page.value = 1
  await load()
}

async function resetFilters(): Promise<void> {
  filters.status = 'PENDING'
  filters.deadline = undefined
  filters.profileNo = undefined
  page.value = 1
  await load()
}

onMounted(load)
</script>

<template>
  <div class="reviews-page">
    <PageHeader title="审核管理" description="查看并处理访客档案提交，确保资料规范完整。" />
    <section class="archive-panel review-panel">
      <nav class="status-tabs" aria-label="审核状态筛选">
        <button :class="{ active: !filters.status }" @click="selectStatus(undefined)">全部</button>
        <button :class="{ active: filters.status === 'PENDING' }" @click="selectStatus('PENDING')">待审核</button>
        <button :class="{ active: filters.status === 'APPROVED' }" @click="selectStatus('APPROVED')">已通过</button>
        <button :class="{ active: filters.status === 'REJECTED' }" @click="selectStatus('REJECTED')">已退回</button>
      </nav>
      <div class="filters">
        <el-select v-model="filters.deadline" clearable placeholder="截止时间" style="width: 140px">
          <el-option label="即将超时" value="DUE_SOON" />
          <el-option label="已超时" value="OVERDUE" />
        </el-select>
        <el-input v-model="filters.profileNo" placeholder="输入档案编号" style="width: 260px" clearable @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="items" row-class-name="clickable-row" @row-click="openDetail">
        <el-table-column prop="revisionId" label="版本 ID" width="90" />
        <el-table-column prop="revisionNumber" label="版本号" width="90" />
        <el-table-column prop="profileNo" label="档案编号" min-width="210" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="submittedAt" label="提交时间" width="180" />
        <el-table-column prop="reviewDeadlineAt" label="审核截止" width="180" />
        <el-table-column label="操作" width="80" fixed="right"><template #default="{ row }"><el-button link @click.stop="openDetail(row)">查看</el-button></template></el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        class="pager"
        @current-change="load"
      />
    </section>
  </div>
</template>

<style scoped>
.review-panel {
  overflow: hidden;
}
.status-tabs {
  height: 52px;
  padding: 0 18px;
  display: flex;
  align-items: flex-end;
  gap: 24px;
  border-bottom: 1px solid var(--archive-line);
}
.status-tabs button {
  height: 52px;
  padding: 0 2px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--archive-muted);
  cursor: pointer;
}
.status-tabs button.active {
  border-bottom-color: var(--archive-ink);
  color: var(--archive-ink);
  font-weight: 600;
}
.filters {
  padding: 14px 18px;
  display: flex;
  gap: 12px;
  border-bottom: 1px solid var(--archive-line);
}
.pager {
  padding: 14px 18px;
  justify-content: flex-end;
}
:deep(.clickable-row) {
  cursor: pointer;
}
</style>

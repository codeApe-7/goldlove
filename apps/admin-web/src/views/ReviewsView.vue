<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listReviews } from '@/api/admin'
import type { ProfileReviewListItem } from '@/types'

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

onMounted(load)
</script>

<template>
  <div>
    <h2 class="page-title">审核管理</h2>
    <el-card>
      <div class="filters">
        <el-select v-model="filters.status" clearable placeholder="状态" style="width: 140px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已退回" value="REJECTED" />
        </el-select>
        <el-select v-model="filters.deadline" clearable placeholder="截止时间" style="width: 140px">
          <el-option label="即将超时" value="DUE_SOON" />
          <el-option label="已超时" value="OVERDUE" />
        </el-select>
        <el-input v-model="filters.profileNo" placeholder="档案编号" style="width: 260px" clearable />
        <el-button type="primary" @click="load">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="items" stripe @row-click="openDetail">
        <el-table-column prop="revisionId" label="版本 ID" width="90" />
        <el-table-column prop="revisionNumber" label="版本号" width="90" />
        <el-table-column prop="profileNo" label="档案编号" width="260" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              :type="
                row.status === 'PENDING'
                  ? 'warning'
                  : row.status === 'APPROVED'
                    ? 'success'
                    : 'danger'
              "
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submittedAt" label="提交时间" width="180" />
        <el-table-column prop="reviewDeadlineAt" label="审核截止" width="180" />
      </el-table>
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        class="pager"
        @current-change="load"
      />
    </el-card>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 18px;
  color: var(--love-deep);
}
.filters {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listProfiles } from '@/api/admin'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { membershipTierMeta, profileStatusMeta } from '@/utils/presentation'
import type { AdminProfileListItem } from '@/types'

const router = useRouter()
const rows = ref<AdminProfileListItem[]>([])
const total = ref(0)
const loading = ref(false)
const filters = reactive({ phone: '', status: '', membershipTier: '', page: 1, size: 20 })

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listProfiles({
      phone: filters.phone || undefined,
      status: filters.status || undefined,
      membershipTier: filters.membershipTier || undefined,
      page: filters.page,
      size: filters.size,
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '档案列表加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  filters.page = 1
  void load()
}

function reset(): void {
  filters.phone = ''
  filters.status = ''
  filters.membershipTier = ''
  search()
}

function openDetail(row: AdminProfileListItem): void {
  void router.push({ name: 'profile-detail', params: { id: row.id } })
}

onMounted(load)
</script>

<template>
  <div class="profiles-view">
    <PageHeader title="档案管理" subtitle="档案保存即可见，未填完的也在列表里" />

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="手机号">
          <el-input v-model="filters.phone" placeholder="支持片段匹配" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="完成度">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="未填完" value="DRAFT" />
            <el-option label="已完善" value="COMPLETED" />
          </el-select>
        </el-form-item>
        <el-form-item label="会员等级">
          <el-select v-model="filters.membershipTier" placeholder="全部" clearable style="width: 140px">
            <el-option label="普通" value="FREE" />
            <el-option label="VIP" value="VIP" />
            <el-option label="SVIP" value="SVIP" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" @row-click="openDetail" class="clickable">
        <el-table-column prop="profileNo" label="档案编号" min-width="240" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="会员等级" width="110">
          <template #default="{ row }">
            <StatusTag v-bind="membershipTierMeta(row.membershipTier)" />
          </template>
        </el-table-column>
        <el-table-column label="完成度" width="110">
          <template #default="{ row }">
            <StatusTag v-bind="profileStatusMeta(row.status)" />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="200" />
      </el-table>
      <el-pagination
        v-model:current-page="filters.page"
        v-model:page-size="filters.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @current-change="load"
        @size-change="search"
      />
    </el-card>
  </div>
</template>

<style scoped>
.filter-card { margin-bottom: 16px; }
.pagination { margin-top: 16px; justify-content: flex-end; }
.clickable :deep(.el-table__row) { cursor: pointer; }
</style>

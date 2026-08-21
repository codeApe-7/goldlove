<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listPaymentOrders } from '@/api/admin'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { amountLabel, paymentOrderStatusMeta } from '@/utils/presentation'
import type { AdminPaymentOrderItem } from '@/types'

const rows = ref<AdminPaymentOrderItem[]>([])
const total = ref(0)
const loading = ref(false)
const filters = reactive({ phone: '', status: '', page: 1, size: 20 })

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listPaymentOrders({
      phone: filters.phone || undefined,
      status: filters.status || undefined,
      page: filters.page,
      size: filters.size,
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '订单列表加载失败')
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
  search()
}

onMounted(load)
</script>

<template>
  <div class="payment-orders-view">
    <PageHeader title="支付订单" subtitle="VIP 升级订单；金额一律以服务端配置为准" />

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="手机号">
          <el-input v-model="filters.phone" placeholder="支持片段匹配" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="待支付" value="CREATED" />
            <el-option label="已支付" value="PAID" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="outTradeNo" label="商户订单号" min-width="280" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="金额" width="120">
          <template #default="{ row }">{{ amountLabel(row.amountMinor) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag v-bind="paymentOrderStatusMeta(row.status)" />
          </template>
        </el-table-column>
        <el-table-column prop="channel" label="渠道" width="140" />
        <el-table-column label="支付时间" width="200">
          <template #default="{ row }">{{ row.paidAt ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" width="200" />
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
</style>

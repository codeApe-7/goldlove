<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listPaymentOrders } from '@/api/admin'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import FilterBar from '@/components/FilterBar.vue'
import TableToolbar from '@/components/TableToolbar.vue'
import EmptyState from '@/components/EmptyState.vue'
import { amountLabel, minuteLabel, paymentOrderStatusMeta } from '@/utils/presentation'
import type { AdminPaymentOrderItem } from '@/types'

const rows = ref<AdminPaymentOrderItem[]>([])
const total = ref(0)
const loading = ref(false)
const filters = reactive({ phone: '', status: '' })
const page = reactive({ current: 1, size: 20 })

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listPaymentOrders({
      phone: filters.phone || undefined,
      status: filters.status || undefined,
      page: page.current,
      size: page.size,
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
  page.current = 1
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

    <FilterBar :loading="loading" @search="search" @reset="reset">
      <template #fields>
        <label class="filter-field">
          <span>手机号</span>
          <el-input
            v-model="filters.phone"
            placeholder="支持片段匹配"
            clearable
            style="width: 200px"
            @keyup.enter="search"
          />
        </label>
        <label class="filter-field">
          <span>状态</span>
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="待支付" value="CREATED" />
            <el-option label="已支付" value="PAID" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </label>
      </template>
    </FilterBar>

    <TableToolbar :selected-count="0" @refresh="load">
      <template #hint>共 {{ total.toLocaleString() }} 笔订单</template>
    </TableToolbar>

    <section class="archive-panel table-panel">
      <el-table :data="rows" v-loading="loading" row-key="id">
        <el-table-column prop="outTradeNo" label="商户订单号" min-width="260">
          <template #default="{ row }"><span class="mono">{{ row.outTradeNo }}</span></template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="金额" width="110">
          <template #default="{ row }"><span class="tabular">{{ amountLabel(row.amountMinor) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag v-bind="paymentOrderStatusMeta(row.status)" /></template>
        </el-table-column>
        <el-table-column prop="channel" label="渠道" width="140" />
        <el-table-column label="支付时间" width="150">
          <template #default="{ row }">{{ minuteLabel(row.paidAt) }}</template>
        </el-table-column>
        <el-table-column label="下单时间" width="150">
          <template #default="{ row }">{{ minuteLabel(row.createdAt) }}</template>
        </el-table-column>
        <template #empty>
          <EmptyState title="暂无订单" hint="访客在会员页发起 VIP 升级后，订单会出现在这里" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @current-change="load"
        @size-change="search"
      />
    </section>
  </div>
</template>

<style scoped>
.table-panel {
  padding: var(--ds-space-4);
}
.pagination {
  margin-top: var(--ds-space-4);
  justify-content: flex-end;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: var(--ds-caption-size);
}
</style>

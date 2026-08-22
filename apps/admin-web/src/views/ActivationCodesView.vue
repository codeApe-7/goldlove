<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { generateActivationCode, listActivationCodes, revokeActivationCode } from '@/api/admin'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import FilterBar from '@/components/FilterBar.vue'
import TableToolbar from '@/components/TableToolbar.vue'
import EmptyState from '@/components/EmptyState.vue'
import { activationCodeStatusMeta, membershipTierMeta, minuteLabel, orDash } from '@/utils/presentation'
import type { AdminActivationCodeItem } from '@/types'

const route = useRoute()
const rows = ref<AdminActivationCodeItem[]>([])
const total = ref(0)
const loading = ref(false)
const generating = ref(false)
const drawerVisible = ref(false)
const issued = ref<AdminActivationCodeItem | null>(null)

const filters = reactive({ phone: '', status: '' })
const page = reactive({ current: 1, size: 20 })
const form = reactive({ boundPhone: '', grantedTier: 'VIP' as 'VIP' | 'SVIP', note: '' })

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listActivationCodes({
      phone: filters.phone || undefined,
      status: filters.status || undefined,
      page: page.current,
      size: page.size,
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '激活码列表加载失败')
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

function openDrawer(): void {
  form.boundPhone = ''
  form.grantedTier = 'VIP'
  form.note = ''
  issued.value = null
  drawerVisible.value = true
}

async function generate(): Promise<void> {
  if (!/^1[3-9]\d{9}$/.test(form.boundPhone.trim())) {
    ElMessage.warning('请输入 11 位手机号')
    return
  }
  generating.value = true
  try {
    issued.value = await generateActivationCode({
      boundPhone: form.boundPhone.trim(),
      grantedTier: form.grantedTier,
      note: form.note || null,
    })
    ElMessage.success('激活码已生成')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生成失败')
  } finally {
    generating.value = false
  }
}

async function revoke(row: AdminActivationCodeItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定作废 ${row.code} 吗？作废后不可恢复。`, '作废激活码', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await revokeActivationCode(row.id)
    ElMessage.success('已作废')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '作废失败')
  }
}

async function copy(code: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择')
  }
}

onMounted(() => {
  // 档案管理页的主操作按钮带 ?generate=1 过来，直接把生成抽屉打开。
  if (route.query.generate === '1') {
    openDrawer()
  }
  void load()
})
</script>
<template>
  <div class="activation-codes-view">
    <PageHeader title="激活码" subtitle="生成时绑定手机号，只有该手机号的账号能兑换" />

    <FilterBar :loading="loading" @search="search" @reset="reset">
      <template #fields>
        <label class="filter-field">
          <span>绑定手机号</span>
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
            <el-option label="未使用" value="UNUSED" />
            <el-option label="已兑换" value="USED" />
            <el-option label="已作废" value="REVOKED" />
          </el-select>
        </label>
      </template>
    </FilterBar>

    <TableToolbar :selected-count="0" @refresh="load">
      <template #hint>共 {{ total.toLocaleString() }} 个激活码</template>
      <template #actions>
        <el-button type="primary" @click="openDrawer">生成激活码</el-button>
      </template>
    </TableToolbar>

    <section class="archive-panel table-panel">
      <el-table :data="rows" v-loading="loading" row-key="id">
        <el-table-column prop="code" label="激活码" min-width="200">
          <template #default="{ row }"><span class="mono">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column label="绑定手机号" width="180">
          <template #default="{ row }">
            <span>{{ row.boundPhone }}</span>
            <el-tooltip
              v-if="!row.boundPhoneRegistered && row.status === 'UNUSED'"
              content="该手机号尚未注册。注册不做短信验证，抢先用这个号注册的人就能领走此码。"
            >
              <el-tag type="warning" size="small" effect="plain" class="warn-tag">未注册</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="授予等级" width="100">
          <template #default="{ row }"><StatusTag v-bind="membershipTierMeta(row.grantedTier)" /></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag v-bind="activationCodeStatusMeta(row.status)" /></template>
        </el-table-column>
        <el-table-column label="兑换人" width="140">
          <template #default="{ row }">{{ orDash(row.redeemedPhone) }}</template>
        </el-table-column>
        <el-table-column label="兑换时间" width="150">
          <template #default="{ row }">{{ minuteLabel(row.redeemedAt) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ minuteLabel(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="{ row }">{{ orDash(row.note) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="copy(row.code)">复制</el-button>
            <el-button v-if="row.status === 'UNUSED'" link type="danger" @click="revoke(row)">
              作废
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState title="暂无激活码" hint="线下收款后在这里生成，并把码私下发给对应手机号的本人">
            <template #action>
              <el-button type="primary" @click="openDrawer">生成激活码</el-button>
            </template>
          </EmptyState>
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
    <el-drawer v-model="drawerVisible" title="生成激活码" size="440px">
      <el-form label-position="top">
        <el-form-item label="手机号" required>
          <el-input v-model="form.boundPhone" maxlength="11" placeholder="激活码只能被该手机号兑换" />
        </el-form-item>
        <el-form-item label="授予等级">
          <el-radio-group v-model="form.grantedTier">
            <el-radio value="VIP">VIP</el-radio>
            <el-radio value="SVIP">SVIP</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input
            v-model="form.note"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="选填，例如线下收款单号"
          />
        </el-form-item>
      </el-form>

      <el-alert v-if="issued" type="success" :closable="false" show-icon class="issued">
        <template #title>
          <span class="mono issued-code">{{ issued.code }}</span>
          <el-button link type="primary" @click="copy(issued.code)">复制</el-button>
        </template>
        <span v-if="!issued.boundPhoneRegistered">
          该手机号尚未注册，请通过私下渠道把码发给本人。
        </span>
      </el-alert>

      <template #footer>
        <el-button @click="drawerVisible = false">关闭</el-button>
        <el-button type="primary" :loading="generating" @click="generate">生成</el-button>
      </template>
    </el-drawer>
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
.warn-tag {
  margin-left: var(--ds-space-2);
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  letter-spacing: 0.04em;
}
.issued {
  margin-top: var(--ds-space-4);
}
.issued-code {
  font-size: var(--ds-h3-size);
}
</style>

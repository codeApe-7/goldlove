<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Setting } from '@element-plus/icons-vue'
import {
  activateAccount,
  exportProfiles,
  listProfiles,
  profileCounts,
  suspendAccount,
  type ProfileQuery,
} from '@/api/admin'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatusTabs, { type StatusTabItem } from '@/components/StatusTabs.vue'
import FilterBar from '@/components/FilterBar.vue'
import TableToolbar from '@/components/TableToolbar.vue'
import EmptyState from '@/components/EmptyState.vue'
import RegionCascader from '@/components/RegionCascader.vue'
import ProfileDetailDrawer from '@/components/ProfileDetailDrawer.vue'
import SuspendAccountDialog from '@/components/SuspendAccountDialog.vue'
import {
  accountStatusMeta,
  ageLabel,
  membershipTierMeta,
  minuteLabel,
  orDash,
  profileStatusMeta,
  shortProfileNo,
} from '@/utils/presentation'
import {
  OPTIONAL_PROFILE_COLUMNS,
  hiddenFromVisible,
  saveHiddenColumns,
  visibleColumns as storedVisibleColumns,
} from '@/utils/profileColumns'
import type { AdminProfileCounts, AdminProfileDetail, AdminProfileListItem, ProfileSort } from '@/types'

/** tab → 筛选条件。「已付费会员」= VIP 或 SVIP（规范图的「已授权」在本产品没有对应数据）。 */
const TAB_FILTERS: Record<string, ProfileQuery> = {
  all: {},
  draft: { status: 'DRAFT' },
  completed: { status: 'COMPLETED' },
  suspended: { accountStatus: 'SUSPENDED' },
  paid: { paidOnly: true },
}

const route = useRoute()
const router = useRouter()

const rows = ref<AdminProfileListItem[]>([])
const selected = ref<AdminProfileListItem[]>([])
const counts = ref<AdminProfileCounts>({ total: 0, draft: 0, completed: 0, suspended: 0, paid: 0 })
const total = ref(0)
const loading = ref(false)
const exporting = ref(false)
const tab = ref('all')
const sort = ref<ProfileSort>('UPDATED_DESC')
const filters = reactive({
  keyword: '',
  membershipTier: '',
  city: '',
  dateRange: null as [Date, Date] | null,
})
const page = reactive({ current: 1, size: 20 })

const drawerVisible = ref(false)
const drawerProfileId = ref<number | null>(null)
const drawerRef = ref<InstanceType<typeof ProfileDetailDrawer> | null>(null)

const statusDialog = reactive({
  visible: false,
  mode: 'suspend' as 'suspend' | 'activate',
  accountId: 0,
  target: '',
  saving: false,
})

const visibleColumns = ref<string[]>(storedVisibleColumns(window.localStorage))

const tabs = computed<StatusTabItem[]>(() => [
  { key: 'all', label: '全部', count: counts.value.total },
  { key: 'draft', label: '草稿', count: counts.value.draft },
  { key: 'completed', label: '已建档', count: counts.value.completed },
  { key: 'suspended', label: '已停用', count: counts.value.suspended },
  { key: 'paid', label: '已付费会员', count: counts.value.paid },
])

function shows(key: string): boolean {
  return visibleColumns.value.includes(key)
}

/** 筛选条上的条件。tab 条件单独加，计数接口不需要它们。 */
function baseQuery(): ProfileQuery {
  const query: ProfileQuery = {}
  if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
  if (filters.membershipTier) query.membershipTier = filters.membershipTier
  if (filters.city) query.city = filters.city
  if (filters.dateRange) {
    const [from, to] = filters.dateRange
    // 日期选择器给的是本地零点，右端要推到当天最后一刻，否则「到 5 月 31 日」会漏掉整天。
    query.createdFrom = startOfDay(from).toISOString()
    query.createdTo = endOfDay(to).toISOString()
  }
  return query
}

function listQuery(): ProfileQuery {
  return { ...baseQuery(), ...TAB_FILTERS[tab.value], sort: sort.value }
}

function startOfDay(value: Date): Date {
  const copy = new Date(value)
  copy.setHours(0, 0, 0, 0)
  return copy
}

function endOfDay(value: Date): Date {
  const copy = new Date(value)
  copy.setHours(23, 59, 59, 999)
  return copy
}
async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listProfiles({
      ...listQuery(),
      page: page.current,
      size: page.size,
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '档案列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadCounts(): Promise<void> {
  try {
    counts.value = await profileCounts(baseQuery())
  } catch {
    // 计数只是 tab 上的角标，拿不到不该盖住列表本身的错误提示。
  }
}

function refresh(): void {
  void load()
  void loadCounts()
}

function search(): void {
  page.current = 1
  refresh()
}

function reset(): void {
  filters.keyword = ''
  filters.membershipTier = ''
  filters.city = ''
  filters.dateRange = null
  sort.value = 'UPDATED_DESC'
  search()
}

function switchTab(key: string): void {
  tab.value = key
  page.current = 1
  void load()
}

function openDetail(row: AdminProfileListItem): void {
  drawerProfileId.value = row.id
  drawerVisible.value = true
}

// ---- 导出 ----

async function download(ids: number[]): Promise<void> {
  exporting.value = true
  try {
    const blob = await exportProfiles(listQuery(), ids)
    saveBlob(blob)
    ElMessage.success(ids.length > 0 ? `已导出 ${ids.length} 条` : '已导出当前筛选结果')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败')
  } finally {
    exporting.value = false
  }
}

function saveBlob(blob: Blob): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `档案导出-${minuteLabel(new Date().toISOString()).replace(/[: ]/g, '-')}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

// ---- 停用 / 启用 ----

function askStatusChange(row: { accountId: number; phone: string; profileNo: string; accountStatus: string }): void {
  statusDialog.mode = row.accountStatus === 'SUSPENDED' ? 'activate' : 'suspend'
  statusDialog.accountId = row.accountId
  statusDialog.target = `${row.phone}（档案 ${shortProfileNo(row.profileNo)}）`
  statusDialog.visible = true
}

async function confirmStatusChange(reason: string): Promise<void> {
  statusDialog.saving = true
  try {
    const trimmed = reason.trim() || null
    if (statusDialog.mode === 'suspend') {
      await suspendAccount(statusDialog.accountId, trimmed)
      ElMessage.success('已停用')
    } else {
      await activateAccount(statusDialog.accountId, trimmed)
      ElMessage.success('已启用')
    }
    statusDialog.visible = false
    refresh()
    if (drawerVisible.value) {
      await drawerRef.value?.reload()
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    statusDialog.saving = false
  }
}

function onDrawerStatusChange(detail: AdminProfileDetail): void {
  askStatusChange(detail)
}

function onColumnsChange(next: string[]): void {
  visibleColumns.value = next
  saveHiddenColumns(window.localStorage, hiddenFromVisible(next))
}

/** 行内「更多操作」。规范图 3.6 的编辑资料 / 备注记录 / 删除本产品没有对应能力，不放空菜单项。 */
function onRowCommand(command: string, row: AdminProfileListItem): void {
  if (command === 'detail') {
    openDetail(row)
    return
  }
  if (command === 'export') {
    void download([row.id])
    return
  }
  if (command === 'link') {
    void copyRowLink(row)
    return
  }
  if (command === 'status') {
    askStatusChange(row)
  }
}

async function copyRowLink(row: AdminProfileListItem): Promise<void> {
  try {
    await navigator.clipboard.writeText(`${window.location.origin}/profiles?profile=${row.id}`)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制地址栏')
  }
}

function goToActivationCodes(): void {
  // 后台不代访客建档，规范图工具栏那个主操作位在本产品对应的是「发激活码」。
  void router.push({ name: 'activation-codes', query: { generate: '1' } })
}

onMounted(() => {
  // 复制链接分享出来的地址带 ?profile=<id>，进页面直接把抽屉打开。
  const shared = Number(route.query.profile)
  if (Number.isInteger(shared) && shared > 0) {
    drawerProfileId.value = shared
    drawerVisible.value = true
  }
  refresh()
})
</script>
<template>
  <div class="profiles-view">
    <PageHeader title="档案管理" subtitle="档案由访客自助填写，保存即可见；后台只读，不代填。" />

    <StatusTabs :items="tabs" :active="tab" @change="switchTab" />

    <FilterBar :loading="loading" @search="search" @reset="reset">
      <template #fields>
        <label class="filter-field">
          <span>关键词</span>
          <el-input
            v-model="filters.keyword"
            placeholder="手机号 / 档案编号"
            clearable
            style="width: 200px"
            @keyup.enter="search"
          />
        </label>
        <label class="filter-field">
          <span>创建时间</span>
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :editable="false"
            style="width: 260px"
          />
        </label>
        <label class="filter-field">
          <span>会员等级</span>
          <el-select v-model="filters.membershipTier" placeholder="全部" clearable style="width: 130px">
            <el-option label="普通" value="FREE" />
            <el-option label="VIP" value="VIP" />
            <el-option label="SVIP" value="SVIP" />
          </el-select>
        </label>
        <label class="filter-field">
          <span>所在地区</span>
          <RegionCascader v-model="filters.city" />
        </label>
        <label class="filter-field">
          <span>排序方式</span>
          <el-select v-model="sort" style="width: 150px" @change="search">
            <el-option label="默认排序" value="UPDATED_DESC" />
            <el-option label="创建时间（新→旧）" value="CREATED_DESC" />
            <el-option label="创建时间（旧→新）" value="CREATED_ASC" />
            <el-option label="手机号（升序）" value="PHONE_ASC" />
            <el-option label="手机号（降序）" value="PHONE_DESC" />
          </el-select>
        </label>
      </template>
    </FilterBar>

    <TableToolbar :selected-count="selected.length" @refresh="refresh">
      <template #batch>
        <el-button
          :loading="exporting"
          @click="download(selected.map((row) => row.id))"
        >
          批量导出
        </el-button>
      </template>
      <template #hint>共 {{ total.toLocaleString() }} 份档案</template>
      <template #actions>
        <el-button :loading="exporting" @click="download([])">导出当前筛选</el-button>
        <el-dropdown trigger="click" :hide-on-click="false">
          <el-button :icon="Setting">列设置<el-icon><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <div class="column-settings">
              <el-checkbox-group :model-value="visibleColumns" @update:model-value="onColumnsChange($event as string[])">
                <el-checkbox
                  v-for="column in OPTIONAL_PROFILE_COLUMNS"
                  :key="column.key"
                  :value="column.key"
                  :label="column.label"
                />
              </el-checkbox-group>
            </div>
          </template>
        </el-dropdown>
        <el-button type="primary" @click="goToActivationCodes">生成激活码</el-button>
      </template>
    </TableToolbar>
    <section class="archive-panel table-panel">
      <el-table
        :data="rows"
        v-loading="loading"
        row-key="id"
        class="clickable"
        @selection-change="selected = $event"
        @row-click="openDetail"
      >
        <el-table-column type="selection" width="44" :selectable="() => true" />
        <el-table-column label="档案编号" width="120">
          <template #default="{ row }">
            <span class="mono">{{ shortProfileNo(row.profileNo) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column v-if="shows('gender')" label="性别" width="70">
          <template #default="{ row }">{{ orDash(row.gender) }}</template>
        </el-table-column>
        <el-table-column v-if="shows('age')" label="年龄" width="70">
          <template #default="{ row }">{{ ageLabel(row.age) }}</template>
        </el-table-column>
        <el-table-column v-if="shows('city')" label="所在地区" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ orDash(row.city) }}</template>
        </el-table-column>
        <el-table-column v-if="shows('membershipTier')" label="会员等级" width="100">
          <template #default="{ row }"><StatusTag v-bind="membershipTierMeta(row.membershipTier)" /></template>
        </el-table-column>
        <el-table-column label="完成度" width="100">
          <template #default="{ row }"><StatusTag v-bind="profileStatusMeta(row.status)" /></template>
        </el-table-column>
        <el-table-column label="账号状态" width="100">
          <template #default="{ row }"><StatusTag v-bind="accountStatusMeta(row.accountStatus)" /></template>
        </el-table-column>
        <el-table-column v-if="shows('createdAt')" label="创建时间" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ minuteLabel(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="shows('updatedAt')" label="更新时间" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ minuteLabel(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-dropdown trigger="click" @command="(command: string) => onRowCommand(command, row)">
              <el-button link type="primary" @click.stop>
                更多操作<el-icon><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="detail">查看详情</el-dropdown-item>
                  <el-dropdown-item command="export">导出资料</el-dropdown-item>
                  <el-dropdown-item command="link">复制链接</el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.accountStatus !== 'CLOSED'"
                    command="status"
                    divided
                  >
                    {{ row.accountStatus === 'SUSPENDED' ? '启用账号' : '停用账号' }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState
            title="暂无档案"
            hint="调整筛选条件，或让访客在小程序端完成建档"
          />
        </template>
      </el-table>

      <div class="table-footer">
        <span class="status-legend">
          <i class="dot warning" />草稿
          <i class="dot success" />已建档
          <i class="dot danger" />已停用
          <i class="dot info" />已付费会员
        </span>
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </section>

    <ProfileDetailDrawer
      ref="drawerRef"
      v-model="drawerVisible"
      :profile-id="drawerProfileId"
      @status-change="onDrawerStatusChange"
      @export="(detail) => download([detail.id])"
    />

    <SuspendAccountDialog
      v-model="statusDialog.visible"
      :mode="statusDialog.mode"
      :target="statusDialog.target"
      :loading="statusDialog.saving"
      @confirm="confirmStatusChange"
    />
  </div>
</template>
<style scoped>
.table-panel {
  padding: var(--ds-space-4);
}
.clickable :deep(.el-table__row) {
  cursor: pointer;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: var(--ds-caption-size);
  letter-spacing: 0.04em;
}
.column-settings {
  padding: var(--ds-space-3) var(--ds-space-4);
}
.column-settings :deep(.el-checkbox-group) {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.table-footer {
  margin-top: var(--ds-space-4);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-space-4);
  flex-wrap: wrap;
}
/* 规范图 5 底部的状态说明：四个色点对应四种状态语义 */
.status-legend {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot:not(:first-child) {
  margin-left: var(--ds-space-3);
}
.dot.warning {
  background: var(--ds-warning);
}
.dot.success {
  background: var(--ds-success);
}
.dot.danger {
  background: var(--ds-danger);
}
.dot.info {
  background: var(--ds-primary);
}
</style>

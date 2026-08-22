<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { profileDetail } from '@/api/admin'
import DetailDrawer from './DetailDrawer.vue'
import StatusTag from './StatusTag.vue'
import EmptyState from './EmptyState.vue'
import {
  accountStatusMeta,
  ageLabel,
  amountLabel,
  membershipTierMeta,
  minuteLabel,
  orDash,
  profileStatusMeta,
  shortProfileNo,
} from '@/utils/presentation'
import type { AdminProfileDetail } from '@/types'

/**
 * 档案详情抽屉（规范图 7.3）。自己按 profileId 拉详情——
 * 列表里的行数据不含学历、照片等字段，共用一份反而要在列表接口上塞冗余列。
 */
const props = defineProps<{ modelValue: boolean; profileId: number | null }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'status-change': [detail: AdminProfileDetail]
  export: [detail: AdminProfileDetail]
}>()

const detail = ref<AdminProfileDetail | null>(null)
const loading = ref(false)
const tab = ref('profile')

const avatar = computed(() => detail.value?.photos.find((photo) => photo.category === 'AVATAR'))
const lifePhotos = computed(
  () => detail.value?.photos.filter((photo) => photo.category === 'LIFE') ?? [],
)

const fields = computed(() => {
  const value = detail.value
  if (!value) return []
  return [
    { label: '性别', value: orDash(value.gender) },
    { label: '年龄', value: ageLabel(value.birthDate) },
    { label: '出生日期', value: orDash(value.birthDate) },
    { label: '所在地区', value: orDash(value.city) },
    { label: '学历', value: orDash(value.education) },
    { label: '职业', value: orDash(value.occupation) },
    { label: '年薪', value: orDash(value.incomeRange) },
    { label: '身高', value: value.heightCm ? `${value.heightCm} cm` : '—' },
    { label: '微信号', value: orDash(value.wechatId) },
    { label: '抖音号', value: orDash(value.douyinId) },
    { label: '抖音昵称', value: orDash(value.douyinNickname) },
    { label: '抖音主页', value: orDash(value.douyinProfileUrl) },
    { label: '累计付费', value: amountLabel(value.membershipCreditMinor) },
    { label: '创建时间', value: minuteLabel(value.createdAt) },
    { label: '更新时间', value: minuteLabel(value.updatedAt) },
  ]
})

watch(
  () => [props.modelValue, props.profileId] as const,
  async ([visible, profileId]) => {
    if (!visible || profileId === null) {
      return
    }
    tab.value = 'profile'
    loading.value = true
    try {
      detail.value = await profileDetail(profileId)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '档案加载失败')
      emit('update:modelValue', false)
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

async function copyLink(): Promise<void> {
  const value = detail.value
  if (!value) return
  const link = `${window.location.origin}/profiles?profile=${value.id}`
  try {
    await navigator.clipboard.writeText(link)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制地址栏')
  }
}

/** 详情里的操作交回列表处理：停用弹窗与导出都要刷新列表，放在这里会各写一遍。 */
function requestStatusChange(): void {
  if (detail.value) {
    emit('status-change', detail.value)
  }
}

function requestExport(): void {
  if (detail.value) {
    emit('export', detail.value)
  }
}

/** 外部改完状态后刷新一次，抽屉里的标签才不会停留在旧状态。 */
async function reload(): Promise<void> {
  if (props.profileId === null) return
  detail.value = await profileDetail(props.profileId)
}

defineExpose({ reload })
</script>
<template>
  <DetailDrawer
    :model-value="modelValue"
    :loading="loading"
    :title="detail ? detail.phone : '档案详情'"
    :subtitle="detail ? `档案 ${shortProfileNo(detail.profileNo)} · 创建于 ${minuteLabel(detail.createdAt)}` : ''"
    :avatar-url="avatar?.previewUrl"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #tag>
      <template v-if="detail">
        <StatusTag v-bind="profileStatusMeta(detail.status)" />
        <StatusTag v-bind="accountStatusMeta(detail.accountStatus)" />
        <StatusTag v-bind="membershipTierMeta(detail.membershipTier)" />
      </template>
    </template>

    <el-tabs v-if="detail" v-model="tab">
      <el-tab-pane label="资料" name="profile">
        <dl class="field-list">
          <div v-for="field in fields" :key="field.label" class="field-row">
            <dt>{{ field.label }}</dt>
            <dd>{{ field.value }}</dd>
          </div>
        </dl>
        <template v-if="detail.dynamicFields.length">
          <h3 class="section-title">自定义字段</h3>
          <dl class="field-list">
            <div v-for="field in detail.dynamicFields" :key="field.fieldCode" class="field-row">
              <dt>{{ field.label }}</dt>
              <dd>{{ orDash(field.value) }}</dd>
            </div>
          </dl>
        </template>
      </el-tab-pane>

      <el-tab-pane :label="`照片 (${detail.photos.length})`" name="photos">
        <div v-if="detail.photos.length" class="photos">
          <figure v-if="avatar" class="photo">
            <el-image :src="avatar.previewUrl" fit="cover" :preview-src-list="[avatar.previewUrl]" />
            <figcaption>头像</figcaption>
          </figure>
          <figure v-for="photo in lifePhotos" :key="photo.id" class="photo">
            <el-image
              :src="photo.previewUrl"
              fit="cover"
              :preview-src-list="lifePhotos.map((item) => item.previewUrl)"
            />
            <figcaption>生活照 {{ photo.sortOrder + 1 }}</figcaption>
          </figure>
        </div>
        <EmptyState v-else title="尚未上传照片" hint="访客保存档案时才会把照片写入档案" />
      </el-tab-pane>
    </el-tabs>

    <template #actions>
      <el-button @click="requestExport">导出资料</el-button>
      <el-button @click="copyLink">复制链接</el-button>
      <el-button
        v-if="detail && detail.accountStatus !== 'CLOSED'"
        :type="detail.accountStatus === 'SUSPENDED' ? 'primary' : 'danger'"
        plain
        @click="requestStatusChange"
      >
        {{ detail.accountStatus === 'SUSPENDED' ? '启用账号' : '停用账号' }}
      </el-button>
    </template>
  </DetailDrawer>
</template>

<style scoped>
.field-list {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--ds-space-5);
}
.field-row {
  display: flex;
  gap: var(--ds-space-3);
  padding: 9px 0;
  border-bottom: 1px solid var(--ds-line);
}
.field-row dt {
  flex: none;
  width: 72px;
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
  line-height: 20px;
}
.field-row dd {
  flex: 1;
  min-width: 0;
  margin: 0;
  color: var(--ds-text);
  font-size: var(--ds-body-size);
  line-height: 20px;
  word-break: break-all;
}
.section-title {
  margin: var(--ds-space-5) 0 var(--ds-space-2);
  font-size: var(--ds-h3-size);
  font-weight: var(--ds-h3-weight);
}
.photos {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-4);
}
.photo {
  width: 140px;
  margin: 0;
}
.photo :deep(.el-image) {
  width: 140px;
  height: 140px;
  border-radius: var(--ds-radius-card);
}
.photo figcaption {
  margin-top: 6px;
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
  text-align: center;
}
</style>

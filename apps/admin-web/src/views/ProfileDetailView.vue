<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { profileDetail } from '@/api/admin'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { amountLabel, membershipTierMeta, profileStatusMeta } from '@/utils/presentation'
import type { AdminProfileDetail } from '@/types'

const route = useRoute()
const router = useRouter()
const detail = ref<AdminProfileDetail | null>(null)
const loading = ref(false)

const avatar = computed(() => detail.value?.photos.find((photo) => photo.category === 'AVATAR'))
const lifePhotos = computed(
  () => detail.value?.photos.filter((photo) => photo.category === 'LIFE') ?? [],
)

const coreFields = computed(() => {
  const value = detail.value
  if (!value) return []
  return [
    { label: '性别', value: value.gender },
    { label: '出生日期', value: value.birthDate },
    { label: '身高（厘米）', value: value.heightCm },
    { label: '学历', value: value.education },
    { label: '职业', value: value.occupation },
    { label: '年薪', value: value.incomeRange },
    { label: '所在城市', value: value.city },
    { label: '微信号', value: value.wechatId },
    { label: '抖音号', value: value.douyinId },
    { label: '抖音昵称', value: value.douyinNickname },
    { label: '抖音主页', value: value.douyinProfileUrl },
  ]
})

async function load(): Promise<void> {
  loading.value = true
  try {
    detail.value = await profileDetail(Number(route.params.id))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '档案加载失败')
  } finally {
    loading.value = false
  }
}

function back(): void {
  void router.push({ name: 'profiles' })
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="profile-detail-view">
    <PageHeader title="档案详情" :subtitle="detail?.profileNo ?? ''">
      <template #actions>
        <el-button @click="back">返回列表</el-button>
      </template>
    </PageHeader>

    <template v-if="detail">
      <el-card shadow="never" class="summary">
        <el-descriptions :column="4" border>
          <el-descriptions-item label="手机号">{{ detail.phone }}</el-descriptions-item>
          <el-descriptions-item label="会员等级">
            <StatusTag v-bind="membershipTierMeta(detail.membershipTier)" />
          </el-descriptions-item>
          <el-descriptions-item label="累计付费">
            {{ amountLabel(detail.membershipCreditMinor) }}
          </el-descriptions-item>
          <el-descriptions-item label="完成度">
            <StatusTag v-bind="profileStatusMeta(detail.status)" />
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detail.updatedAt }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="section">
        <template #header><span>核心资料</span></template>
        <el-descriptions :column="3" border>
          <el-descriptions-item
            v-for="field in coreFields"
            :key="field.label"
            :label="field.label"
          >
            {{ field.value ?? '—' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card v-if="detail.dynamicFields.length" shadow="never" class="section">
        <template #header><span>自定义字段</span></template>
        <el-descriptions :column="3" border>
          <el-descriptions-item
            v-for="field in detail.dynamicFields"
            :key="field.fieldCode"
            :label="field.label"
          >
            {{ field.value ?? '—' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="section">
        <template #header><span>照片</span></template>
        <div v-if="detail.photos.length" class="photos">
          <div v-if="avatar" class="photo">
            <el-image :src="avatar.previewUrl" fit="cover" :preview-src-list="[avatar.previewUrl]" />
            <span>头像</span>
          </div>
          <div v-for="photo in lifePhotos" :key="photo.id" class="photo">
            <el-image
              :src="photo.previewUrl"
              fit="cover"
              :preview-src-list="lifePhotos.map((item) => item.previewUrl)"
            />
            <span>生活照 {{ photo.sortOrder + 1 }}</span>
          </div>
        </div>
        <el-empty v-else description="尚未上传照片" />
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.summary { margin-bottom: 16px; }
.section { margin-bottom: 16px; }
.photos { display: flex; flex-wrap: wrap; gap: 16px; }
.photo { width: 160px; display: flex; flex-direction: column; gap: 8px; }
.photo :deep(.el-image) { width: 160px; height: 160px; border-radius: 8px; }
.photo span { color: #85868a; font-size: 13px; text-align: center; }
</style>

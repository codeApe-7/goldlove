<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approveReview, rejectReview, reviewDetail } from '@/api/admin'
import type { ProfileReviewDetail } from '@/types'

const route = useRoute()
const router = useRouter()
const detail = ref<ProfileReviewDetail | null>(null)
const loading = ref(false)
const deciding = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    detail.value = await reviewDetail(Number(route.params.id))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function approve(): Promise<void> {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('确认通过该档案版本？', '通过审核', { type: 'success' })
  } catch {
    return
  }
  deciding.value = true
  try {
    await approveReview(detail.value.revisionId, detail.value.version)
    ElMessage.success('已通过')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    deciding.value = false
  }
}

async function reject(): Promise<void> {
  if (!detail.value) return
  try {
    const { value } = await ElMessageBox.prompt('请输入面向嘉宾的退回说明', '退回审核', {
      inputType: 'textarea',
      inputValidator: (text: string) => (text.trim() ? true : '退回说明不能为空'),
    })
    deciding.value = true
    await rejectReview(detail.value.revisionId, detail.value.version, null, value.trim())
    ElMessage.success('已退回')
    await load()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    deciding.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <el-page-header class="page-header" @back="router.push({ name: 'reviews' })">
      <template #content>审核详情 #{{ detail?.revisionId }}</template>
    </el-page-header>
    <template v-if="detail">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-card>
            <template #header>档案信息</template>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="档案编号">{{ detail.profileNo }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
              <el-descriptions-item label="性别">{{ detail.gender ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="出生日期">{{ detail.birthDate ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="身高">{{ detail.heightCm ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="学历">{{ detail.education ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="职业">{{ detail.occupation ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="收入">{{ detail.incomeRange ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="城市">{{ detail.city ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="微信号">{{ detail.wechatId ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="抖音号">{{ detail.douyinId ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="提交时间">{{ detail.submittedAt }}</el-descriptions-item>
              <el-descriptions-item label="截止时间">{{ detail.reviewDeadlineAt }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card>
            <template #header>照片</template>
            <el-empty v-if="detail.photos.length === 0" description="无照片" />
            <div class="photos">
              <el-image
                v-for="photo in detail.photos"
                :key="photo.sha256"
                :src="photo.downloadUrl"
                :preview-src-list="detail.photos.map((p) => p.downloadUrl)"
                fit="cover"
                class="photo"
              />
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-card class="section">
        <template #header>与最后已通过版本的差异</template>
        <el-table v-if="detail.differences.length" :data="detail.differences" stripe>
          <el-table-column prop="fieldLabel" label="字段" width="140" />
          <el-table-column prop="oldValue" label="原值" />
          <el-table-column prop="newValue" label="新值" />
        </el-table>
        <el-empty v-else description="与最后已通过版本无差异（或尚无已通过版本）" />
      </el-card>
      <el-card v-if="detail.status === 'PENDING'" class="section">
        <el-button type="success" :loading="deciding" @click="approve">通过</el-button>
        <el-button type="danger" :loading="deciding" @click="reject">退回</el-button>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.page-header {
  margin-bottom: 16px;
}
.photos {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.photo {
  width: 120px;
  height: 120px;
  border-radius: 8px;
}
.section {
  margin-top: 16px;
}
</style>

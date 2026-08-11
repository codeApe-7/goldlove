<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approveReview, rejectReview, reviewDetail } from '@/api/admin'
import type { ProfileReviewDetail } from '@/types'
import StatusTag from '@/components/StatusTag.vue'

const route = useRoute()
const router = useRouter()
const detail = ref<ProfileReviewDetail | null>(null)
const loading = ref(false)
const deciding = ref(false)
const rejectComment = ref('')

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
  if (!rejectComment.value.trim()) {
    ElMessage.warning('退回说明不能为空')
    return
  }
  deciding.value = true
  try {
    await rejectReview(detail.value.revisionId, detail.value.version, null, rejectComment.value.trim())
    ElMessage.success('已退回')
    rejectComment.value = ''
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    deciding.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="detail-page">
    <header class="detail-header">
      <el-button link @click="router.push({ name: 'reviews' })">← 返回列表</el-button>
      <div><h1>审核详情</h1><span v-if="detail">档案编号：{{ detail.profileNo }}</span></div>
      <span v-if="detail" class="revision-no">版本 ID：{{ detail.revisionId }}</span>
    </header>
    <template v-if="detail">
      <div class="detail-grid">
        <main class="detail-main">
          <section class="archive-panel info-panel">
            <div class="section-heading"><h2>档案信息</h2><StatusTag :status="detail.status" /></div>
            <dl class="info-grid">
              <div><dt>性别</dt><dd>{{ detail.gender ?? '—' }}</dd></div>
              <div><dt>出生日期</dt><dd>{{ detail.birthDate ?? '—' }}</dd></div>
              <div><dt>身高</dt><dd>{{ detail.heightCm ? `${detail.heightCm} cm` : '—' }}</dd></div>
              <div><dt>学历</dt><dd>{{ detail.education ?? '—' }}</dd></div>
              <div><dt>职业</dt><dd>{{ detail.occupation ?? '—' }}</dd></div>
              <div><dt>年薪</dt><dd>{{ detail.incomeRange ?? '—' }}</dd></div>
              <div><dt>所在城市</dt><dd>{{ detail.city ?? '—' }}</dd></div>
              <div><dt>微信号</dt><dd>{{ detail.wechatId ?? '—' }}</dd></div>
              <div><dt>抖音号</dt><dd>{{ detail.douyinId ?? '—' }}</dd></div>
              <div><dt>抖音昵称</dt><dd>{{ detail.douyinNickname ?? '—' }}</dd></div>
              <div class="wide"><dt>抖音主页</dt><dd>{{ detail.douyinProfileUrl ?? '—' }}</dd></div>
              <div><dt>提交时间</dt><dd>{{ detail.submittedAt }}</dd></div>
              <div><dt>审核截止</dt><dd>{{ detail.reviewDeadlineAt }}</dd></div>
            </dl>
          </section>
          <section class="archive-panel photo-panel">
            <div class="section-heading"><h2>照片资料</h2><span>共 {{ detail.photos.length }} 张</span></div>
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
          </section>
          <section class="archive-panel difference-panel">
            <div class="section-heading"><h2>与最后已通过版本的差异</h2><span>版本 {{ detail.lastApprovedRevisionId ?? '首次提交' }} → {{ detail.revisionNumber }}</span></div>
            <div v-if="detail.differences.length" class="difference-list">
              <div v-for="difference in detail.differences" :key="difference.fieldCode" class="difference-row">
                <strong>{{ difference.fieldLabel }}</strong><span class="old-value">{{ difference.oldValue ?? '—' }}</span><b>→</b><span class="new-value">{{ difference.newValue ?? '—' }}</span>
              </div>
            </div>
            <el-empty v-else description="与最后已通过版本无差异（或尚无已通过版本）" />
          </section>
        </main>
        <aside class="action-column">
          <section class="archive-panel action-panel">
            <h2>审核操作</h2>
            <template v-if="detail.status === 'PENDING'">
              <el-button type="primary" :loading="deciding" @click="approve">通过</el-button>
              <el-input v-model="rejectComment" type="textarea" :rows="5" maxlength="200" show-word-limit placeholder="退回时请填写面向嘉宾的说明" />
              <el-button :loading="deciding" @click="reject">退回</el-button>
            </template>
            <div v-else class="completed-state"><StatusTag :status="detail.status" /><p>该版本审核已经完成，结果不可更改。</p></div>
          </section>
          <section class="archive-panel policy-panel"><h2>审核须知</h2><ol><li>核对档案信息是否真实完整。</li><li>退回时说明需清晰、可执行。</li><li>审核结果提交后不可修改。</li></ol></section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-header {
  margin: -24px -24px 20px;
  padding: 14px 24px;
  display: grid;
  grid-template-columns: 100px 1fr auto;
  align-items: center;
  border-bottom: 1px solid var(--archive-line);
  background: #ffffff;
}
.detail-header h1 {
  display: inline;
  margin: 0 12px 0 0;
  font-size: 18px;
}
.detail-header span {
  color: var(--archive-muted);
  font-size: 11px;
}
.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 16px;
}
.detail-main,
.action-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.info-panel,
.photo-panel,
.difference-panel,
.action-panel,
.policy-panel {
  padding: 18px;
}
.section-heading {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-heading h2,
.action-panel h2,
.policy-panel h2 {
  margin: 0;
  font-size: 14px;
}
.section-heading > span {
  color: var(--archive-muted);
  font-size: 11px;
}
.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  margin: 0;
  border: 1px solid var(--archive-line);
  border-radius: 6px;
  overflow: hidden;
}
.info-grid div {
  min-height: 54px;
  padding: 10px 12px;
  border-right: 1px solid var(--archive-line);
  border-bottom: 1px solid var(--archive-line);
}
.info-grid div:nth-child(even),
.info-grid .wide {
  border-right: 0;
}
.info-grid .wide {
  grid-column: 1 / -1;
}
.info-grid dt {
  color: var(--archive-muted);
  font-size: 10px;
}
.info-grid dd {
  margin: 5px 0 0;
  font-size: 12px;
}
.photos {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.photo {
  width: 106px;
  height: 82px;
  border-radius: 6px;
}
.difference-list {
  border: 1px solid var(--archive-line);
  border-radius: 6px;
  overflow: hidden;
}
.difference-row {
  min-height: 46px;
  padding: 10px 12px;
  display: grid;
  grid-template-columns: 120px 1fr 28px 1fr;
  align-items: center;
  border-bottom: 1px solid var(--archive-line);
  font-size: 12px;
}
.difference-row:last-child {
  border-bottom: 0;
}
.difference-row b {
  text-align: center;
  color: #9b9ca0;
}
.old-value {
  color: #7c7d81;
}
.new-value {
  color: #15803d;
}
.action-panel {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.action-panel h2 {
  margin-bottom: 4px;
}
.completed-state {
  padding: 12px;
  border-radius: 6px;
  background: #f7f7f5;
}
.completed-state p,
.policy-panel li {
  color: var(--archive-muted);
  font-size: 11px;
  line-height: 1.7;
}
.policy-panel ol {
  margin: 12px 0 0;
  padding-left: 18px;
}
@media (max-width: 1100px) {
  .detail-header {
    margin: -18px -18px 18px;
    padding-inline: 18px;
  }
}
</style>

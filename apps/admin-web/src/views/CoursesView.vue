<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MdEditor, config, zh_CN } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import {
  archiveCourse,
  courseDetail,
  createCourse,
  deleteCourse,
  listCourseCollections,
  listCourses,
  publishCourse,
  updateCourse,
  uploadCourseImage,
  uploadCourseMarkdown,
} from '@/api/admin'
import {
  courseContentTypeLabel,
  courseStatusMeta,
  durationLabel,
  fileSizeLabel,
  minuteLabel,
} from '@/utils/presentation'
import { useCourseVideoUpload } from '@/composables/useCourseVideoUpload'
import type {
  AdminCourseCollectionView,
  AdminCourseListItem,
  CourseContentType,
  CourseStatus,
  SaveCoursePayload,
} from '@/types'
import PageHeader from '@/components/PageHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import TableToolbar from '@/components/TableToolbar.vue'
import EmptyState from '@/components/EmptyState.vue'
import StatusTag from '@/components/StatusTag.vue'

config({ editorConfig: { languageUserDefined: { 'zh-CN': zh_CN } } })

const CONTENT_TYPES: Array<{ value: CourseContentType; label: string }> = [
  { value: 'ARTICLE', label: '图文' },
  { value: 'VIDEO', label: '视频' },
  { value: 'TEXT', label: '纯文本' },
]
const STATUSES: Array<{ value: CourseStatus; label: string }> = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'ARCHIVED', label: '已下架' },
]

const collections = ref<AdminCourseCollectionView[]>([])
const courses = ref<AdminCourseListItem[]>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)

const query = reactive({
  collectionId: undefined as number | undefined,
  contentType: undefined as CourseContentType | undefined,
  status: undefined as CourseStatus | undefined,
  keyword: '',
  page: 1,
  size: 20,
})

const drawerOpen = ref(false)
const editingId = ref<number | null>(null)
const markdownInput = ref<HTMLInputElement | null>(null)
const coverInput = ref<HTMLInputElement | null>(null)
const videoInput = ref<HTMLInputElement | null>(null)

const videoUpload = useCourseVideoUpload()

const form = reactive({
  collectionId: null as number | null,
  title: '',
  subtitle: '',
  summary: '',
  authorName: '',
  contentType: 'ARTICLE' as CourseContentType,
  contentMarkdown: '',
  coverObjectKey: null as string | null,
  coverPreviewUrl: null as string | null,
  videoObjectKey: null as string | null,
  videoSizeBytes: null as number | null,
  videoContentType: null as string | null,
  videoDurationSeconds: null as number | null,
  videoPreviewUrl: null as string | null,
  sortOrder: 0,
  expectedVersion: null as number | null,
})

const drawerTitle = computed(() => (editingId.value === null ? '新增课程' : '编辑课程'))
const needsBody = computed(() => form.contentType !== 'VIDEO')
const isVideo = computed(() => form.contentType === 'VIDEO')

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await listCourses({
      collectionId: query.collectionId,
      contentType: query.contentType,
      status: query.status,
      keyword: query.keyword.trim() || undefined,
      page: query.page,
      size: query.size,
    })
    courses.value = page.items
    total.value = page.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '课程加载失败')
  } finally {
    loading.value = false
  }
}

async function loadCollections(): Promise<void> {
  try {
    collections.value = await listCourseCollections()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '目录加载失败')
  }
}

function search(): void {
  query.page = 1
  void load()
}

function reset(): void {
  query.collectionId = undefined
  query.contentType = undefined
  query.status = undefined
  query.keyword = ''
  query.page = 1
  void load()
}

function openCreate(): void {
  editingId.value = null
  form.collectionId = collections.value[0]?.id ?? null
  form.title = ''
  form.subtitle = ''
  form.summary = ''
  form.authorName = ''
  form.contentType = 'ARTICLE'
  form.contentMarkdown = ''
  form.coverObjectKey = null
  form.coverPreviewUrl = null
  form.videoObjectKey = null
  form.videoSizeBytes = null
  form.videoContentType = null
  form.videoDurationSeconds = null
  form.videoPreviewUrl = null
  form.sortOrder = 0
  form.expectedVersion = null
  void videoUpload.cancel()
  drawerOpen.value = true
}

async function openEdit(row: AdminCourseListItem): Promise<void> {
  try {
    const detail = await courseDetail(row.id)
    editingId.value = detail.id
    form.collectionId = detail.collectionId
    form.title = detail.title
    form.subtitle = detail.subtitle ?? ''
    form.summary = detail.summary ?? ''
    form.authorName = detail.authorName ?? ''
    form.contentType = detail.contentType
    form.contentMarkdown = detail.contentMarkdown ?? ''
    form.coverObjectKey = detail.coverObjectKey
    form.coverPreviewUrl = detail.coverPreviewUrl
    form.videoObjectKey = detail.videoObjectKey
    form.videoSizeBytes = detail.videoSizeBytes
    form.videoContentType = detail.videoContentType
    form.videoDurationSeconds = detail.videoDurationSeconds
    form.videoPreviewUrl = detail.videoPreviewUrl
    form.sortOrder = detail.sortOrder
    form.expectedVersion = detail.version
    void videoUpload.cancel()
    drawerOpen.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '课程详情加载失败')
  }
}

/** 编辑器的图片上传钩子：传到 COS，再把签名地址插回 markdown。 */
async function onUploadImg(
  files: File[],
  callback: (urls: string[]) => void,
): Promise<void> {
  try {
    const assets = await Promise.all(files.map((file) => uploadCourseImage(file)))
    callback(assets.map((asset) => asset.previewUrl))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '插图上传失败')
    callback([])
  }
}

/**
 * 导入 `.md` 文件。会覆盖编辑器里已有的正文，所以先确认——
 * 默默吞掉别人写了一半的稿子是不可接受的。
 */
async function importMarkdown(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  if (form.contentMarkdown.trim()) {
    try {
      await ElMessageBox.confirm(
        '导入会覆盖当前正文，确定继续吗？',
        '导入 Markdown',
        { type: 'warning' },
      )
    } catch {
      return
    }
  }
  try {
    const asset = await uploadCourseMarkdown(file)
    form.contentMarkdown = asset.content
    ElMessage.success('Markdown 已导入编辑器，保存后才会入库')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Markdown 导入失败')
  }
}

async function pickCover(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  try {
    const asset = await uploadCourseImage(file)
    form.coverObjectKey = asset.objectKey
    form.coverPreviewUrl = asset.previewUrl
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '封面上传失败')
  }
}

/**
 * 选视频即开始切片上传。时长在上传过程中由 `<video>` 元数据读出来，
 * **只登记，不做任何范围校验、也不给任何提示**（产品口径）。
 */
async function pickVideo(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  const uploaded = await videoUpload.upload(file)
  if (uploaded) {
    form.videoObjectKey = uploaded.objectKey
    form.videoSizeBytes = uploaded.sizeBytes
    form.videoContentType = uploaded.contentType
    form.videoDurationSeconds = uploaded.durationSeconds
    // 后端在 complete 时一并签了回放地址，保存前就能确认传对了文件。
    form.videoPreviewUrl = uploaded.previewUrl
  }
}

async function save(): Promise<void> {
  if (form.collectionId === null) {
    ElMessage.warning('请选择所属目录')
    return
  }
  if (!form.title.trim()) {
    ElMessage.warning('请填写教材名称')
    return
  }
  if (needsBody.value && !form.contentMarkdown.trim()) {
    ElMessage.warning('图文与纯文本课程必须填写正文')
    return
  }
  if (isVideo.value && !form.videoObjectKey) {
    ElMessage.warning('请先上传视频')
    return
  }

  const payload: SaveCoursePayload = {
    collectionId: form.collectionId,
    title: form.title.trim(),
    subtitle: form.subtitle.trim() || null,
    summary: form.summary.trim() || null,
    authorName: form.authorName.trim() || null,
    contentType: form.contentType,
    contentMarkdown: form.contentMarkdown.trim() || null,
    coverObjectKey: form.coverObjectKey,
    videoObjectKey: form.videoObjectKey,
    videoDurationSeconds: form.videoDurationSeconds,
    videoSizeBytes: form.videoSizeBytes,
    videoContentType: form.videoContentType,
    sortOrder: form.sortOrder,
  }

  saving.value = true
  try {
    if (editingId.value === null) {
      await createCourse(payload)
      ElMessage.success('课程已创建，还是草稿状态')
    } else {
      await updateCourse(editingId.value, {
        ...payload,
        expectedVersion: form.expectedVersion ?? 0,
      })
      ElMessage.success('课程已保存')
    }
    drawerOpen.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function publish(row: AdminCourseListItem): Promise<void> {
  await act(() => publishCourse(row.id), '课程已发布，访客端会员可见')
}

async function archive(row: AdminCourseListItem): Promise<void> {
  await act(() => archiveCourse(row.id), '课程已下架')
}

async function remove(row: AdminCourseListItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除《${row.title}》吗？封面与视频文件会一起删除，此操作不可恢复。`,
      '删除课程',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await act(() => deleteCourse(row.id), '课程已删除')
}

async function act(action: () => Promise<unknown>, message: string): Promise<void> {
  try {
    await action()
    ElMessage.success(message)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

onMounted(async () => {
  await loadCollections()
  await load()
})
</script>

<template>
  <div class="courses-page">
    <PageHeader
      title="课程管理"
      subtitle="课程按目录分类，发布后仅会员可查看正文与视频。草稿与已下架的课程访客端看不到。"
    />

    <FilterBar :loading="loading" @search="search" @reset="reset">
      <template #fields>
        <label class="field">
          <span>目录</span>
          <el-select v-model="query.collectionId" placeholder="全部目录" clearable>
            <el-option
              v-for="item in collections"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </label>
        <label class="field">
          <span>类型</span>
          <el-select v-model="query.contentType" placeholder="全部类型" clearable>
            <el-option
              v-for="item in CONTENT_TYPES"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </label>
        <label class="field">
          <span>状态</span>
          <el-select v-model="query.status" placeholder="全部状态" clearable>
            <el-option
              v-for="item in STATUSES"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </label>
        <label class="field">
          <span>关键词</span>
          <el-input
            v-model="query.keyword"
            placeholder="教材名称或副标题"
            clearable
            @keyup.enter="search"
          />
        </label>
      </template>
    </FilterBar>

    <TableToolbar :selected-count="0" @refresh="load">
      <template #hint>共 {{ total }} 门课程</template>
      <template #actions>
        <el-button type="primary" @click="openCreate">新增课程</el-button>
      </template>
    </TableToolbar>

    <section v-loading="loading" class="archive-panel">
      <el-table v-if="courses.length > 0" :data="courses" row-key="id">
        <el-table-column prop="title" label="教材名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="collectionName" label="目录" width="130" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ courseContentTypeLabel(row.contentType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag
              :label="courseStatusMeta(row.status).label"
              :tone="courseStatusMeta(row.status).tone"
            />
          </template>
        </el-table-column>
        <el-table-column label="讲师" width="110">
          <template #default="{ row }">{{ row.authorName || '—' }}</template>
        </el-table-column>
        <el-table-column label="时长" width="90">
          <template #default="{ row }">
            <span class="tabular">{{ durationLabel(row.videoDurationSeconds) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="发布时间" width="170">
          <template #default="{ row }">{{ minuteLabel(row.publishedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status !== 'PUBLISHED'" link @click="publish(row)">发布</el-button>
            <el-button v-else link @click="archive(row)">下架</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <EmptyState
        v-else-if="!loading"
        title="没有符合条件的课程"
        hint="换个筛选条件，或者点右上角「新增课程」建一门。新建的课程是草稿，发布后访客端才看得到。"
      />

      <el-pagination
        v-if="total > query.size"
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="prev, pager, next, total"
        class="pager"
        @current-change="load"
      />
    </section>

    <el-drawer v-model="drawerOpen" :title="drawerTitle" size="820px">
      <el-form label-position="top">
        <div class="grid">
          <el-form-item label="所属目录">
            <el-select v-model="form.collectionId" placeholder="请选择">
              <el-option
                v-for="item in collections"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="课程类型">
            <el-radio-group v-model="form.contentType">
              <el-radio-button
                v-for="item in CONTENT_TYPES"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>

        <el-form-item label="教材名称">
          <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="课程标题" />
        </el-form-item>
        <div class="grid">
          <el-form-item label="副标题">
            <el-input v-model="form.subtitle" maxlength="200" placeholder="选填" />
          </el-form-item>
          <el-form-item label="讲师">
            <el-input v-model="form.authorName" maxlength="64" placeholder="选填" />
          </el-form-item>
        </div>
        <el-form-item label="简介">
          <el-input
            v-model="form.summary"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="列表页会展示这段话"
          />
        </el-form-item>

        <el-form-item label="封面">
          <div class="asset-row">
            <img v-if="form.coverPreviewUrl" :src="form.coverPreviewUrl" alt="封面" class="cover" />
            <el-button @click="coverInput?.click()">
              {{ form.coverObjectKey ? '更换封面' : '上传封面' }}
            </el-button>
            <input
              ref="coverInput"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              class="hidden-input"
              @change="pickCover"
            />
          </div>
        </el-form-item>

        <!-- 视频区 -->
        <el-form-item v-if="isVideo" label="视频">
          <div class="video-panel">
            <div class="asset-row">
              <el-button @click="videoInput?.click()">
                {{ form.videoObjectKey ? '重新上传' : '选择视频' }}
              </el-button>
              <input
                ref="videoInput"
                type="file"
                accept="video/mp4,video/quicktime,video/webm"
                class="hidden-input"
                @change="pickVideo"
              />
              <span class="hint">支持 MP4 / MOV / WebM，浏览器分块上传，大文件可断点重试</span>
            </div>

            <div v-if="videoUpload.phase.value === 'uploading'" class="progress">
              <el-progress :percentage="videoUpload.percent.value" />
              <span class="hint">
                {{ videoUpload.filename.value }} ·
                第 {{ videoUpload.donePartCount.value }} / {{ videoUpload.totalParts.value }} 块
              </span>
              <el-button link type="danger" @click="videoUpload.cancel()">取消上传</el-button>
            </div>

            <el-alert
              v-else-if="videoUpload.phase.value === 'failed'"
              type="error"
              :closable="false"
              show-icon
              :title="videoUpload.errorMessage.value"
              description="已经传完的分块还留着，重试会从断掉那一块继续，不会从头再来。"
            >
              <template #default>
                <el-button link type="primary" @click="videoUpload.retry()">重试</el-button>
              </template>
            </el-alert>

            <div v-if="form.videoObjectKey" class="video-meta">
              <span>已上传</span>
              <span class="tabular">{{ fileSizeLabel(form.videoSizeBytes) }}</span>
              <span class="tabular">时长 {{ durationLabel(form.videoDurationSeconds) }}</span>
            </div>

            <!--
              回放预览。新传完的视频与已保存课程的视频都走这里：前者的地址由后端在
              complete 时签发，后者来自课程详情的 videoPreviewUrl。
              两者都是短时签名地址，抽屉开太久会过期——所以给一句提示而不是让人对着
              一个放不出来的播放器猜。
            -->
            <div v-if="form.videoPreviewUrl" class="video-preview">
              <video
                :src="form.videoPreviewUrl"
                controls
                preload="metadata"
                class="preview-player"
              />
              <span class="hint">预览地址有效期约 30 分钟，过期后重新打开本页即可刷新</span>
            </div>
            <el-alert
              v-else-if="form.videoObjectKey"
              type="info"
              :closable="false"
              show-icon
              title="这条视频暂时无法预览"
              description="对象已在存储里，但没有拿到可回放的地址。重新打开本页会重新签发；若仍不行，检查后端对象存储配置。"
            />
          </div>
        </el-form-item>

        <!-- 正文区：图文与纯文本必填，视频课作为简介可选 -->
        <el-form-item :label="needsBody ? '正文（Markdown）' : '视频简介（Markdown，选填）'">
          <div class="editor-wrap">
            <div class="editor-actions">
              <el-button size="small" @click="markdownInput?.click()">导入 .md 文件</el-button>
              <input
                ref="markdownInput"
                type="file"
                accept=".md,.markdown,text/markdown"
                class="hidden-input"
                @change="importMarkdown"
              />
              <span class="hint">支持 Markdown 语法，插图会上传到对象存储</span>
            </div>
            <MdEditor
              v-model="form.contentMarkdown"
              language="zh-CN"
              :preview="true"
              style="height: 420px"
              @on-upload-img="onUploadImg"
            />
          </div>
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" :controls="false" />
          <span class="hint">数字小的排在前面</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="drawerOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.archive-panel {
  padding: var(--ds-space-4);
}
.field {
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-1);
  min-width: 180px;
}
.field > span {
  color: var(--ds-text-secondary);
  font-size: var(--ds-caption-size);
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--ds-space-4);
}
.asset-row {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
  flex-wrap: wrap;
}
.hidden-input {
  display: none;
}
.cover {
  width: 96px;
  height: 64px;
  object-fit: cover;
  border-radius: var(--ds-radius-control);
  border: 1px solid var(--ds-line);
}
.video-panel {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-3);
}
.progress {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
}
.progress :deep(.el-progress) {
  flex: 1;
}
.video-meta {
  display: flex;
  gap: var(--ds-space-4);
  color: var(--ds-text-secondary);
  font-size: var(--ds-caption-size);
}
.video-preview {
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-2);
}
.preview-player {
  width: 100%;
  max-width: 420px;
  max-height: 260px;
  border-radius: var(--ds-radius-control);
  background: #000;
}
.editor-wrap {
  width: 100%;
}
.editor-actions {
  margin-bottom: var(--ds-space-2);
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
}
.hint {
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
.pager {
  margin-top: var(--ds-space-4);
  justify-content: flex-end;
}
</style>

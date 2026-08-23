<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createCourseCollection,
  listCourseCollections,
  updateCourseCollection,
} from '@/api/admin'
import { courseCollectionStatusMeta, minuteLabel } from '@/utils/presentation'
import type { AdminCourseCollectionView } from '@/types'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import TableToolbar from '@/components/TableToolbar.vue'

const collections = ref<AdminCourseCollectionView[]>([])
const loading = ref(false)
const saving = ref(false)
const drawerOpen = ref(false)
/** null = 新建。 */
const editingId = ref<number | null>(null)

const form = reactive({ name: '', description: '', sortOrder: 0 })

const drawerTitle = computed(() => (editingId.value === null ? '新增合集' : '编辑合集'))

async function load(): Promise<void> {
  loading.value = true
  try {
    collections.value = await listCourseCollections()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '合集加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  form.name = ''
  form.description = ''
  // 排在现有合集之后，运营不用自己想这个数字。
  form.sortOrder = collections.value.reduce((max, item) => Math.max(max, item.sortOrder), 0) + 10
  drawerOpen.value = true
}

function openEdit(collection: AdminCourseCollectionView): void {
  editingId.value = collection.id
  form.name = collection.name
  form.description = collection.description ?? ''
  form.sortOrder = collection.sortOrder
  drawerOpen.value = true
}

async function save(): Promise<void> {
  if (!form.name.trim()) {
    ElMessage.warning('请填写合集名称')
    return
  }
  saving.value = true
  try {
    if (editingId.value === null) {
      await createCourseCollection({
        name: form.name.trim(),
        description: form.description.trim() || null,
        sortOrder: form.sortOrder,
      })
      ElMessage.success('合集已创建')
    } else {
      await updateCourseCollection(editingId.value, {
        name: form.name.trim(),
        description: form.description.trim() || null,
        sortOrder: form.sortOrder,
      })
      ElMessage.success('合集已保存')
    }
    drawerOpen.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

/**
 * 隐藏 / 显示。合集没有删除——它下面挂着课程，后端也没给运行账号 DELETE 权限，
 * 所以这一页不提供删除入口，下线一律用隐藏。
 */
async function toggleVisibility(collection: AdminCourseCollectionView): Promise<void> {
  const next = collection.status === 'ACTIVE' ? 'HIDDEN' : 'ACTIVE'
  try {
    await updateCourseCollection(collection.id, { status: next })
    ElMessage.success(next === 'HIDDEN' ? '合集已隐藏，访客端不再展示' : '合集已恢复显示')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="course-collections-page">
    <PageHeader
      title="课程合集"
      subtitle="课程按合集分类展示。隐藏后访客端不再出现这个合集及其课程，已有课程不受影响。"
    />

    <TableToolbar :selected-count="0" @refresh="load">
      <template #hint>共 {{ collections.length }} 个合集</template>
      <template #actions>
        <el-button type="primary" @click="openCreate">新增合集</el-button>
      </template>
    </TableToolbar>

    <section v-loading="loading" class="archive-panel">
      <el-table v-if="collections.length > 0" :data="collections" row-key="id">
        <el-table-column prop="name" label="合集名称" min-width="160" />
        <el-table-column prop="description" label="说明" min-width="240">
          <template #default="{ row }">{{ row.description || '—' }}</template>
        </el-table-column>
        <el-table-column label="课程数" width="120">
          <template #default="{ row }">
            <span class="tabular">{{ row.publishedCourseCount }} / {{ row.courseCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag
              :label="courseCollectionStatusMeta(row.status).label"
              :tone="courseCollectionStatusMeta(row.status).tone"
            />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ minuteLabel(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link @click="toggleVisibility(row)">
              {{ row.status === 'ACTIVE' ? '隐藏' : '显示' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <EmptyState
        v-else-if="!loading"
        title="还没有合集"
        hint="课程必须挂在某个合集下。迁移已经种了四个默认分类，如果这里是空的，说明数据被清过。"
      />
    </section>

    <el-drawer v-model="drawerOpen" :title="drawerTitle" size="420px">
      <el-form label-position="top">
        <el-form-item label="合集名称">
          <el-input v-model="form.name" maxlength="64" show-word-limit placeholder="例如：情绪与认知" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="一句话说清这个合集讲什么"
          />
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
.hint {
  margin-left: var(--ds-space-3);
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
</style>

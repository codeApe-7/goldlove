<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createFieldDefinition, listFieldDefinitions, updateFieldDefinition } from '@/api/admin'
import type { ProfileFieldDefinitionView } from '@/types'
import PageHeader from '@/components/PageHeader.vue'
import TableToolbar from '@/components/TableToolbar.vue'
import EmptyState from '@/components/EmptyState.vue'
import { fieldTypeLabel, orDash, storageKindLabel } from '@/utils/presentation'

const items = ref<ProfileFieldDefinitionView[]>([])
const total = ref(0)
const page = reactive({ current: 1, size: 20 })
const drawerVisible = ref(false)
const editing = ref<ProfileFieldDefinitionView | null>(null)
const saving = ref(false)
const loading = ref(false)

const form = reactive({
  fieldCode: '',
  label: '',
  dataType: 'TEXT',
  required: false,
  enabled: true,
  optionsText: '',
  sortOrder: 0,
  instructions: '',
})

/**
 * 核心字段的必填与启用是锁死的：后端 ProfileFieldDefinitionService 对 CORE
 * 改这两项会直接抛 FIELD_DEFINITION_IMMUTABLE。这里把开关置灰，而不是让人点完再吃 409。
 */
const coreLocked = computed(() => editing.value?.storageKind === 'CORE')

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listFieldDefinitions(page.current, page.size)
    items.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '字段列表加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  Object.assign(form, {
    fieldCode: '',
    label: '',
    dataType: 'TEXT',
    required: false,
    enabled: true,
    optionsText: '',
    sortOrder: 0,
    instructions: '',
  })
  drawerVisible.value = true
}

function openEdit(row: ProfileFieldDefinitionView): void {
  editing.value = row
  Object.assign(form, {
    fieldCode: row.fieldCode,
    label: row.label,
    dataType: row.dataType,
    required: row.required,
    enabled: row.enabled,
    optionsText: row.options.join('\n'),
    sortOrder: row.sortOrder,
    instructions: row.instructions ?? '',
  })
  drawerVisible.value = true
}

async function save(): Promise<void> {
  saving.value = true
  try {
    const options = form.optionsText
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
    if (editing.value) {
      await updateFieldDefinition(editing.value.id, {
        expectedVersion: editing.value.version,
        label: form.label,
        // CORE 字段不接受这两项变更，原样回传当前值，避免触发不可变校验。
        required: coreLocked.value ? editing.value.required : form.required,
        enabled: coreLocked.value ? editing.value.enabled : form.enabled,
        options,
        sortOrder: form.sortOrder,
        instructions: form.instructions,
      })
      ElMessage.success('字段已更新')
    } else {
      await createFieldDefinition({
        fieldCode: form.fieldCode,
        label: form.label,
        dataType: form.dataType,
        required: form.required,
        options,
        sortOrder: form.sortOrder,
        instructions: form.instructions,
      })
      ElMessage.success('字段已创建')
    }
    drawerVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
<template>
  <div class="field-page">
    <PageHeader title="字段配置" subtitle="核心字段固定存在；自定义字段可按业务需要新增。" />

    <TableToolbar :selected-count="0" @refresh="load">
      <template #hint>共 {{ total }} 个字段</template>
      <template #actions>
        <el-button type="primary" @click="openCreate">新建字段</el-button>
      </template>
    </TableToolbar>

    <section class="archive-panel table-panel">
      <el-table :data="items" v-loading="loading" row-key="id">
        <el-table-column prop="fieldCode" label="字段标识" width="160">
          <template #default="{ row }"><span class="mono">{{ row.fieldCode }}</span></template>
        </el-table-column>
        <el-table-column prop="label" label="字段名称" min-width="140" />
        <el-table-column label="字段类型" width="110">
          <template #default="{ row }">{{ fieldTypeLabel(row.dataType) }}</template>
        </el-table-column>
        <el-table-column label="存储" width="90">
          <template #default="{ row }"><span class="kind-pill">{{ storageKindLabel(row.storageKind) }}</span></template>
        </el-table-column>
        <el-table-column label="必填" width="70">
          <template #default="{ row }"><el-switch :model-value="row.required" disabled /></template>
        </el-table-column>
        <el-table-column label="启用" width="70">
          <template #default="{ row }"><el-switch :model-value="row.enabled" disabled /></template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="顺序" width="70" />
        <el-table-column label="说明" min-width="160">
          <template #default="{ row }">{{ orDash(row.instructions) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState title="暂无字段" hint="核心字段由数据库迁移种下，这里可以新增自定义字段" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="page.current"
        :page-size="page.size"
        :total="total"
        layout="total, prev, pager, next"
        class="pagination"
        @current-change="load"
      />
    </section>
    <el-drawer
      v-model="drawerVisible"
      :title="editing ? '编辑字段' : '新建字段'"
      size="440px"
    >
      <el-form label-position="top">
        <el-form-item label="字段名称" required>
          <el-input v-model="form.label" placeholder="请输入字段名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="字段标识">
          <el-input
            v-model="form.fieldCode"
            :disabled="editing !== null"
            placeholder="英文 / 数字 / 下划线，小写字母开头"
          />
        </el-form-item>
        <el-form-item label="字段类型">
          <el-select v-model="form.dataType" :disabled="editing !== null" style="width: 100%">
            <el-option label="文本" value="TEXT" />
            <el-option label="长文本" value="LONG_TEXT" />
            <el-option label="整数" value="INTEGER" />
            <el-option label="小数" value="DECIMAL" />
            <el-option label="日期" value="DATE" />
            <el-option label="布尔" value="BOOLEAN" />
            <el-option label="单选" value="SINGLE_OPTION" />
          </el-select>
        </el-form-item>
        <div class="switch-row">
          <el-form-item label="是否必填">
            <el-switch v-model="form.required" :disabled="coreLocked" />
          </el-form-item>
          <el-form-item v-if="editing" label="是否启用">
            <el-switch v-model="form.enabled" :disabled="coreLocked" />
          </el-form-item>
        </div>
        <el-form-item label="选项（每行一个）">
          <el-input v-model="form.optionsText" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input
            v-model="form.instructions"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请输入备注说明（选填）"
          />
        </el-form-item>

        <el-alert
          v-if="coreLocked"
          type="info"
          :closable="false"
          show-icon
          title="核心字段只能改名称、选项、排序与说明"
          description="必填与启用由数据库触发器锁住——核心字段的取值存在 guest_profile 的具名列上，关掉它会让访客端表单与库表对不上。"
        />
        <el-alert
          v-else-if="editing"
          type="warning"
          :closable="false"
          show-icon
          title="关闭启用后，访客端表单立刻不再显示这个字段"
          description="已经填过的值仍留在库里，重新启用即可见。"
        />
      </el-form>

      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
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
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: var(--ds-caption-size);
}
.kind-pill {
  padding: 3px 7px;
  border-radius: var(--ds-radius-control);
  background: #f3f4f6;
  color: var(--ds-text-secondary);
  font-size: var(--ds-caption-size);
}
.switch-row {
  display: flex;
  gap: var(--ds-space-6);
}
</style>

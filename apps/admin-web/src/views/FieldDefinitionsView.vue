<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createFieldDefinition, listFieldDefinitions, updateFieldDefinition } from '@/api/admin'
import type { ProfileFieldDefinitionView } from '@/types'

const items = ref<ProfileFieldDefinitionView[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const dialogVisible = ref(false)
const editing = ref<ProfileFieldDefinitionView | null>(null)
const saving = ref(false)
const form = reactive({
  fieldCode: '',
  label: '',
  dataType: 'TEXT',
  required: false,
  optionsText: '',
  sortOrder: 0,
  instructions: '',
})

async function load(): Promise<void> {
  const result = await listFieldDefinitions(page.value, size)
  items.value = result.items
  total.value = result.total
}

function openCreate(): void {
  editing.value = null
  Object.assign(form, {
    fieldCode: '',
    label: '',
    dataType: 'TEXT',
    required: false,
    optionsText: '',
    sortOrder: 0,
    instructions: '',
  })
  dialogVisible.value = true
}

function openEdit(row: ProfileFieldDefinitionView): void {
  editing.value = row
  Object.assign(form, {
    fieldCode: row.fieldCode,
    label: row.label,
    dataType: row.dataType,
    required: row.required,
    optionsText: row.options.join('\n'),
    sortOrder: row.sortOrder,
    instructions: row.instructions ?? '',
  })
  dialogVisible.value = true
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
        required: form.required,
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
    dialogVisible.value = false
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
  <div>
    <h2 class="page-title">字段配置</h2>
    <el-card>
      <template #header>
        <div class="header-row">
          <span>档案字段定义</span>
          <el-button type="primary" @click="openCreate">新增字段</el-button>
        </div>
      </template>
      <el-table :data="items" stripe>
        <el-table-column prop="fieldCode" label="字段代码" width="160" />
        <el-table-column prop="label" label="名称" />
        <el-table-column prop="dataType" label="类型" width="120" />
        <el-table-column prop="storageKind" label="存储" width="100" />
        <el-table-column label="必填" width="80">
          <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">{{ row.enabled ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        class="pager"
        @current-change="load"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑字段' : '新增字段'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="字段代码">
          <el-input
            v-model="form.fieldCode"
            :disabled="editing !== null"
            placeholder="小写字母开头"
          />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.label" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.dataType" :disabled="editing !== null">
            <el-option label="文本" value="TEXT" />
            <el-option label="长文本" value="LONG_TEXT" />
            <el-option label="整数" value="INTEGER" />
            <el-option label="小数" value="DECIMAL" />
            <el-option label="日期" value="DATE" />
            <el-option label="布尔" value="BOOLEAN" />
            <el-option label="单选" value="SINGLE_OPTION" />
          </el-select>
        </el-form-item>
        <el-form-item label="必填">
          <el-switch v-model="form.required" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" />
        </el-form-item>
        <el-form-item label="选项（每行一个）">
          <el-input v-model="form.optionsText" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.instructions" type="textarea" :rows="2" />
        </el-form-item>
        <el-alert
          v-if="editing?.storageKind === 'CORE'"
          type="info"
          :closable="false"
          title="核心字段仅可调整名称、选项、排序与说明"
        />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 18px;
  color: var(--love-deep);
}
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'

/**
 * 停用 / 启用确认弹窗（规范图 7.2）。备注是选填的，会写进 audit_log.metadata。
 * 停用与启用共用一个弹窗——形态一致，只是文案与按钮色不同。
 */
const props = defineProps<{
  modelValue: boolean
  mode: 'suspend' | 'activate'
  /** 展示用的档案身份，例如「138****8000（档案 A1B2C3D4）」 */
  target: string
  loading?: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [reason: string]
}>()

const reason = ref('')

// 每次打开都从空白开始，避免把上一次的理由带到下一个账号上。
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      reason.value = ''
    }
  },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'suspend' ? '确认停用账号？' : '确认启用账号？'"
    width="440px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="confirm-body">
      <el-icon class="confirm-icon" :class="mode"><WarningFilled /></el-icon>
      <div class="confirm-copy">
        <p v-if="mode === 'suspend'">
          停用后该手机号无法再登录或修改档案，后台仍可查看。可以再次启用。
        </p>
        <p v-else>启用后该手机号可以重新登录并继续修改档案。</p>
        <p class="confirm-target">{{ target }}</p>
      </div>
    </div>

    <el-input
      v-model="reason"
      type="textarea"
      :rows="3"
      maxlength="200"
      show-word-limit
      :placeholder="mode === 'suspend' ? '输入停用原因或备注说明（选填）' : '输入启用原因或备注说明（选填）'"
    />

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button
        :type="mode === 'suspend' ? 'danger' : 'primary'"
        :loading="loading"
        @click="$emit('confirm', reason)"
      >
        {{ mode === 'suspend' ? '确认停用' : '确认启用' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.confirm-body {
  display: flex;
  gap: var(--ds-space-3);
  margin-bottom: var(--ds-space-4);
}
.confirm-icon {
  flex: none;
  font-size: 20px;
}
.confirm-icon.suspend {
  color: var(--ds-warning);
}
.confirm-icon.activate {
  color: var(--ds-primary);
}
.confirm-copy p {
  margin: 0;
  color: var(--ds-text-secondary);
  font-size: var(--ds-body-size);
  line-height: 1.6;
}
.confirm-target {
  margin-top: var(--ds-space-2) !important;
  color: var(--ds-text) !important;
  font-weight: 500;
}
</style>

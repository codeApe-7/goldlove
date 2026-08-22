<script setup lang="ts">
import { User } from '@element-plus/icons-vue'

/**
 * 右侧详情抽屉（规范图 7.3）。抽屉而不是独立页：从列表点进来时筛选条件与页码都不丢，
 * 关掉就回到原来的位置。头像 + 标题 + 状态标签在头部，操作集中在右侧竖排操作栏。
 */
defineProps<{
  modelValue: boolean
  title: string
  subtitle?: string
  avatarUrl?: string
  loading?: boolean
}>()
defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :with-header="false"
    size="760px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="drawer-body">
      <div class="drawer-main">
        <header class="drawer-head">
          <el-avatar :size="48" :src="avatarUrl" shape="square">
            <!-- 没上传头像时用人形图标，而不是标题首字：标题是手机号，取首字只会显示一个「1」 -->
            <el-icon><User /></el-icon>
          </el-avatar>
          <div class="head-copy">
            <div class="head-title">
              <strong>{{ title }}</strong>
              <slot name="tag" />
            </div>
            <span v-if="subtitle" class="head-subtitle">{{ subtitle }}</span>
          </div>
          <el-button text class="drawer-close" @click="$emit('update:modelValue', false)">
            关闭
          </el-button>
        </header>
        <div class="drawer-content">
          <slot />
        </div>
      </div>
      <aside v-if="$slots.actions" class="drawer-rail">
        <slot name="actions" />
      </aside>
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer-body {
  height: 100%;
  display: flex;
  gap: var(--ds-space-4);
}
.drawer-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.drawer-head {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
  padding-bottom: var(--ds-space-4);
  border-bottom: 1px solid var(--ds-line);
}
.head-copy {
  flex: 1;
  min-width: 0;
}
.head-title {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
}
.head-title strong {
  color: var(--ds-text);
  font-size: var(--ds-h3-size);
  font-weight: var(--ds-h3-weight);
}
.head-subtitle {
  display: block;
  margin-top: var(--ds-space-1);
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
.drawer-close {
  flex: none;
  color: var(--ds-text-muted);
}
.drawer-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-top: var(--ds-space-4);
}
.drawer-rail {
  flex: none;
  width: 116px;
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-2);
  padding-left: var(--ds-space-4);
  border-left: 1px solid var(--ds-line);
}
.drawer-rail :deep(.el-button) {
  width: 100%;
  margin: 0;
}
</style>

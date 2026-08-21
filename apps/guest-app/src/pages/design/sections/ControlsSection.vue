<script setup lang="ts">
import { ref } from 'vue'
import AppButton from '@/components/AppButton.vue'
import AppInput from '@/components/AppInput.vue'

/** 规范图区块 2 · 按钮与输入框全状态。 */
const defaultText = ref('')
const focusText = ref('')
const errorText = ref('')
const successText = ref('已通过')
const disabledText = ref('')
const realName = ref('')
const keyword = ref('')
const password = ref('archive-2026')
const intro = ref('')
</script>

<template>
  <view class="spec-block">
    <text class="spec-block__title">2. 按钮与输入框</text>

    <view class="spec-group">
      <text class="spec-group__title">按钮 / Button</text>

      <view class="spec-row">
        <text class="spec-label">主按钮（默认 / 按下 / 禁用）</text>
        <view class="spec-inline">
          <AppButton>主要按钮</AppButton>
          <AppButton class="force-active">主要按钮</AppButton>
          <AppButton disabled>主要按钮</AppButton>
        </view>
      </view>

      <view class="spec-row">
        <text class="spec-label">次按钮（默认 / 按下 / 禁用）</text>
        <view class="spec-inline">
          <AppButton variant="secondary">次要按钮</AppButton>
          <AppButton variant="secondary" class="force-active">次要按钮</AppButton>
          <AppButton variant="secondary" disabled>次要按钮</AppButton>
        </view>
      </view>

      <view class="spec-row">
        <text class="spec-label">文字按钮（默认 / 按下 / 禁用）</text>
        <view class="spec-inline">
          <AppButton variant="text">文字按钮</AppButton>
          <AppButton variant="text" class="force-active">文字按钮</AppButton>
          <AppButton variant="text" disabled>文字按钮</AppButton>
        </view>
      </view>

      <text class="spec-note">
        「按下」一列用样式类模拟 :active，真机上由手指按压触发。
      </text>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">输入框 / Input</text>

      <view class="spec-row">
        <text class="spec-label">默认</text>
        <AppInput v-model="defaultText" placeholder="请输入内容" />
      </view>

      <view class="spec-row">
        <text class="spec-label">聚焦（带计数器）</text>
        <AppInput v-model="focusText" placeholder="请输入内容" :maxlength="20" class="force-focus" />
      </view>

      <view class="spec-row">
        <text class="spec-label">错误</text>
        <AppInput
          v-model="errorText"
          placeholder="请输入内容"
          :maxlength="20"
          state="error"
          message="请输入正确的内容"
        />
      </view>

      <view class="spec-row">
        <text class="spec-label">成功</text>
        <AppInput
          v-model="successText"
          :maxlength="20"
          state="success"
          message="输入已通过验证"
        />
      </view>

      <view class="spec-row">
        <text class="spec-label">禁用</text>
        <AppInput v-model="disabledText" placeholder="请输入内容" :maxlength="20" disabled />
      </view>

      <view class="spec-row">
        <text class="spec-label">带说明</text>
        <AppInput
          v-model="realName"
          placeholder="请输入真实姓名"
          hint="该信息仅用于身份验证，仅自己可见"
        />
      </view>

      <view class="spec-row">
        <text class="spec-label">带图标</text>
        <AppInput v-model="keyword" placeholder="搜索职业关键词" icon="search" />
      </view>

      <view class="spec-row">
        <text class="spec-label">密码框</text>
        <AppInput v-model="password" type="password" placeholder="请输入密码" />
      </view>

      <view class="spec-row">
        <text class="spec-label">多行输入</text>
        <AppInput
          v-model="intro"
          type="textarea"
          placeholder="请简要介绍自己的基本情况、兴趣爱好、择偶标准等…"
          :maxlength="200"
          hint="最多可输入 200 字"
        />
      </view>

      <text class="spec-note">
        规范图在下拉框的聚焦 / 错误 / 禁用态上也画了 0/20 计数器，那是沿用输入框边框的绘图习惯；
        下拉框不能键入字符，计数器没有意义，故未实现。
      </text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

// 静态陈列需要同时看到默认态与按下态。传给子组件的 class 落在子组件根节点上，
// 根节点带本组件的 scope 属性，所以按下态可以直接选中；输入框的边框在内部节点，需要 :deep。
.force-active.variant-primary {
  background: $ds-graphite;
}

.force-active.variant-secondary {
  border-color: $ds-gray;
  background: $ds-porcelain;
}

.force-active.variant-text {
  color: $ds-ink;
}

.force-focus :deep(.field) {
  @include ds-focus-ring;
}
</style>

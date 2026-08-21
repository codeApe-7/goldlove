<script setup lang="ts">
import { ref } from 'vue'
import AppSelect from '@/components/AppSelect.vue'

/** 规范图区块 3.1–3.6 · 内联下拉框：关闭态、展开态、可搜索、各类单选示例。 */
const EDUCATION = ['博士及以上', '硕士研究生', '大学本科', '大专', '高中及以下', '其他学历']
const OCCUPATION = ['产品经理', '产品运营', '项目经理', '前端开发工程师', '后端开发工程师', '数据分析师']
const INDUSTRY = ['互联网 / IT', '金融 / 投资', '教育 / 培训', '医疗 / 健康', '法律 / 咨询', '政府 / 事业单位', '其他行业']
const INCOME = ['20万以下', '20万-30万', '30万-50万', '50万-80万', '80万-120万', '120万以上']

const closedDefault = ref('')
const closedError = ref('')
const closedDisabled = ref('')
const education = ref('大学本科')
const occupation = ref('')
const industry = ref('互联网 / IT')
const income = ref('30万-50万')
const truncated = ref('海外留学博士后研究人员联合培养项目')
</script>

<template>
  <view class="spec-block">
    <text class="spec-block__title">3. 移动端下拉框 / 选择器（一）</text>

    <view class="spec-group">
      <text class="spec-group__title">3.1 普通下拉框（单选）— 关闭状态</text>

      <view class="spec-row">
        <text class="spec-label">默认</text>
        <AppSelect v-model="closedDefault" :options="EDUCATION" placeholder="请选择学历" />
      </view>

      <view class="spec-row">
        <text class="spec-label">错误</text>
        <AppSelect
          v-model="closedError"
          :options="EDUCATION"
          placeholder="请选择学历"
          state="error"
          message="请选择学历"
        />
      </view>

      <view class="spec-row">
        <text class="spec-label">禁用</text>
        <AppSelect v-model="closedDisabled" :options="EDUCATION" placeholder="请选择学历" disabled />
      </view>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">3.2 下拉菜单 — 展开状态</text>
      <text class="spec-label">点开任意一个下拉框即可看到展开面板：选中项右侧打勾并高亮，超长选项单行截断，面板底部固定提示。</text>
      <AppSelect v-model="education" :options="EDUCATION" placeholder="请选择学历" />
      <view class="spacer" />
      <text class="spec-label">选项过长截断</text>
      <AppSelect
        v-model="truncated"
        :options="[truncated, ...EDUCATION]"
        placeholder="请选择学历"
      />
      <text class="spec-note">已选值不在选项表里时照原样展示，不会被清空——存量档案的学历是自由文本。</text>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">3.3 可搜索下拉框</text>
      <AppSelect
        v-model="occupation"
        :options="OCCUPATION"
        placeholder="请选择职业"
        searchable
        search-placeholder="搜索职业关键词"
      />
    </view>

    <view class="spec-group">
      <text class="spec-group__title">3.4 单选 — 学历示例</text>
      <AppSelect v-model="education" :options="EDUCATION" placeholder="请选择学历" />
    </view>

    <view class="spec-group">
      <text class="spec-group__title">3.5 单选 — 职业（行业）示例</text>
      <AppSelect v-model="industry" :options="INDUSTRY" placeholder="请选择职业" />
    </view>

    <view class="spec-group">
      <text class="spec-group__title">3.6 年薪 / 收入（区间选择）</text>
      <AppSelect
        v-model="income"
        :options="INCOME"
        placeholder="请选择年薪 / 收入"
        footer="收入信息仅作参考，非公开"
      />
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.spacer {
  height: $ds-space-3;
}
</style>

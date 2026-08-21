<script setup lang="ts">
import { ref } from 'vue'
import AppWheelSelect from '@/components/AppWheelSelect.vue'
import AppCascadeField from '@/components/AppCascadeField.vue'
import AppDateField from '@/components/AppDateField.vue'
import AppRadioTiles from '@/components/AppRadioTiles.vue'
import AppTagSelect from '@/components/AppTagSelect.vue'

/** 规范图区块 3.7–3.11 · 底部弹层滚轮、级联、日期、性别格子、多选标签。 */
const INCOME = ['20万以下', '20万-30万', '30万-50万', '50万-80万', '80万-120万', '120万以上', '保密']
const GENDER_ICONS: Record<string, string> = { 男: 'user', 女: 'user', 不公开: 'lock' }
const HOBBY_CATALOG = ['健身', '旅行', '阅读', '摄影', '音乐', '美食', '电影', '烹饪', '羽毛球']

const income = ref('30万-50万')
const privateIncome = ref('保密')
const region = ref('北京市 / 东城区')
const birthDate = ref('1992-03-30')
const gender = ref('男')
const hobbies = ref(['健身', '旅行', '阅读', '摄影'])
</script>

<template>
  <view class="spec-block">
    <text class="spec-block__title">3. 移动端下拉框 / 选择器（二）</text>

    <view class="spec-group">
      <text class="spec-group__title">3.7 底部弹层选择器（移动端）</text>
      <AppWheelSelect
        v-model="income"
        :options="INCOME"
        title="请选择年薪 / 收入"
        placeholder="请选择年薪 / 收入"
        mask-private
      />
      <text class="spec-note">
        用 picker-view 自绘弹层，而不是原生 &lt;picker&gt;——后者在 H5 会渲染 uni-app 自带面板，
        取消 / 确定与中间高亮条都改不动。
      </text>

      <view class="spacer" />
      <text class="spec-label">选「保密」时的形态</text>
      <AppWheelSelect
        v-model="privateIncome"
        :options="INCOME"
        title="请选择年薪 / 收入"
        placeholder="请选择年薪 / 收入"
        mask-private
      />
      <text class="spec-note">
        年薪档位本身是敏感信息，选「保密」后加锁标、收敛字重，档案里不再展示具体区间。
      </text>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">3.8 级联选择器（所在城市 / 地区）</text>
      <AppCascadeField v-model="region" />
      <text class="spec-note">
        全国三级区划：34 个省级单位（含港澳台）、344 个地级、3104 个县级，数据由
        `scripts/generate-regions.mjs` 从国家统计局派生数据集生成。直辖市与港澳台的中间层折叠成省名。
        台湾省只到县市一级——两份数据源都没有台湾的下级区划，不臆造乡镇。
      </text>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">3.9 日期选择器（出生日期）</text>
      <AppDateField v-model="birthDate" />
      <text class="spec-note">日列跟随年月变化，闰年 2 月给到 29 日；从 31 天的月份滚到 2 月会自动收敛。</text>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">3.10 性别选择</text>
      <AppRadioTiles
        v-model="gender"
        :options="['男', '女', '不公开']"
        :icons="GENDER_ICONS"
        hint="性别信息仅自己可见，用于系统匹配参考"
      />
      <text class="spec-note">
        后端 gender 字段的选项目前是 男 / 女 两项，「不公开」需要 V2 迁移落库后才会出现在真实表单里。
      </text>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">3.11 多选标签选择（兴趣爱好）</text>
      <AppTagSelect v-model="hobbies" :catalog="HOBBY_CATALOG" :limit="8" />
      <text class="spec-note">
        后端目前没有兴趣爱好字段，本组件只在预览页展示；启用需在后台新增一个 DYNAMIC 字段。
      </text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.spacer {
  height: $ds-space-3;
}
</style>

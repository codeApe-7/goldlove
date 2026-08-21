<script setup lang="ts">
import { ref } from 'vue'
import SectionCard from '@/components/SectionCard.vue'
import AppFormRow from '@/components/AppFormRow.vue'
import AppInput from '@/components/AppInput.vue'
import AppSelect from '@/components/AppSelect.vue'
import AppDateField from '@/components/AppDateField.vue'
import AppCascadeField from '@/components/AppCascadeField.vue'
import AppWheelSelect from '@/components/AppWheelSelect.vue'

/** 规范图区块 4 · 表单区块示例：基本资料与社交账号两组。 */
const EDUCATION = ['博士及以上', '硕士研究生', '大学本科', '大专', '高中及以下', '其他学历']
const OCCUPATION = ['产品经理', '产品运营', '项目经理', '前端开发工程师', '后端开发工程师', '数据分析师']
const INCOME = ['20万以下', '20万-30万', '30万-50万', '50万-80万', '80万-120万', '120万以上']
const MARITAL = ['未婚', '离异', '丧偶']

const form = ref({
  name: '',
  birthDate: '',
  education: '',
  occupation: '',
  income: '',
  region: '',
  marital: '',
  wechat: '',
  phone: '',
  alipay: '',
  email: '',
})
</script>

<template>
  <view class="spec-block">
    <text class="spec-block__title">4. 表单区块示例</text>

    <SectionCard title="基本资料（示例）">
      <view class="form-body">
        <AppFormRow label="真实姓名" required hint="该信息仅用于身份验证，仅自己可见">
          <AppInput v-model="form.name" placeholder="请输入真实姓名" />
        </AppFormRow>
        <AppFormRow label="出生日期" required>
          <AppDateField v-model="form.birthDate" />
        </AppFormRow>
        <AppFormRow label="学历" required>
          <AppSelect v-model="form.education" :options="EDUCATION" placeholder="请选择学历" />
        </AppFormRow>
        <AppFormRow label="职业" required>
          <AppSelect
            v-model="form.occupation"
            :options="OCCUPATION"
            placeholder="请选择职业"
            searchable
            search-placeholder="搜索职业关键词"
          />
        </AppFormRow>
        <AppFormRow label="年薪 / 收入" required>
          <AppWheelSelect
            v-model="form.income"
            :options="INCOME"
            title="请选择年薪 / 收入"
            placeholder="请选择年薪 / 收入"
          />
        </AppFormRow>
        <AppFormRow label="所在城市 / 地区" required>
          <AppCascadeField v-model="form.region" />
        </AppFormRow>
        <AppFormRow label="婚姻状态" required>
          <AppSelect v-model="form.marital" :options="MARITAL" placeholder="请选择婚姻状态" />
        </AppFormRow>
      </view>
    </SectionCard>

    <SectionCard title="社交账号（示例）">
      <view class="form-body">
        <AppFormRow label="微信号" optional>
          <AppInput v-model="form.wechat" placeholder="请输入微信号" />
        </AppFormRow>
        <AppFormRow label="手机号" optional>
          <AppInput v-model="form.phone" type="number" placeholder="请输入手机号" :maxlength="11" />
        </AppFormRow>
        <AppFormRow label="支付宝账号" optional>
          <AppInput v-model="form.alipay" placeholder="请输入支付宝账号" />
        </AppFormRow>
        <AppFormRow label="邮箱" optional hint="仅用于接收平台通知，不对外展示">
          <AppInput v-model="form.email" placeholder="请输入邮箱" />
        </AppFormRow>
      </view>
    </SectionCard>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.form-body {
  padding: 0 $ds-space-3;
}
</style>

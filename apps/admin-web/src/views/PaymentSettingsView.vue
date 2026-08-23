<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { paymentSetting, updatePaymentSetting } from '@/api/admin'
import { amountLabel, minuteLabel } from '@/utils/presentation'
import { minorToYuan, yuanToMinor } from '@/utils/money'
import type { PaymentSettingView } from '@/types'
import PageHeader from '@/components/PageHeader.vue'

const setting = ref<PaymentSettingView | null>(null)
// el-input-number 在输入框被清空时会 emit null，所以这里必须容忍 null，
// 否则清空后 yuanToMinor(null) 会算出 NaN 一路传到请求体里。
const amountYuan = ref<number | null>(0)
const loading = ref(false)
const saving = ref(false)

const minYuan = computed(() => minorToYuan(setting.value?.minAmountMinor ?? 1))
const maxYuan = computed(() => minorToYuan(setting.value?.maxAmountMinor ?? 10_000_000))
const nextAmountMinor = computed(() => yuanToMinor(amountYuan.value ?? 0))

/** 输入框里的值与当前生效金额不同。只用来决定「撤销修改」能不能点。 */
const edited = computed(
  () => setting.value !== null && nextAmountMinor.value !== setting.value.vipUpgradeAmountMinor,
)

/**
 * 能不能保存。改了值当然能；**没改值但金额还没被后台接管时也能**——
 * 那一次保存的意义是把当前金额固定进 `payment_setting`，从此不再跟随服务器环境变量。
 * 后端也是这么判的（只有「已有行且值相同」才算空操作），前端跟着放开，
 * 否则「固定当前金额」这件事在界面上根本做不到。
 */
const canSave = computed(
  () => setting.value !== null && (edited.value || !setting.value.managedInAdmin),
)

/** 按钮文案要说清这一下点下去做的是什么。 */
const saveLabel = computed(() =>
  setting.value && !setting.value.managedInAdmin && !edited.value ? '固定当前金额' : '保存金额',
)

/**
 * 金额有两个来源：后台设过就用后台的，没设过回落到服务端配置
 * （`VIP_UPGRADE_AMOUNT_MINOR`）。这一句要写在界面上——否则运营看到一个数字，
 * 无法判断它是谁定的、改了会不会被环境变量盖回去。
 */
const sourceLabel = computed(() =>
  setting.value?.managedInAdmin ? '后台设置' : '服务器配置（后台尚未设置过）',
)

async function load(): Promise<void> {
  loading.value = true
  try {
    const loaded = await paymentSetting()
    setting.value = loaded
    amountYuan.value = minorToYuan(loaded.vipUpgradeAmountMinor)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '支付设置加载失败')
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  const amountMinor = nextAmountMinor.value
  if (!Number.isFinite(amountMinor) || amountMinor <= 0) {
    ElMessage.warning('请填写支付金额')
    return
  }
  saving.value = true
  try {
    const wasManaged = setting.value?.managedInAdmin ?? false
    const saved = await updatePaymentSetting(amountMinor)
    setting.value = saved
    amountYuan.value = minorToYuan(saved.vipUpgradeAmountMinor)
    ElMessage.success(
      wasManaged
        ? `已改为 ${amountLabel(saved.vipUpgradeAmountMinor)}`
        : `已固定为 ${amountLabel(saved.vipUpgradeAmountMinor)}，之后由后台接管`,
    )
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function reset(): void {
  if (setting.value) {
    amountYuan.value = minorToYuan(setting.value.vipUpgradeAmountMinor)
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="payment-settings-page">
    <PageHeader title="支付设置" subtitle="VIP 升级的收款金额在这里维护，保存后对新创建的订单生效。" />

    <section class="archive-panel current-panel">
      <div class="current">
        <span class="label">当前生效金额</span>
        <strong class="tabular">{{ amountLabel(setting?.vipUpgradeAmountMinor ?? 0) }}</strong>
        <span class="source">来源：{{ sourceLabel }}</span>
      </div>
      <i class="divider" />
      <dl class="meta">
        <div>
          <dt>服务器配置值</dt>
          <dd class="tabular">{{ amountLabel(setting?.configuredAmountMinor ?? 0) }}</dd>
        </div>
        <div>
          <dt>最近修改</dt>
          <dd>{{ setting?.updatedAt ? minuteLabel(setting.updatedAt) : '—' }}</dd>
        </div>
        <div>
          <dt>商品描述</dt>
          <dd>{{ setting?.orderDescription || '—' }}</dd>
        </div>
      </dl>
    </section>

    <section class="archive-panel form-panel">
      <h2>修改 VIP 升级金额</h2>
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="收款金额（元）">
          <el-input-number
            v-model="amountYuan"
            :min="minYuan"
            :max="maxYuan"
            :step="1"
            :precision="2"
            :controls="false"
            class="amount-input"
          />
          <span class="range-hint">
            可填 {{ amountLabel(setting?.minAmountMinor ?? 0) }} ~
            {{ amountLabel(setting?.maxAmountMinor ?? 0) }}
          </span>
        </el-form-item>
        <div class="actions">
          <el-button type="primary" :loading="saving" :disabled="!canSave" @click="save">
            {{ saveLabel }}
          </el-button>
          <el-button :disabled="!edited || saving" @click="reset">撤销修改</el-button>
        </div>
      </el-form>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="改价只影响之后创建的订单"
        description="已经创建但还没付的订单保留下单时的金额——回调结算按订单金额核对，不按当前设置。访客端会员页的价格取的就是这个值，用户刷新后即可看到。"
      />
      <el-alert
        v-if="setting && !setting.managedInAdmin"
        type="info"
        :closable="false"
        show-icon
        title="现在用的还是服务器配置值"
        description="点「固定当前金额」就能把它写进数据库、由后台接管；此后改服务器环境变量不再影响它。"
      />
    </section>
  </div>
</template>

<style scoped>
.current-panel {
  padding: var(--ds-space-5);
  display: flex;
  align-items: center;
  gap: var(--ds-space-6);
}
.current {
  display: flex;
  flex-direction: column;
  min-width: 200px;
}
.current .label {
  color: var(--ds-text-secondary);
  font-size: var(--ds-body-size);
  font-weight: 500;
}
.current strong {
  margin-top: var(--ds-space-2);
  color: var(--ds-text);
  font-size: 34px;
  font-weight: var(--ds-h1-weight);
  line-height: 1.1;
}
.current .source {
  margin-top: var(--ds-space-2);
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
.divider {
  width: 1px;
  height: 64px;
  background: var(--ds-line);
}
.meta {
  flex: 1;
  margin: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--ds-space-4);
}
.meta dt {
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
.meta dd {
  margin: var(--ds-space-2) 0 0;
  color: var(--ds-text);
  font-size: var(--ds-body-size);
}
.form-panel {
  margin-top: var(--ds-space-4);
  padding: var(--ds-space-5);
  max-width: 640px;
}
.form-panel h2 {
  margin: 0 0 var(--ds-space-4);
  font-size: var(--ds-h3-size);
  font-weight: var(--ds-h3-weight);
}
.amount-input {
  width: 180px;
}
.range-hint {
  margin-left: var(--ds-space-3);
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
.actions {
  margin-bottom: var(--ds-space-4);
  display: flex;
  gap: var(--ds-space-2);
}
</style>

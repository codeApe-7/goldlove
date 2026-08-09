<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  currentAuthorizationDocumentVersion,
  provisionGuest,
  reissueCredential,
} from '@/api/admin'
import type { ProvisionedGuest } from '@/types'

const loading = ref(false)
const credentialLoading = ref(false)
const created = ref<ProvisionedGuest | null>(null)
const reissued = ref<ProvisionedGuest | null>(null)
const form = reactive({
  phone: '',
  paymentReference: '',
  amountYuan: 199,
  paidAt: new Date().toISOString(),
  authorizationDocumentVersion: '',
  note: '',
})

onMounted(async () => {
  try {
    const doc = await currentAuthorizationDocumentVersion()
    form.authorizationDocumentVersion = doc.version
  } catch {
    form.authorizationDocumentVersion = 'v0.3'
  }
})

async function submit(): Promise<void> {
  loading.value = true
  try {
    created.value = await provisionGuest({
      phone: form.phone.trim(),
      paymentReference: form.paymentReference.trim(),
      amountMinor: Math.round(form.amountYuan * 100),
      paidAt: new Date(form.paidAt).toISOString(),
      authorizationDocumentVersion: form.authorizationDocumentVersion.trim(),
      note: form.note.trim() || null,
    })
    ElMessage.success('访客登记成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登记失败')
  } finally {
    loading.value = false
  }
}

async function reissue(): Promise<void> {
  credentialLoading.value = true
  try {
    reissued.value = await reissueCredential(form.phone.trim())
    ElMessage.success('初始凭证已补发（仅本次展示）')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '补发失败')
  } finally {
    credentialLoading.value = false
  }
}
</script>

<template>
  <div>
    <h2 class="page-title">访客登记</h2>
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card>
          <el-form label-width="110px">
            <el-form-item label="手机号" required>
              <el-input v-model="form.phone" placeholder="11 位手机号" />
            </el-form-item>
            <el-form-item label="付款参考号" required>
              <el-input v-model="form.paymentReference" placeholder="付款单号" />
            </el-form-item>
            <el-form-item label="付款金额（元）" required>
              <el-input-number v-model="form.amountYuan" :min="0" :precision="2" />
            </el-form-item>
            <el-form-item label="付款时间" required>
              <el-date-picker
                v-model="form.paidAt"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss.sssZ"
              />
            </el-form-item>
            <el-form-item label="授权书版本">
              <el-input v-model="form.authorizationDocumentVersion" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.note" type="textarea" :rows="2" />
            </el-form-item>
            <el-button type="primary" :loading="loading" @click="submit">
              登记并生成初始凭证
            </el-button>
          </el-form>
          <el-alert
            v-if="created"
            type="success"
            :closable="false"
            class="credential-alert"
            :title="`初始凭证（仅本次展示）：${created.initialCredential}`"
          />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header>补发初始凭证</template>
          <el-form label-width="80px">
            <el-form-item label="手机号">
              <el-input v-model="form.phone" placeholder="已登记访客手机号" />
            </el-form-item>
            <el-button :loading="credentialLoading" @click="reissue">
              补发凭证（作废旧凭证）
            </el-button>
          </el-form>
          <el-alert
            v-if="reissued"
            type="warning"
            :closable="false"
            class="credential-alert"
            :title="`新初始凭证（仅本次展示）：${reissued.initialCredential}`"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 18px;
  color: var(--love-deep);
}
.credential-alert {
  margin-top: 16px;
}
</style>

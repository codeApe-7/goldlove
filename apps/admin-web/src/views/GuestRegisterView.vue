<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, CopyDocument, Lock } from '@element-plus/icons-vue'
import {
  currentAuthorizationDocumentVersion,
  provisionGuest,
  reissueCredential,
} from '@/api/admin'
import type { ProvisionedGuest } from '@/types'
import PageHeader from '@/components/PageHeader.vue'

const loading = ref(false)
const credentialLoading = ref(false)
const created = ref<ProvisionedGuest | null>(null)
const reissued = ref<ProvisionedGuest | null>(null)
const reissuePhone = ref('')
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
  if (!reissuePhone.value.trim()) {
    ElMessage.warning('请输入需要补发凭证的手机号')
    return
  }
  credentialLoading.value = true
  try {
    reissued.value = await reissueCredential(reissuePhone.value.trim())
    ElMessage.success('初始凭证已补发（仅本次展示）')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '补发失败')
  } finally {
    credentialLoading.value = false
  }
}

async function copyCredential(value: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success('凭证已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制凭证')
  }
}
</script>

<template>
  <div class="register-page">
    <PageHeader title="访客登记" description="录入访客支付信息，生成初始凭证用于账号激活。" />
    <div class="register-grid">
      <section class="archive-panel form-panel">
        <div class="panel-heading">
          <h2>登记信息</h2>
          <p>带 * 项为必填，请确保支付信息准确无误。</p>
        </div>
        <el-form label-position="top" class="register-form">
          <div class="form-grid">
            <el-form-item label="手机号" required>
              <el-input v-model="form.phone" placeholder="请输入 11 位手机号" />
            </el-form-item>
            <el-form-item label="付款参考号" required>
              <el-input v-model="form.paymentReference" placeholder="请输入付款参考号" />
            </el-form-item>
            <el-form-item label="付款金额（元）" required>
              <el-input-number v-model="form.amountYuan" :min="0" :precision="2" controls-position="right" />
            </el-form-item>
            <el-form-item label="付款时间" required>
              <el-date-picker
                v-model="form.paidAt"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss.sssZ"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="授权书版本" required>
              <el-input v-model="form.authorizationDocumentVersion" placeholder="请输入授权书版本" />
            </el-form-item>
            <el-form-item label="备注" class="span-two">
              <el-input v-model="form.note" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="可填写补充说明（选填）" />
            </el-form-item>
          </div>
          <el-button type="primary" class="submit-button" :loading="loading" @click="submit">
            登记并生成初始凭证
          </el-button>
        </el-form>
      </section>
      <aside class="side-column">
        <section class="archive-panel credential-panel" :class="{ ready: created }">
          <el-icon class="credential-icon"><CircleCheck v-if="created" /><Lock v-else /></el-icon>
          <span class="credential-label">初始凭证（仅本次）</span>
          <strong class="credential-code tabular">{{ created?.initialCredential || '登记成功后显示' }}</strong>
          <p>{{ created ? `账号 ${created.phoneMasked}` : '请妥善保存，关闭页面后无法再次查看。' }}</p>
          <el-button v-if="created" plain :icon="CopyDocument" @click="copyCredential(created.initialCredential)">复制凭证</el-button>
        </section>
        <section class="archive-panel reissue-panel">
          <div class="panel-heading compact">
            <h2>补发初始凭证</h2>
            <p>补发后旧凭证立即作废，请谨慎操作。</p>
          </div>
          <el-input v-model="reissuePhone" placeholder="请输入 11 位手机号" />
          <el-button class="reissue-button" :loading="credentialLoading" @click="reissue">生成新凭证</el-button>
          <div v-if="reissued" class="reissued-result">
            <span>新凭证</span>
            <strong class="tabular">{{ reissued.initialCredential }}</strong>
            <el-button link @click="copyCredential(reissued.initialCredential)">复制</el-button>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.register-grid {
  display: grid;
  grid-template-columns: minmax(520px, 1.45fr) minmax(300px, 0.75fr);
  gap: 16px;
}
.form-panel,
.credential-panel,
.reissue-panel {
  padding: 20px;
}
.panel-heading h2 {
  margin: 0;
  font-size: 15px;
}
.panel-heading p {
  margin: 7px 0 0;
  color: var(--archive-muted);
  font-size: 11px;
}
.register-form {
  margin-top: 22px;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 16px;
}
.span-two {
  grid-column: 1 / -1;
}
.register-form :deep(.el-input-number) {
  width: 100%;
}
.submit-button {
  width: 100%;
  margin-top: 6px;
}
.side-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.credential-panel {
  min-height: 190px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.credential-panel.ready {
  border-color: #bce8c9;
  background: #fbfffc;
}
.credential-icon {
  color: var(--archive-success);
  font-size: 24px;
}
.credential-label {
  margin-top: 9px;
  color: var(--archive-muted);
  font-size: 11px;
}
.credential-code {
  margin-top: 10px;
  font-size: 20px;
  letter-spacing: 0.08em;
}
.credential-panel p {
  margin: 8px 0 14px;
  color: var(--archive-muted);
  font-size: 10px;
}
.panel-heading.compact {
  margin-bottom: 16px;
}
.reissue-button {
  width: 100%;
  margin-top: 12px;
}
.reissued-result {
  margin-top: 14px;
  padding: 10px;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 8px;
  border-radius: 6px;
  background: #f5f5f3;
  font-size: 11px;
}
@media (max-width: 1100px) {
  .register-grid {
    grid-template-columns: minmax(500px, 1fr) 280px;
  }
}
</style>

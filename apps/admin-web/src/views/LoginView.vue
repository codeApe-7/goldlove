<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import BrandMark from '@/components/BrandMark.vue'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function submit(): Promise<void> {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入账号与密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    await router.push({ name: 'dashboard' })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}

function forgotPassword(): void {
  ElMessage.info('请联系系统管理员重置密码')
}
</script>

<template>
  <div class="login-page">
    <aside class="brand-rail">
      <div class="brand-lockup">
        <BrandMark light />
        <div>
          <strong>gold 智能档案库</strong>
          <span>管理后台</span>
        </div>
      </div>
      <p>真实 · 严谨 · 安全合规</p>
    </aside>
    <main class="login-stage">
      <section class="login-card">
        <BrandMark class="panel-mark" />
        <h1>gold 智能档案库</h1>
        <p class="panel-label">管理后台</p>
        <p class="tagline">真实 · 严谨 · 安全合规</p>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="账号">
            <el-input v-model="form.username" autocomplete="username" placeholder="请输入账号或手机号" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              show-password
            />
          </el-form-item>
          <div class="form-meta">
            <!-- 会话是 HttpOnly Cookie，服务端 30 天有效期；前端没有「记住我」这个开关可拨，
                 所以这里不放一个点了没反应的复选框。 -->
            <span />
            <el-button link @click="forgotPassword">忘记密码？</el-button>
          </div>
          <el-button type="primary" class="submit" :loading="loading" native-type="submit">
            登录
          </el-button>
        </el-form>
        <p class="login-help">如有疑问，请联系系统管理员或查看帮助文档</p>
      </section>
      <p class="copyright">© 2026 gold 智能档案库 · 管理后台</p>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  min-width: 900px;
  height: 100%;
  display: flex;
  background: var(--ds-page);
}
.brand-rail {
  width: 22%;
  min-width: 220px;
  padding: 34px 30px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: var(--ds-sidebar);
  color: #ffffff;
}
.brand-lockup {
  display: flex;
  align-items: center;
  gap: 12px;
}
.brand-lockup strong,
.brand-lockup span {
  display: block;
}
.brand-lockup strong {
  font-size: var(--ds-h3-size);
  font-weight: var(--ds-h3-weight);
  letter-spacing: 0.05em;
}
.brand-lockup span {
  margin-top: 5px;
  color: var(--ds-sidebar-text);
  font-size: var(--ds-caption-size);
  letter-spacing: 0.22em;
}
.brand-rail > p {
  color: #6b6d73;
  font-size: var(--ds-caption-size);
  letter-spacing: 0.18em;
}
.login-stage {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.login-card {
  width: 360px;
  padding: 36px 42px 30px;
  border: 1px solid var(--ds-line);
  border-radius: var(--ds-radius-card);
  background: var(--ds-surface);
  box-shadow: 0 20px 50px rgba(17, 17, 19, 0.05);
}
.panel-mark {
  display: flex;
  margin: 0 auto 14px;
}
h1 {
  margin: 0;
  text-align: center;
  font-size: var(--ds-h2-size);
  font-weight: var(--ds-h2-weight);
  letter-spacing: 0.06em;
}
.panel-label {
  margin: 6px 0 0;
  text-align: center;
  font-size: var(--ds-body-size);
  font-weight: 500;
}
.tagline {
  margin: 8px 0 26px;
  text-align: center;
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
  letter-spacing: 0.08em;
}
.form-meta {
  margin: -2px 0 15px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}
.submit {
  width: 100%;
  height: 38px;
}
.login-help {
  margin: 18px 0 0;
  text-align: center;
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
.copyright {
  position: absolute;
  bottom: 28px;
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
</style>

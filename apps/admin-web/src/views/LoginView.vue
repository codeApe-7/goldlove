<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

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
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <h1 class="brand">婚恋智能档案库 · 管理后台</h1>
      <p class="tagline">让每一段认真开始的关系都有据可循</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="账号">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            show-password
          />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" native-type="submit">
          登录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f7f5f2 0%, #f3e3e7 55%, #e8cfd6 100%);
}
.login-card {
  width: 380px;
  border-radius: 12px;
}
.brand {
  margin: 0 0 8px;
  color: var(--love-deep);
  font-size: 22px;
  text-align: center;
}
.tagline {
  margin: 0 0 24px;
  text-align: center;
  color: #8b7a80;
  font-size: 13px;
}
.submit {
  width: 100%;
}
</style>

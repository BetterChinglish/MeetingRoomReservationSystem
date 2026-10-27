<template>
  <div class="login-wrapper">
    <div class="login-card">
      <div class="card-header">
        <div class="logo-circle">
          <el-icon :size="28" color="#fff"><Calendar /></el-icon>
        </div>
        <h2>会议室预约系统</h2>
        <p class="subtitle">企业统一行政管理平台 · 员工在线预约与排期</p>
      </div>

      <!-- Quick credentials helper for exam evaluation -->
      <div class="quick-roles-bar">
        <span class="quick-title">快捷填入角色账号：</span>
        <div class="role-tags">
          <el-tag
            size="small"
            class="clickable-tag"
            type="primary"
            @click="fillCredentials('emp001', '123456')"
          >
            张伟 (研发部 · 普通员工)
          </el-tag>
          <el-tag
            size="small"
            class="clickable-tag"
            type="danger"
            @click="fillCredentials('admin', '123456')"
          >
            李强 (行政部 · 系统管理员)
          </el-tag>
          <el-tag
            size="small"
            class="clickable-tag"
            type="success"
            @click="fillCredentials('emp002', '123456')"
          >
            李雷 (产品部 · 普通员工)
          </el-tag>
        </div>
      </div>

      <el-form
        ref="loginFormRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="登录账号" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入账号 (如 emp001, admin)"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码 (默认 123456)"
            :prefix-icon="Lock"
            show-password
            clearable
          />
        </el-form-item>

        <div class="form-options">
          <el-checkbox v-model="rememberMe">保持登录状态</el-checkbox>
        </div>

        <el-button
          type="primary"
          :loading="loading"
          class="submit-btn"
          @click="handleLogin"
        >
          登 录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { Calendar, User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)
const rememberMe = ref(true)

const form = reactive({
  username: 'emp001',
  password: '123456'
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function fillCredentials(u, p) {
  form.username = u
  form.password = p
}

async function handleLogin() {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async valid => {
    if (!valid) return
    loading.value = true
    try {
      const data = await userStore.handleLogin({
        username: form.username,
        password: form.password
      }, rememberMe.value)

      ElMessage.success(`欢迎回来，${data.user.name} (${data.user.role === 'ADMIN' ? '管理员' : '普通员工'})`)
      const redirect = route.query.redirect || '/reservations'
      router.push(redirect)
    } catch (err) {
      // Error handled by interceptor
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f2027 0%, #203a43 50%, #2c5364 100%);
  padding: 24px;
}

.login-card {
  width: 100%;
  max-width: 440px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.25);
  padding: 36px 32px;
}

.card-header {
  text-align: center;
  margin-bottom: 24px;
}

.logo-circle {
  width: 54px;
  height: 54px;
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 12px;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.4);
}

.card-header h2 {
  font-size: 22px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 6px;
}

.subtitle {
  font-size: 13px;
  color: #8c92a4;
}

.quick-roles-bar {
  background: #f8f9fa;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 20px;
}

.quick-title {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.role-tags {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.clickable-tag {
  cursor: pointer;
  text-align: left;
  display: block;
  transition: transform 0.15s;
}

.clickable-tag:hover {
  transform: translateX(4px);
}

.login-form {
  margin-top: 10px;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.submit-btn {
  width: 100%;
  height: 40px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 6px;
}
</style>

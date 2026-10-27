<template>
  <header class="app-header">
    <div class="header-left">
      <div class="logo-box">
        <el-icon :size="22" color="#fff"><Calendar /></el-icon>
      </div>
      <div class="title-group">
        <span class="system-title">会议室预约系统</span>
        <el-tag size="small" type="info" effect="plain" class="badge-tag">企业统一行政管理平台</el-tag>
      </div>
    </div>

    <nav class="nav-pills">
      <router-link to="/reservations" class="nav-item" active-class="active">
        <el-icon><List /></el-icon>
        <span>预约列表</span>
      </router-link>
      <router-link to="/reservations/create" class="nav-item" active-class="active">
        <el-icon><Plus /></el-icon>
        <span>发起预约</span>
      </router-link>
      <router-link to="/dashboard" class="nav-item" active-class="active">
        <el-icon><DataAnalysis /></el-icon>
        <span>数据看板</span>
      </router-link>
    </nav>

    <div class="header-right">
      <div class="clock-display">
        <el-icon><Clock /></el-icon>
        <span>{{ currentTime }}</span>
      </div>

      <div class="user-card" v-if="userStore.userInfo">
        <el-avatar :size="32" class="user-avatar">
          {{ userStore.name ? userStore.name.slice(0, 1) : 'U' }}
        </el-avatar>
        <div class="user-meta">
          <div class="user-name-row">
            <span class="user-name">{{ userStore.name }}</span>
            <el-tag :type="userStore.isAdmin ? 'danger' : 'primary'" size="small" effect="dark" class="role-badge">
              {{ userStore.isAdmin ? '管理员' : '普通员工' }}
            </el-tag>
          </div>
          <span class="user-dept">{{ userStore.deptName }}</span>
        </div>

        <el-button type="danger" link :icon="SwitchButton" @click="handleLogout" title="退出登录" class="logout-btn">
          退出
        </el-button>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { Calendar, List, Plus, DataAnalysis, Clock, SwitchButton } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const currentTime = ref('')

let timer = null
function updateClock() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const hh = String(now.getHours()).padStart(2, '0')
  const mm = String(now.getMinutes()).padStart(2, '0')
  const ss = String(now.getSeconds()).padStart(2, '0')
  currentTime.value = `${y}-${m}-${d} ${hh}:${mm}:${ss}`
}

onMounted(() => {
  updateClock()
  timer = setInterval(updateClock, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出会议室预约系统吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await userStore.handleLogout()
    ElMessage.success('已安全退出登录')
    router.push('/login')
  } catch (e) {
    // cancelled
  }
}
</script>

<style scoped>
.app-header {
  height: 64px;
  background: #ffffff;
  border-bottom: 1px solid #ebeef5;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-box {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(24, 144, 255, 0.35);
}

.title-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.system-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  letter-spacing: 0.5px;
}

.badge-tag {
  font-size: 12px;
  border-radius: 4px;
  background-color: #f4f4f5;
  color: #909399;
}

.nav-pills {
  display: flex;
  background: #f0f2f5;
  border-radius: 8px;
  padding: 4px;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  font-size: 14px;
  color: #606266;
  text-decoration: none;
  border-radius: 6px;
  transition: all 0.2s;
  font-weight: 500;
}

.nav-item:hover {
  color: #409eff;
}

.nav-item.active {
  background: #ffffff;
  color: #409eff;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.clock-display {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #909399;
  font-family: monospace;
  background: #f8f9fa;
  padding: 6px 12px;
  border-radius: 6px;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-left: 12px;
  border-left: 1px solid #ebeef5;
}

.user-avatar {
  background: #409eff;
  color: #fff;
  font-weight: 600;
}

.user-meta {
  display: flex;
  flex-direction: column;
}

.user-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.role-badge {
  font-size: 10px;
  padding: 0 6px;
  height: 18px;
  line-height: 18px;
}

.user-dept {
  font-size: 12px;
  color: #909399;
}

.logout-btn {
  margin-left: 8px;
}
</style>

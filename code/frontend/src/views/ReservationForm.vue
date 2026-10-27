<template>
  <div class="reservation-form-page">
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="$router.push('/reservations')">返回列表</el-button>
      <h2>发起会议室预约</h2>
    </div>

    <el-row :gutter="24">
      <!-- Left Column: Form -->
      <el-col :span="14">
        <el-card shadow="never" class="form-card">
          <template #header>
            <div class="card-title">
              <el-icon><Calendar /></el-icon>
              <span>预约关键信息填写</span>
            </div>
          </template>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="110px"
            label-position="right"
          >
            <el-form-item label="预约人">
              <el-input :value="`${userStore.name} (${userStore.username})`" disabled style="width: 280px" />
              <span class="form-hint">登录身份自动注入，不可改写</span>
            </el-form-item>

            <el-form-item label="归属部门" prop="deptId">
              <el-select v-model="form.deptId" placeholder="请选择部门" style="width: 280px">
                <el-option
                  v-for="dept in deptList"
                  :key="dept.id"
                  :label="dept.name"
                  :value="dept.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="目标会议室" prop="roomId">
              <el-select
                v-model="form.roomId"
                placeholder="请选择会议室"
                style="width: 100%"
                @change="handleRoomChange"
              >
                <el-option
                  v-for="room in roomList"
                  :key="room.id"
                  :label="`${room.name} (${room.location} · 容纳${room.capacity}人)`"
                  :value="room.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="预约日期" prop="reserveDate">
              <el-date-picker
                v-model="form.reserveDate"
                type="date"
                placeholder="选择预约日期"
                value-format="YYYY-MM-DD"
                :disabled-date="disabledPastDates"
                style="width: 280px"
                @change="fetchRoomSchedule"
              />
            </el-form-item>

            <el-form-item label="会议时段" required>
              <div class="time-select-row">
                <el-form-item prop="startTime" class="inline-item">
                  <el-select
                    v-model="form.startTime"
                    placeholder="开始时间"
                    style="width: 140px"
                    @change="handleTimeChange"
                  >
                    <el-option
                      v-for="time in timeOptions"
                      :key="time"
                      :label="time"
                      :value="time"
                    />
                  </el-select>
                </el-form-item>
                <span class="time-separator">至</span>
                <el-form-item prop="endTime" class="inline-item">
                  <el-select
                    v-model="form.endTime"
                    placeholder="结束时间"
                    style="width: 140px"
                    @change="handleTimeChange"
                  >
                    <el-option
                      v-for="time in timeOptions"
                      :key="time"
                      :label="time"
                      :value="time"
                    />
                  </el-select>
                </el-form-item>
              </div>
            </el-form-item>

            <el-form-item label="会议主题" prop="title">
              <el-input
                v-model="form.title"
                placeholder="请输入会议议题与简述 (100字以内)"
                maxlength="100"
                show-word-limit
                clearable
              />
            </el-form-item>

            <!-- Conflict warning banner -->
            <div v-if="conflictWarning" class="conflict-alert">
              <el-alert
                :title="conflictWarning"
                type="warning"
                show-icon
                :closable="false"
              />
            </div>

            <el-form-item class="form-actions">
              <el-button type="primary" :loading="submitting" @click="handleSubmit">
                确认提交预约
              </el-button>
              <el-button @click="handleReset">重 置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- Right Column: Timeline / Schedule for Selected Room -->
      <el-col :span="10">
        <el-card shadow="never" class="schedule-card">
          <template #header>
            <div class="card-title">
              <el-icon><Clock /></el-icon>
              <span>当日已有排期看板</span>
            </div>
          </template>

          <div v-if="!form.roomId" class="empty-schedule">
            <el-empty description="请先选择左侧会议室" :image-size="80" />
          </div>

          <div v-else>
            <div class="schedule-info-bar">
              <span class="room-tag">{{ getSelectedRoomName() }}</span>
              <span class="date-tag">{{ form.reserveDate }}</span>
            </div>

            <div v-if="scheduleLoading" class="loading-box" v-loading="scheduleLoading" style="height: 150px"></div>

            <div v-else-if="existingReservations.length === 0" class="no-bookings">
              <el-result
                icon="success"
                title="全天暂无预约"
                sub-title="该会议室当日各时段均可自由预约"
              />
            </div>

            <div v-else class="timeline-list">
              <el-timeline>
                <el-timeline-item
                  v-for="item in existingReservations"
                  :key="item.id"
                  :type="item.computedStatus === 'ACTIVE' ? 'primary' : 'info'"
                  :timestamp="`${formatTime(item.startTime)} ~ ${formatTime(item.endTime)}`"
                  placement="top"
                >
                  <el-card shadow="hover" class="timeline-card">
                    <div class="booking-row">
                      <span class="booking-title">{{ item.title }}</span>
                      <el-tag size="small">{{ item.userName }} ({{ item.deptName }})</el-tag>
                    </div>
                  </el-card>
                </el-timeline-item>
              </el-timeline>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getActiveRooms } from '@/api/room'
import { getDepartments } from '@/api/dept'
import { createReservation, getReservations } from '@/api/reservation'
import { ArrowLeft, Calendar, Clock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const submitting = ref(false)
const scheduleLoading = ref(false)
const conflictWarning = ref('')

const roomList = ref([])
const deptList = ref([])
const existingReservations = ref([])

// Generate 30-min interval times e.g. 08:00, 08:30, ... 20:00
const timeOptions = []
for (let h = 8; h <= 20; h++) {
  const hh = String(h).padStart(2, '0')
  timeOptions.push(`${hh}:00`)
  if (h < 20) {
    timeOptions.push(`${hh}:30`)
  }
}

function getTodayString() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const form = reactive({
  roomId: null,
  deptId: userStore.userInfo?.deptId || null,
  reserveDate: getTodayString(),
  startTime: '09:00',
  endTime: '10:00',
  title: ''
})

const rules = {
  roomId: [{ required: true, message: '请选择会议室', trigger: 'change' }],
  deptId: [{ required: true, message: '请选择预约部门', trigger: 'change' }],
  reserveDate: [{ required: true, message: '请选择预约日期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  title: [{ required: true, message: '请输入会议主题', trigger: 'blur' }]
}

onMounted(async () => {
  try {
    const [rooms, depts] = await Promise.all([getActiveRooms(), getDepartments()])
    roomList.value = rooms
    deptList.value = depts
    if (rooms.length > 0) {
      form.roomId = rooms[0].id
      fetchRoomSchedule()
    }
  } catch (e) {}
})

function disabledPastDates(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

function handleRoomChange() {
  fetchRoomSchedule()
  checkConflictLocal()
}

function handleTimeChange() {
  checkConflictLocal()
}

async function fetchRoomSchedule() {
  if (!form.roomId || !form.reserveDate) return
  scheduleLoading.value = true
  try {
    const res = await getReservations({
      roomId: form.roomId,
      reserveDate: form.reserveDate,
      page: 1,
      size: 50
    })
    existingReservations.value = res.list.filter(item => item.status !== 'CANCELLED')
    checkConflictLocal()
  } catch (e) {
  } finally {
    scheduleLoading.value = false
  }
}

function checkConflictLocal() {
  conflictWarning.value = ''
  if (!form.startTime || !form.endTime || existingReservations.value.length === 0) return

  if (form.startTime >= form.endTime) {
    conflictWarning.value = '结束时间必须晚于开始时间'
    return
  }

  for (const r of existingReservations.value) {
    const rStart = formatTime(r.startTime)
    const rEnd = formatTime(r.endTime)
    // s_new < r_end && r_start < e_new
    if (form.startTime < rEnd && rStart < form.endTime) {
      conflictWarning.value = `与已有预约冲突：${r.title} (${rStart} ~ ${rEnd} · ${r.userName})`
      break
    }
  }
}

function getSelectedRoomName() {
  const r = roomList.value.find(item => item.id === form.roomId)
  return r ? r.name : '未选择会议室'
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async valid => {
    if (!valid) return

    if (form.startTime >= form.endTime) {
      ElMessage.error('结束时间必须晚于开始时间')
      return
    }

    submitting.value = true
    try {
      await createReservation({
        roomId: form.roomId,
        reserveDate: form.reserveDate,
        startTime: form.startTime,
        endTime: form.endTime,
        deptId: form.deptId,
        title: form.title
      })
      ElMessage.success('预约申请成功创建！')
      router.push('/reservations')
    } catch (e) {
      // Handled by request interceptor
    } finally {
      submitting.value = false
    }
  })
}

function handleReset() {
  form.title = ''
  form.startTime = '09:00'
  form.endTime = '10:00'
  conflictWarning.value = ''
}

function formatTime(t) {
  if (!t) return ''
  return t.length > 5 ? t.slice(0, 5) : t
}
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.form-hint {
  font-size: 12px;
  color: #909399;
  margin-left: 12px;
}

.time-select-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.inline-item {
  margin-bottom: 0 !important;
}

.time-separator {
  color: #909399;
  font-size: 14px;
}

.conflict-alert {
  margin-bottom: 20px;
}

.form-actions {
  margin-top: 32px;
}

.schedule-info-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.room-tag {
  background: #ecf5ff;
  color: #409eff;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
}

.date-tag {
  background: #f4f4f5;
  color: #909399;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 13px;
  font-family: monospace;
}

.timeline-list {
  max-height: 440px;
  overflow-y: auto;
  padding-right: 8px;
}

.timeline-card {
  padding: 8px 12px;
  border-radius: 6px;
}

.booking-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.booking-title {
  font-weight: 500;
  color: #303133;
}
</style>

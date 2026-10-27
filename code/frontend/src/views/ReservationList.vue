<template>
  <div class="reservation-list-page">
    <!-- Filter Card -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="会议室">
          <el-select v-model="filters.roomId" placeholder="全部会议室" clearable style="width: 180px">
            <el-option
              v-for="room in roomList"
              :key="room.id"
              :label="room.name"
              :value="room.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="预约日期">
          <el-date-picker
            v-model="filters.reserveDate"
            type="date"
            placeholder="选择单日"
            value-format="YYYY-MM-DD"
            style="width: 160px"
            clearable
          />
        </el-form-item>

        <el-form-item label="预约部门">
          <el-select v-model="filters.deptId" placeholder="全部部门" clearable style="width: 150px">
            <el-option
              v-for="dept in deptList"
              :key="dept.id"
              :label="dept.name"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="预约状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="未开始" value="PENDING" />
            <el-option label="进行中" value="ACTIVE" />
            <el-option label="已结束" value="ENDED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-radio-group v-model="viewScope" size="default" @change="handleScopeChange">
            <el-radio-button label="ALL">全部预约</el-radio-button>
            <el-radio-button label="MINE">仅看本人</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item class="action-items">
          <el-button type="primary" :icon="Search" @click="handleSearch">查 询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重 置</el-button>
          <el-button type="success" :icon="Plus" @click="$router.push('/reservations/create')">
            发起预约
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table Card -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        style="width: 100%"
        empty-text="暂无匹配的预约记录"
      >
        <el-table-column prop="id" label="ID" width="70" align="center" />

        <el-table-column prop="title" label="会议主题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="table-title">{{ row.title }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="roomName" label="会议室" min-width="150">
          <template #default="{ row }">
            <div class="room-cell">
              <span class="room-name">{{ row.roomName }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="userName" label="预约人" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.userId === userStore.userId ? 'success' : 'info'">
              {{ row.userName }}
              <span v-if="row.userId === userStore.userId"> (本人)</span>
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="deptName" label="归属部门" width="120" align="center" />

        <el-table-column prop="reserveDate" label="预约日期" width="120" align="center" />

        <el-table-column label="起止时段" width="160" align="center">
          <template #default="{ row }">
            <span class="time-range">{{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="computedStatus" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag
              :type="getStatusTagType(row.computedStatus)"
              effect="light"
              :class="['status-badge', { 'pulse-active': row.computedStatus === 'ACTIVE' }]"
            >
              {{ getStatusLabel(row.computedStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <!-- Cancel Button -->
              <el-button
                v-if="row.computedStatus === 'PENDING' && canOperate(row)"
                type="danger"
                size="small"
                link
                @click="handleCancel(row)"
              >
                取消
              </el-button>

              <!-- Early End Button -->
              <el-button
                v-if="row.computedStatus === 'ACTIVE' && canOperate(row)"
                type="warning"
                size="small"
                link
                @click="handleEarlyEnd(row)"
              >
                提前结束
              </el-button>

              <el-button
                type="primary"
                size="small"
                link
                @click="showDetail(row)"
              >
                详情
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchReservations"
          @current-change="fetchReservations"
        />
      </div>
    </el-card>

    <!-- Detail Dialog -->
    <el-dialog v-model="detailVisible" title="预约详细信息" width="550px">
      <el-descriptions :column="2" border v-if="selectedRow">
        <el-descriptions-item label="预约编号">{{ selectedRow.id }}</el-descriptions-item>
        <el-descriptions-item label="当前即时状态">
          <el-tag :type="getStatusTagType(selectedRow.computedStatus)">
            {{ getStatusLabel(selectedRow.computedStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="会议主题" :span="2">{{ selectedRow.title }}</el-descriptions-item>
        <el-descriptions-item label="会议室">{{ selectedRow.roomName }}</el-descriptions-item>
        <el-descriptions-item label="预约部门">{{ selectedRow.deptName }}</el-descriptions-item>
        <el-descriptions-item label="预约人">{{ selectedRow.userName }}</el-descriptions-item>
        <el-descriptions-item label="预约日期">{{ selectedRow.reserveDate }}</el-descriptions-item>
        <el-descriptions-item label="会议时段" :span="2">
          {{ formatTime(selectedRow.startTime) }} 至 {{ formatTime(selectedRow.endTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ selectedRow.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="更新时间" :span="2">{{ selectedRow.updatedAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import { getReservations, cancelReservation, earlyEndReservation } from '@/api/reservation'
import { getAllRooms } from '@/api/room'
import { getDepartments } from '@/api/dept'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()

const loading = ref(false)
const tableData = ref([])
const roomList = ref([])
const deptList = ref([])
const viewScope = ref('ALL')

const detailVisible = ref(false)
const selectedRow = ref(null)

const filters = reactive({
  roomId: null,
  reserveDate: null,
  deptId: null,
  status: null,
  userId: null
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

onMounted(async () => {
  await Promise.all([loadRooms(), loadDepts()])
  fetchReservations()
})

async function loadRooms() {
  try {
    roomList.value = await getAllRooms()
  } catch (e) {}
}

async function loadDepts() {
  try {
    deptList.value = await getDepartments()
  } catch (e) {}
}

function handleScopeChange(val) {
  if (val === 'MINE') {
    filters.userId = userStore.userId
  } else {
    filters.userId = null
  }
  handleSearch()
}

function handleSearch() {
  pagination.page = 1
  fetchReservations()
}

function handleReset() {
  filters.roomId = null
  filters.reserveDate = null
  filters.deptId = null
  filters.status = null
  filters.userId = null
  viewScope.value = 'ALL'
  pagination.page = 1
  fetchReservations()
}

async function fetchReservations() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size,
      ...(filters.roomId && { roomId: filters.roomId }),
      ...(filters.reserveDate && { reserveDate: filters.reserveDate }),
      ...(filters.deptId && { deptId: filters.deptId }),
      ...(filters.status && { status: filters.status }),
      ...(filters.userId && { userId: filters.userId })
    }
    const data = await getReservations(params)
    tableData.value = data.list
    pagination.total = data.total
  } catch (e) {
  } finally {
    loading.value = false
  }
}

function canOperate(row) {
  if (userStore.isAdmin) return true
  return row.userId === userStore.userId
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(`确定要取消「${row.title}」的预约吗？时段将立即释放。`, '确认取消', {
      type: 'warning',
      confirmButtonText: '确定取消',
      cancelButtonText: '暂不取消'
    })
    await cancelReservation(row.id)
    ElMessage.success('预约已成功取消')
    fetchReservations()
  } catch (e) {}
}

async function handleEarlyEnd(row) {
  try {
    await ElMessageBox.confirm(`确定要提前结束「${row.title}」的会议吗？状态将变更为已结束。`, '确认提前结束', {
      type: 'warning',
      confirmButtonText: '提前结束',
      cancelButtonText: '取消'
    })
    await earlyEndReservation(row.id)
    ElMessage.success('会议已提前结束')
    fetchReservations()
  } catch (e) {}
}

function showDetail(row) {
  selectedRow.value = row
  detailVisible.value = true
}

function formatTime(t) {
  if (!t) return ''
  return t.length > 5 ? t.slice(0, 5) : t
}

function getStatusTagType(status) {
  switch (status) {
    case 'PENDING': return 'primary'
    case 'ACTIVE': return 'success'
    case 'ENDED': return 'info'
    case 'CANCELLED': return 'danger'
    default: return 'info'
  }
}

function getStatusLabel(status) {
  switch (status) {
    case 'PENDING': return '未开始'
    case 'ACTIVE': return '进行中'
    case 'ENDED': return '已结束'
    case 'CANCELLED': return '已取消'
    default: return status || '未知'
  }
}
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
  border-radius: 8px;
}

.table-card {
  border-radius: 8px;
}

.table-title {
  font-weight: 500;
  color: #1f2d3d;
}

.time-range {
  font-family: monospace;
  font-weight: 600;
  color: #475669;
}

.table-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.status-badge {
  font-weight: 500;
  border-radius: 4px;
}

.pulse-active {
  animation: pulse 1.8s infinite;
}

@keyframes pulse {
  0% { box-shadow: 0 0 0 0 rgba(103, 194, 58, 0.6); }
  70% { box-shadow: 0 0 0 6px rgba(103, 194, 58, 0); }
  100% { box-shadow: 0 0 0 0 rgba(103, 194, 58, 0); }
}
</style>

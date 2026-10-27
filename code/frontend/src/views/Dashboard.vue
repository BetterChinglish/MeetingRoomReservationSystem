<template>
  <div class="dashboard-page">
    <!-- Filter Control Bar -->
    <el-card shadow="never" class="filter-card">
      <div class="dashboard-controls">
        <div class="control-left">
          <span class="control-label">统计维度：</span>
          <el-radio-group v-model="dimension" size="default" @change="handleDimensionChange">
            <el-radio-button label="DATE">按日期维度</el-radio-button>
            <el-radio-button label="MONTH">按月份维度</el-radio-button>
          </el-radio-group>

          <el-date-picker
            v-if="dimension === 'DATE'"
            v-model="selectedDate"
            type="date"
            placeholder="选择统计日期"
            value-format="YYYY-MM-DD"
            style="width: 170px; margin-left: 16px;"
            :clearable="false"
            @change="fetchStats"
          />

          <el-date-picker
            v-else
            v-model="selectedMonth"
            type="month"
            placeholder="选择统计月份"
            value-format="YYYY-MM"
            style="width: 170px; margin-left: 16px;"
            :clearable="false"
            @change="fetchStats"
          />
        </div>

        <div class="control-right">
          <el-button
            type="primary"
            plain
            :icon="Refresh"
            :loading="refreshing"
            @click="handleForceRefresh"
          >
            强制刷新数据
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- KPI Metric Summary Cards -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-content">
            <div class="kpi-icon-box bg-blue">
              <el-icon :size="24" color="#fff"><Calendar /></el-icon>
            </div>
            <div class="kpi-data">
              <span class="kpi-title">总预约场次</span>
              <span class="kpi-value text-blue">{{ summary.totalBookings }} <span class="unit">场</span></span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-content">
            <div class="kpi-icon-box bg-purple">
              <el-icon :size="24" color="#fff"><Clock /></el-icon>
            </div>
            <div class="kpi-data">
              <span class="kpi-title">总占用时长</span>
              <span class="kpi-value text-purple">{{ formatMinutes(summary.totalMinutes) }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-content">
            <div class="kpi-icon-box bg-green">
              <el-icon :size="24" color="#fff"><OfficeBuilding /></el-icon>
            </div>
            <div class="kpi-data">
              <span class="kpi-title">活跃会议室数</span>
              <span class="kpi-value text-green">{{ summary.activeRooms }} <span class="unit">间</span></span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-content">
            <div class="kpi-icon-box bg-orange">
              <el-icon :size="24" color="#fff"><UserFilled /></el-icon>
            </div>
            <div class="kpi-data">
              <span class="kpi-title">活跃部门数</span>
              <span class="kpi-value text-orange">{{ summary.activeDepts }} <span class="unit">个</span></span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Charts Row -->
    <el-row :gutter="16" class="charts-row">
      <el-col :span="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="chart-header">
              <span>各会议室预约场次与占用时长对比</span>
            </div>
          </template>
          <div ref="roomChartRef" class="echarts-container"></div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="chart-header">
              <span>各部门预约场次与占用时长对比</span>
            </div>
          </template>
          <div ref="deptChartRef" class="echarts-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Data Tables Card -->
    <el-card shadow="never" class="detail-card">
      <template #header>
        <div class="table-header-tabs">
          <el-radio-group v-model="activeDetailTab" size="small">
            <el-radio-button label="ROOM">会议室维度明细</el-radio-button>
            <el-radio-button label="DEPT">部门维度明细</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <!-- Room detail table -->
      <el-table
        v-if="activeDetailTab === 'ROOM'"
        :data="roomStats"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="roomId" label="会议室ID" width="100" align="center" />
        <el-table-column prop="roomName" label="会议室名称" min-width="180" />
        <el-table-column prop="bookingCount" label="预约场次" width="140" align="center" sortable>
          <template #default="{ row }">
            <el-tag :type="row.bookingCount > 0 ? 'primary' : 'info'">{{ row.bookingCount }} 场</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="occupiedMinutes" label="累计占用时长" min-width="180" align="center" sortable>
          <template #default="{ row }">
            <span>{{ formatMinutes(row.occupiedMinutes) }} ({{ row.occupiedMinutes }} 分钟)</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- Dept detail table -->
      <el-table
        v-else
        :data="deptStats"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="deptId" label="部门ID" width="100" align="center" />
        <el-table-column prop="deptName" label="部门名称" min-width="180" />
        <el-table-column prop="bookingCount" label="预约场次" width="140" align="center" sortable>
          <template #default="{ row }">
            <el-tag :type="row.bookingCount > 0 ? 'success' : 'info'">{{ row.bookingCount }} 场</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="occupiedMinutes" label="累计占用时长" min-width="180" align="center" sortable>
          <template #default="{ row }">
            <span>{{ formatMinutes(row.occupiedMinutes) }} ({{ row.occupiedMinutes }} 分钟)</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDashboardStats, refreshDashboard } from '@/api/dashboard'
import { Calendar, Clock, OfficeBuilding, UserFilled, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const dimension = ref('DATE')
const selectedDate = ref('')
const selectedMonth = ref('')
const refreshing = ref(false)
const activeDetailTab = ref('ROOM')

const roomChartRef = ref(null)
const deptChartRef = ref(null)
let roomChart = null
let deptChart = null

const summary = reactive({
  totalBookings: 0,
  totalMinutes: 0,
  activeRooms: 0,
  activeDepts: 0
})

const roomStats = ref([])
const deptStats = ref([])

function initDates() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  selectedDate.value = `${y}-${m}-${day}`
  selectedMonth.value = `${y}-${m}`
}

onMounted(() => {
  initDates()
  fetchStats()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (roomChart) roomChart.dispose()
  if (deptChart) deptChart.dispose()
})

function handleResize() {
  if (roomChart) roomChart.resize()
  if (deptChart) deptChart.resize()
}

function handleDimensionChange() {
  fetchStats()
}

async function fetchStats() {
  try {
    const params = {
      dimension: dimension.value,
      ...(dimension.value === 'DATE' ? { date: selectedDate.value } : { month: selectedMonth.value })
    }
    const data = await getDashboardStats(params)
    applyStatsData(data)
  } catch (e) {}
}

async function handleForceRefresh() {
  refreshing.value = true
  try {
    const params = {
      dimension: dimension.value,
      ...(dimension.value === 'DATE' ? { date: selectedDate.value } : { month: selectedMonth.value })
    }
    const data = await refreshDashboard(params)
    applyStatsData(data)
    ElMessage.success('看板缓存已刷新，已加载最新实时数据')
  } catch (e) {
  } finally {
    refreshing.value = false
  }
}

function applyStatsData(data) {
  summary.totalBookings = data.summary.totalBookings
  summary.totalMinutes = data.summary.totalMinutes
  summary.activeRooms = data.summary.activeRooms
  summary.activeDepts = data.summary.activeDepts

  roomStats.value = data.roomStats || []
  deptStats.value = data.deptStats || []

  nextTick(() => {
    renderCharts()
  })
}

function renderCharts() {
  renderRoomChart()
  renderDeptChart()
}

function renderRoomChart() {
  if (!roomChartRef.value) return
  if (!roomChart) {
    roomChart = echarts.init(roomChartRef.value)
  }

  const names = roomStats.value.map(r => r.roomName)
  const bookings = roomStats.value.map(r => r.bookingCount)
  const hours = roomStats.value.map(r => (r.occupiedMinutes / 60).toFixed(1))

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: params => {
        const item0 = params[0]
        const item1 = params[1]
        return `<strong>${item0.name}</strong><br/>
                预约场次: ${item0.value} 场<br/>
                占用时长: ${item1.value} 小时 (${(item1.value * 60).toFixed(0)} 分钟)`
      }
    },
    legend: {
      data: ['预约场次(场)', '占用时长(小时)']
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { interval: 0, rotate: 25 }
    },
    yAxis: [
      {
        type: 'value',
        name: '场次',
        minInterval: 1
      },
      {
        type: 'value',
        name: '小时'
      }
    ],
    series: [
      {
        name: '预约场次(场)',
        type: 'bar',
        data: bookings,
        itemStyle: { color: '#1890ff' }
      },
      {
        name: '占用时长(小时)',
        type: 'line',
        yAxisIndex: 1,
        data: hours,
        itemStyle: { color: '#722ed1' }
      }
    ]
  }

  roomChart.setOption(option)
}

function renderDeptChart() {
  if (!deptChartRef.value) return
  if (!deptChart) {
    deptChart = echarts.init(deptChartRef.value)
  }

  const names = deptStats.value.map(d => d.deptName)
  const bookings = deptStats.value.map(d => d.bookingCount)
  const hours = deptStats.value.map(d => (d.occupiedMinutes / 60).toFixed(1))

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: params => {
        const item0 = params[0]
        const item1 = params[1]
        return `<strong>${item0.name}</strong><br/>
                预约场次: ${item0.value} 场<br/>
                占用时长: ${item1.value} 小时 (${(item1.value * 60).toFixed(0)} 分钟)`
      }
    },
    legend: {
      data: ['部门预约场次(场)', '占用时长(小时)']
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { interval: 0, rotate: 20 }
    },
    yAxis: [
      {
        type: 'value',
        name: '场次',
        minInterval: 1
      },
      {
        type: 'value',
        name: '小时'
      }
    ],
    series: [
      {
        name: '部门预约场次(场)',
        type: 'bar',
        data: bookings,
        itemStyle: { color: '#52c41a' }
      },
      {
        name: '占用时长(小时)',
        type: 'line',
        yAxisIndex: 1,
        data: hours,
        itemStyle: { color: '#fa8c16' }
      }
    ]
  }

  deptChart.setOption(option)
}

function formatMinutes(min) {
  if (!min || min === 0) return '0 分钟'
  const h = Math.floor(min / 60)
  const m = min % 60
  if (h > 0 && m > 0) {
    return `${h} 小时 ${m} 分钟`
  } else if (h > 0) {
    return `${h} 小时`
  }
  return `${m} 分钟`
}
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
  border-radius: 8px;
}

.dashboard-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.control-left {
  display: flex;
  align-items: center;
}

.control-label {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
  margin-right: 8px;
}

.kpi-row {
  margin-bottom: 16px;
}

.kpi-card {
  border-radius: 8px;
}

.kpi-content {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 4px;
}

.kpi-icon-box {
  width: 50px;
  height: 50px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bg-blue { background: linear-gradient(135deg, #36cfc9 0%, #1890ff 100%); }
.bg-purple { background: linear-gradient(135deg, #b37feb 0%, #722ed1 100%); }
.bg-green { background: linear-gradient(135deg, #95de64 0%, #52c41a 100%); }
.bg-orange { background: linear-gradient(135deg, #ffc069 0%, #fa8c16 100%); }

.kpi-data {
  display: flex;
  flex-direction: column;
}

.kpi-title {
  font-size: 13px;
  color: #8c92a4;
  margin-bottom: 4px;
}

.kpi-value {
  font-size: 22px;
  font-weight: 700;
}

.kpi-value .unit {
  font-size: 13px;
  font-weight: normal;
  color: #909399;
}

.text-blue { color: #1890ff; }
.text-purple { color: #722ed1; }
.text-green { color: #52c41a; }
.text-orange { color: #fa8c16; }

.charts-row {
  margin-bottom: 16px;
}

.chart-card {
  border-radius: 8px;
}

.chart-header {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}

.echarts-container {
  height: 320px;
  width: 100%;
}

.detail-card {
  border-radius: 8px;
}
</style>

import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: '',
  timeout: 10000
})

service.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token') || sessionStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

service.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200 || res.code === 201) {
      return res.data
    } else {
      ElMessage.error(res.message || '请求处理失败')
      return Promise.reject(new Error(res.message || 'Error'))
    }
  },
  error => {
    const res = error.response
    if (res) {
      if (res.status === 401) {
        ElMessage.error('登录凭据已失效，请重新登录')
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        sessionStorage.removeItem('token')
        sessionStorage.removeItem('userInfo')
        window.location.href = '/login'
      } else if (res.status === 403) {
        ElMessage.error(res.data?.message || '无权限访问该资源')
      } else if (res.status === 409) {
        ElMessage.warning(res.data?.message || '时段冲突：该会议室已被占用')
      } else {
        ElMessage.error(res.data?.message || `服务器异常 (${res.status})`)
      }
    } else {
      ElMessage.error('网络连接异常，请检查服务状态')
    }
    return Promise.reject(error)
  }
)

export default service

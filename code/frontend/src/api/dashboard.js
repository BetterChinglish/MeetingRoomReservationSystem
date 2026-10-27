import request from './request'

export function getDashboardStats(params) {
  return request({
    url: '/api/dashboard/stats',
    method: 'get',
    params
  })
}

export function refreshDashboard(params) {
  return request({
    url: '/api/dashboard/refresh',
    method: 'post',
    params
  })
}

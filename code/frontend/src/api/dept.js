import request from './request'

export function getDepartments() {
  return request({
    url: '/api/departments',
    method: 'get'
  })
}

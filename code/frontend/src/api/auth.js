import request from './request'

export function login(data) {
  return request({
    url: '/api/auth/login',
    method: 'post',
    data
  })
}

export function logout() {
  return request({
    url: '/api/auth/logout',
    method: 'post'
  })
}

export function getMe() {
  return request({
    url: '/api/auth/me',
    method: 'get'
  })
}

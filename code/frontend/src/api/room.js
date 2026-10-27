import request from './request'

export function getActiveRooms() {
  return request({
    url: '/api/rooms/active',
    method: 'get'
  })
}

export function getAllRooms(params) {
  return request({
    url: '/api/rooms',
    method: 'get',
    params
  })
}

export function updateRoomStatus(id, status) {
  return request({
    url: `/api/rooms/${id}/status`,
    method: 'put',
    data: { status }
  })
}

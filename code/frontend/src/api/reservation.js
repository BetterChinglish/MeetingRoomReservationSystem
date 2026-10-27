import request from './request'

export function createReservation(data) {
  return request({
    url: '/api/reservations',
    method: 'post',
    data
  })
}

export function getReservations(params) {
  return request({
    url: '/api/reservations',
    method: 'get',
    params
  })
}

export function getReservationDetail(id) {
  return request({
    url: `/api/reservations/${id}`,
    method: 'get'
  })
}

export function cancelReservation(id) {
  return request({
    url: `/api/reservations/${id}/cancel`,
    method: 'post'
  })
}

export function earlyEndReservation(id) {
  return request({
    url: `/api/reservations/${id}/early-end`,
    method: 'post'
  })
}

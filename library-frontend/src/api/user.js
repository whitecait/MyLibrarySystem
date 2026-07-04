import request from '@/utils/request'

export function loginAdmin(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data: {
      username: data.username,
      password: data.password,
    },
  })
}

export function getReaderInfo() {
  const raw = localStorage.getItem('user')
  return Promise.resolve({
    code: 0,
    msg: 'success',
    data: raw ? JSON.parse(raw) : null,
  })
}

export function updateReaderInfo(data) {
  return request({
    url: '/readers/update',
    method: 'post',
    data,
  })
}

import request from '@/utils/request'

export function loginReader(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data: {
      username: data.username,
      password: data.password,
    },
  })
}

export function getReaderPage(params) {
  return request({
    url: '/readers/page',
    method: 'get',
    params,
  })
}

export function registerReader(data) {
  return request({
    url: '/auth/register',
    method: 'post',
    data: {
      studentId: data.studentId || data.xueshengxuehao,
      name: data.name || data.xingming,
      password: data.password || data.mima,
      gender: data.gender || data.xingbie,
      department: data.department || data.yuanxi,
      phone: data.phone || data.shouji,
    },
  })
}

export function saveReader(data) {
  return request({
    url: '/readers/save',
    method: 'post',
    data,
  })
}

export function updateReader(data) {
  return request({
    url: '/readers/update',
    method: 'post',
    data,
  })
}

export function deleteReader(ids) {
  return request({
    url: '/readers/delete',
    method: 'post',
    data: ids,
  })
}

export function resetReaderPassword(username) {
  return request({
    url: '/readers/resetPass',
    method: 'post',
    data: { username },
  })
}

import request from '@/utils/request'

// 获取归还列表
export function getReturnList(params) {
  return request({
    url: '/tushuguihai/page',
    method: 'get',
    params
  })
}

// 申请归还
export function addReturn(data) {
  return request({
    url: '/tushuguihai/add',
    method: 'post',
    data
  })
}

// 审核归还 (批量)
export function approveReturn(data) {
  return request({
    url: '/tushuguihai/shBatch',
    method: 'post',
    params: {
      sfsh: data.sfsh,
      shhf: data.shhf
    },
    data: data.ids
  })
}

// 删除归还记录
export function deleteReturn(ids) {
  return request({
    url: '/tushuguihai/delete',
    method: 'post',
    data: ids
  })
}

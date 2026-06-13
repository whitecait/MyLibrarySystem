import request from '@/utils/request'

// 获取续借列表
export function getRenewalList(params) {
    return request({
        url: '/tushuxujie/page',
        method: 'get',
        params
    })
}

// 审核续借 (批量)
export function approveRenewal(data) {
    return request({
        url: '/tushuxujie/shBatch',
        method: 'post',
        params: {
            sfsh: data.sfsh,
            shhf: data.shhf
        },
        data: data.ids
    })
}

// 申请续借
export function addRenewal(data) {
    return request({
        url: '/tushuxujie/add',
        method: 'post',
        data
    })
}

// 删除续借记录
export function deleteRenewal(ids) {
    return request({
        url: '/tushuxujie/delete',
        method: 'post',
        data: ids
    })
}

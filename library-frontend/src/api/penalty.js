import request from '@/utils/request'

// 获取罚款列表
export function getPenaltyList(params) {
    return request({
        url: '/chaoqifakuan/page',
        method: 'get',
        params
    })
}

// 获取我的罚款列表
export const getMyFines = (params) => {
    return request({
        url: '/chaoqifakuan/page',
        method: 'get',
        params
    });
};

// 新增罚款
export function addPenalty(data) {
    return request({
        url: '/chaoqifakuan/save',
        method: 'post',
        data
    })
}

// 修改罚款
export function updatePenalty(data) {
    return request({
        url: '/chaoqifakuan/update',
        method: 'post',
        data
    })
}

// 删除罚款
export function deletePenalty(ids) {
    return request({
        url: '/chaoqifakuan/delete',
        method: 'post',
        data: ids
    })
}

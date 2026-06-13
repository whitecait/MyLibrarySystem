import request from '@/utils/request';

// 获取我的借阅列表
export const getMyBorrowings = (params) => {
    return request({
        url: '/tushujieyue/page',
        method: 'get',
        params
    });
};

// 获取我的罚款列表
export const getMyFines = (params) => {
    return request({
        url: '/chaoqifakuan/page',
        method: 'get',
        params
    });
};

// 审核借阅 (批量)
export function approveBorrow(data) {
    return request({
        url: '/tushujieyue/shBatch',
        method: 'post',
        params: {
            sfsh: data.sfsh,
            shhf: data.shhf
        },
        data: data.ids
    })
}

// 借阅图书
export function addBorrow(data) {
    return request({
        url: '/tushujieyue/add',
        method: 'post',
        data
    })
}

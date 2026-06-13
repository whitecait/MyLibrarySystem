import request from '@/utils/request';

// 获取管理员信息
export function getAdminSession() {
    return request({
        url: '/users/session',
        method: 'get'
    });
}

// 获取读者信息
export function getReaderSession() {
    return request({
        url: '/duzhe/session',
        method: 'get'
    });
}

// 获取图书总数
export function getBookTotal() {
    return request({
        url: '/tushuxinxi/page?limit=1',
        method: 'get'
    });
}

// 获取读者总数
export function getReaderTotal() {
    return request({
        url: '/duzhe/page?limit=1',
        method: 'get'
    });
}

// 获取借阅记录（用于统计）
export function getBorrowPage(params) {
    return request({
        url: '/tushujieyue/page',
        method: 'get',
        params
    });
}

// 获取罚款记录（用于统计）
export function getFinePage(params) {
    return request({
        url: '/chaoqifakuan/page',
        method: 'get',
        params
    });
}

// 获取公告列表
export function getNewsList(params) {
    return request({
        url: '/news/page',
        method: 'get',
        params
    });
}

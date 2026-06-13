import request from '@/utils/request';

// 管理员登录
export function loginAdmin(data) {
    return request({
        url: '/users/login',
        method: 'post',
        params: data
    });
}

// 获取当前读者信息 (通过session)
export function getReaderInfo() {
    return request({
        url: '/duzhe/session',
        method: 'get'
    })
}

// 更新读者信息
export function updateReaderInfo(data) {
    return request({
        url: '/duzhe/update',
        method: 'post',
        data
    })
}

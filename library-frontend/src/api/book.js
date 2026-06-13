import request from '@/utils/request';

/**
 * 获取图书分页列表
 * @param {object} params 查询参数
 */
export function getBookPage(params) {
    return request({
        url: '/tushuxinxi/page',
        method: 'get',
        params,
    });
}

/**
 * 获取图书列表
 * @param {object} params 查询参数
 */
export function getBookList(params) {
    return request({
        url: '/tushuxinxi/list',
        method: 'get',
        params,
    });
}

/**
 * 保存图书信息
 * @param {object} data 图书信息
 */
export function saveBook(data) {
    return request({
        url: '/tushuxinxi/save',
        method: 'post',
        data,
    });
}

/**
 * 更新图书信息
 * @param {object} data 图书信息
 */
export function updateBook(data) {
    return request({
        url: '/tushuxinxi/update',
        method: 'post',
        data,
    });
}

/**
 * 删除图书
 * @param {Array<number>} data 图书ID数组
 */
export function deleteBook(data) {
    return request({
        url: '/tushuxinxi/delete',
        method: 'post',
        data,
    });
}

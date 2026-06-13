import request from '@/utils/request'

// 获取新闻列表
export function getNewsList(params) {
    return request({
        url: '/news/page',
        method: 'get',
        params
    })
}

// 获取新闻详情
export function getNewsDetail(id) {
    return request({
        url: `/news/info/${id}`,
        method: 'get'
    })
}

// 新增新闻
export function addNews(data) {
    return request({
        url: '/news/save',
        method: 'post',
        data
    })
}

// 修改新闻
export function updateNews(data) {
    return request({
        url: '/news/update',
        method: 'post',
        data
    })
}

// 删除新闻
export function deleteNews(ids) {
    return request({
        url: '/news/delete',
        method: 'post',
        data: ids
    })
}

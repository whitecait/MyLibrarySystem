import request from '@/utils/request';

// 分页查询图书分类
export const getCategoryPage = (params) => {
    return request({
        url: '/tushuleibie/page',
        method: 'get',
        params
    });
};

// 获取所有图书分类
export const getAllCategories = () => {
    return request({
        url: '/tushuleibie/list',
        method: 'get',
        params: {
            page: 1,
            limit: 1000 // Assuming there are less than 1000 categories
        }
    });
};

// 获取图书类别下拉选项
export const getCategoryOptions = () => {
    return request({
        url: '/option/tushuleibie/tushuleixing',
        method: 'get'
    });
};


// 新增图书分类
export const saveCategory = (data) => {
    return request({
        url: '/tushuleibie/save',
        method: 'post',
        data
    });
};

// 更新图书分类
export const updateCategory = (data) => {
    return request({
        url: '/tushuleibie/update',
        method: 'post',
        data
    });
};

// 删除图书分类
export const deleteCategory = (ids) => {
    return request({
        url: '/tushuleibie/delete',
        method: 'post',
        data: ids
    });
};

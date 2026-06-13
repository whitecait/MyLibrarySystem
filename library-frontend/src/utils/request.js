import axios from 'axios';
import { ElMessage } from 'element-plus';
import router from '../router'; // 引入 router

// 创建 axios 实例
const service = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL, // api 的 base_url
    timeout: 15000, // 请求超时时间
});

// request 拦截器
service.interceptors.request.use(
    config => {
        // 在发送请求之前做些什么
        const token = localStorage.getItem('token');
        if (token) {
            // 让每个请求携带自定义token
            config.headers['token'] = token;
        }
        return config;
    },
    error => {
        // 对请求错误做些什么
        console.log(error); // for debug
        return Promise.reject(error);
    }
);

// response 拦截器
service.interceptors.response.use(
    (response) => {
        const res = response.data;
        // 如果自定义代码不是 0，则判断为错误。
        if (res.code !== 0) {
            ElMessage({
                message: res.msg || 'Error',
                type: 'error',
                duration: 5 * 1000,
            });

            // 50008: 非法的token; 50012: 其他客户端登录了; 50014: Token 过期了;
            if (res.code === 401) {
                // 清除本地token并跳转到登录页
                localStorage.removeItem('token');
                router.push('/login');
            }
            return Promise.reject(new Error(res.msg || 'Error'));
        } else {
            return res;
        }
    },
    (error) => {
        console.log('err' + error); // for debug
        ElMessage({
            message: error.message,
            type: 'error',
            duration: 5 * 1000,
        });
        return Promise.reject(error);
    }
);

export default service;

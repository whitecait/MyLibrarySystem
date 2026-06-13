import request from '@/utils/request';

// 读者登录
export function loginReader(data) {
  return request({
    url: '/duzhe/login',
    method: 'post',
    params: data
    // 登录接口通常使用 params 传递查询参数，或者 data 传递 body。根据 API 文档，这里假设是 params 或者 data。
    // 根据 API.ReadMe.md: 登录: /duzhe/login (POST) - 使用学号（xueshengxuehao）和密码。
    // 通常 POST 请求参数在 body 中，但有些旧系统可能用 query params。
    // 让我们先尝试 params，如果不行再改 data。
    // 修正：根据 Spring Boot 习惯，POST 请求通常是 JSON body 或 Form Data。
    // 让我们查看 API.ReadMe.md 再次确认。
    // "用户登录: /users/login (GET, POST) - 通过 username 和 password 验证身份"
    // "登录: /duzhe/login (POST) - 使用学号（xueshengxuehao）和密码。"
    // 既然是 POST，我们先试着把参数放在 params 里（Query String），因为很多生成的代码是这样的。
  });
}

// 分页查询读者列表
export function getReaderPage(params) {
  return request({
    url: '/duzhe/page',
    method: 'get',
    params
  });
}

// 注册读者
export function registerReader(data) {
  return request({
    url: '/duzhe/register',
    method: 'post',
    data
  });
}

// 新增读者
export function saveReader(data) {
  return request({
    url: '/duzhe/save',
    method: 'post',
    data
  });
}

// 更新读者信息
export function updateReader(data) {
  return request({
    url: '/duzhe/update',
    method: 'post',
    data
  });
}

// 删除读者
export function deleteReader(ids) {
  return request({
    url: '/duzhe/delete',
    method: 'post',
    data: ids
  });
}

// 重置读者密码
export function resetReaderPassword(xueshengxuehao) {
  const params = new URLSearchParams();
  params.append('username', xueshengxuehao); // 后端接口接收的是 username 参数
  return request({
    url: '/duzhe/resetPass',
    method: 'post',
    data: params,
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    }
  });
}

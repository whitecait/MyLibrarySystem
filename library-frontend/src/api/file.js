import request from '@/utils/request';

/**
 * 文件上传
 * @param {File} file - 要上传的文件
 * @returns 
 */
export function uploadFile(file) {
  const formData = new FormData();
  formData.append('file', file);
  return request({
    url: '/file/upload',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
}

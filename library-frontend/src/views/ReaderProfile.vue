<template>
    <div class="reader-profile-container">
        <el-card class="box-card">
            <template #header>
                <div class="card-header">
                    <span>个人信息</span>
                </div>
            </template>

            <el-form ref="formRef" :model="form" :rules="rules" label-width="100px"
                style="max-width: 500px; margin: 0 auto;">
                <el-form-item label="账号" prop="xueshengxuehao">
                    <el-input v-model="form.xueshengxuehao" disabled></el-input>
                </el-form-item>
                <el-form-item label="姓名" prop="xingming">
                    <el-input v-model="form.xingming" placeholder="请输入姓名"></el-input>
                </el-form-item>
                <el-form-item label="头像" prop="touxiang">
                    <el-upload class="avatar-uploader" action="/api/file/upload" :show-file-list="false"
                        :on-success="handleAvatarSuccess" :headers="uploadHeaders">
                        <img v-if="form.touxiang" :src="getImgUrl(form.touxiang)" class="avatar" />
                        <el-icon v-else class="avatar-uploader-icon">
                            <Plus />
                        </el-icon>
                    </el-upload>
                </el-form-item>
                <el-form-item label="生日" prop="chushengnianyue">
                    <el-date-picker v-model="form.chushengnianyue" type="date" placeholder="选择日期" format="YYYY-MM-DD"
                        value-format="YYYY-MM-DD" style="width: 100%" />
                </el-form-item>
                <el-form-item label="院系" prop="yuanxi">
                    <el-input v-model="form.yuanxi"></el-input>
                </el-form-item>
                <el-form-item label="密码" prop="mima">
                    <el-input v-model="form.mima" type="password" show-password placeholder="如果不修改请留空"
                        autocomplete="new-password"></el-input>
                </el-form-item>
                <el-form-item label="性别" prop="xingbie">
                    <el-select v-model="form.xingbie" placeholder="请选择性别">
                        <el-option label="男" value="男"></el-option>
                        <el-option label="女" value="女"></el-option>
                    </el-select>
                </el-form-item>
                <el-form-item label="手机" prop="shouji">
                    <el-input v-model="form.shouji" maxlength="11"></el-input>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="submitForm">保存修改</el-button>
                </el-form-item>
            </el-form>
        </el-card>
    </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getReaderInfo, updateReaderInfo } from '@/api/user'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getImgUrl } from '@/utils/index'

const formRef = ref(null)
const form = ref({
    xueshengxuehao: '',
    mima: '',
    xingming: '',
    xingbie: '',
    chushengnianyue: '',
    yuanxi: '',
    shouji: '',
    touxiang: ''
})

const uploadHeaders = {
    'Token': localStorage.getItem('token')
}

// 校验规则
const rules = {
    xingming: [
        { required: true, message: '请输入姓名', trigger: 'blur' },
        { pattern: /^[\u4e00-\u9fa5a-zA-Z0-9_]+$/, message: '姓名包含非法字符，仅支持中英文、数字及下划线', trigger: 'blur' }
    ],
    mima: [
        { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
    ],
    shouji: [
        { required: true, message: '请输入手机号', trigger: 'blur' },
        { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的11位手机号码', trigger: 'blur' }
    ]
}

const fetchData = async () => {
    try {
        const res = await getReaderInfo()
        if (res.code === 0) {
            form.value = res.data
            // 安全起见，清空密码显示
            form.value.mima = ''
        } else {
            ElMessage.error(res.msg || '获取个人信息失败')
        }
    } catch (error) {
        console.error(error)
        ElMessage.error('获取个人信息失败')
    }
}

const handleAvatarSuccess = (res) => {
    if (res.code === 0) {
        form.value.touxiang = res.file
        ElMessage.success('头像上传成功')
    } else {
        ElMessage.error('上传失败: ' + res.msg)
    }
}

const submitForm = async () => {
    if (!formRef.value) return
    await formRef.value.validate(async (valid) => {
        if (valid) {
            try {
                // 如果密码为空，说明未修改，删除该字段以防覆盖
                const payload = { ...form.value }
                if (!payload.mima) {
                    delete payload.mima
                }

                const res = await updateReaderInfo(payload)
                if (res.code === 0) {
                    ElMessage.success('修改成功')
                    fetchData()
                } else {
                    ElMessage.error(res.msg || '修改失败')
                }
            } catch (error) {
                console.error(error)
                ElMessage.error('修改失败')
            }
        }
    })
}

onMounted(() => {
    fetchData()
})
</script>

<style scoped>
.reader-profile-container {
    padding: 20px;
}

.avatar-uploader .avatar {
    width: 100px;
    height: 100px;
    display: block;
    border-radius: 6px;
    object-fit: cover;
}

.avatar-uploader .el-upload {
    border: 1px dashed var(--el-border-color);
    border-radius: 6px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    transition: var(--el-transition-duration-fast);
}

.avatar-uploader .el-upload:hover {
    border-color: var(--el-color-primary);
}

.avatar-uploader-icon {
    font-size: 28px;
    color: #8c939d;
    width: 100px;
    height: 100px;
    text-align: center;
    line-height: 100px;
}
</style>

<template>
    <div class="login-container">
        <!-- 登录表单 -->
        <el-form v-if="!isRegister" :model="form" :rules="rules" ref="loginFormRef" label-width="80px" class="login-form">
            <h2 class="login-title">图书管理系统</h2>

            <el-form-item label="角色">
                <el-radio-group v-model="role">
                    <el-radio label="reader">读者</el-radio>
                    <el-radio label="admin">管理员</el-radio>
                </el-radio-group>
            </el-form-item>

            <el-form-item :label="role === 'admin' ? '用户名' : '学号'" prop="username">
                <el-input v-model="form.username" :placeholder="role === 'admin' ? '请输入用户名' : '请输入学号'"></el-input>
            </el-form-item>
            <el-form-item label="密码" prop="password">
                <el-input type="password" v-model="form.password" placeholder="请输入密码" show-password></el-input>
            </el-form-item>
            <el-form-item>
                <el-button type="primary" @click="handleLogin" :loading="loading" style="width: 100%;">登录</el-button>
            </el-form-item>
            <!-- 注册切换按钮 -->
            <div class="register-link" v-if="role === 'reader'">
                <el-link type="primary" @click="toggleMode">没有账号？立即注册</el-link>
            </div>
        </el-form>

        <!-- 注册表单 -->
        <el-form v-else :model="registerForm" :rules="registerRules" ref="registerFormRef" label-width="80px"
            class="login-form">
            <h2 class="login-title">读者注册</h2>

            <el-form-item label="学号" prop="xueshengxuehao">
                <el-input v-model="registerForm.xueshengxuehao" placeholder="请输入学号"></el-input>
            </el-form-item>
            <el-form-item label="姓名" prop="xingming">
                <el-input v-model="registerForm.xingming" placeholder="请输入姓名"></el-input>
            </el-form-item>
            <el-form-item label="密码" prop="mima">
                <el-input type="password" v-model="registerForm.mima" placeholder="请输入密码" show-password></el-input>
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmMima">
                <el-input type="password" v-model="registerForm.confirmMima" placeholder="请再次输入密码" show-password></el-input>
            </el-form-item>
            <el-form-item>
                <el-button type="primary" @click="handleRegister" :loading="loading" style="width: 100%;">注册</el-button>
            </el-form-item>
            <div class="register-link">
                <el-link type="primary" @click="toggleMode">已有账号？返回登录</el-link>
            </div>
        </el-form>
    </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { loginAdmin } from '@/api/user';
import { loginReader, registerReader } from '@/api/reader';

const loginFormRef = ref(null);
const registerFormRef = ref(null);
const router = useRouter();
const loading = ref(false);
const role = ref('reader'); // 默认为读者
const isRegister = ref(false); // 是否为注册模式

const form = reactive({
    username: '',
    password: ''
});

const registerForm = reactive({
    xueshengxuehao: '',
    mima: '',
    confirmMima: '',
    xingming: ''
});

const rules = ref({
    username: [
        { required: true, message: '请输入账号', trigger: 'blur' }
        // TODO: 在此处添加账号格式校验逻辑，例如学号长度或字符限制
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' }
        // TODO: 在此处添加密码复杂度校验逻辑，例如最小长度、特殊字符要求
    ]
});

const registerRules = reactive({
    xueshengxuehao: [
        { required: true, message: '请输入学号', trigger: 'blur' }
        // TODO: 在此处添加学号唯一性或格式校验逻辑
    ],
    mima: [
        { required: true, message: '请输入密码', trigger: 'blur' }
        // TODO: 在此处添加密码强度校验逻辑
    ],
    confirmMima: [
        { required: true, message: '请再次输入密码', trigger: 'blur' },
        {
            validator: (rule, value, callback) => {
                if (value !== registerForm.mima) {
                    callback(new Error('两次输入密码不一致!'));
                } else {
                    callback();
                }
            }, trigger: 'blur'
        }
    ],
    xingming: [
        { required: true, message: '请输入姓名', trigger: 'blur' }
        // TODO: 在此处添加姓名格式校验逻辑，例如禁止特殊字符
    ]
});

// 切换角色时重置表单
watch(role, () => {
    form.username = '';
    form.password = '';
    if (loginFormRef.value) {
        loginFormRef.value.clearValidate();
    }
});

// 切换登录/注册模式
const toggleMode = () => {
    isRegister.value = !isRegister.value;
    // 重置表单
    form.username = '';
    form.password = '';
    registerForm.xueshengxuehao = '';
    registerForm.mima = '';
    registerForm.confirmMima = '';
    registerForm.xingming = '';
    if (loginFormRef.value) loginFormRef.value.clearValidate();
    if (registerFormRef.value) registerFormRef.value.clearValidate();
};

const handleLogin = () => {
    loginFormRef.value.validate(async (valid) => {
        if (valid) {
            loading.value = true;
            try {
                let res;
                if (role.value === 'admin') {
                    res = await loginAdmin({
                        username: form.username,
                        password: form.password
                    });
                } else {
                    res = await loginReader({
                        username: form.username, // 后端映射为学生学号
                        password: form.password
                    });
                }

                if (res.code === 0) {
                    localStorage.setItem('token', res.token);
                    localStorage.setItem('role', role.value); // 存储角色信息
                    if (res.user) {
                        localStorage.setItem('user', JSON.stringify(res.user));
                    }
                    ElMessage.success('登录成功！');
                    router.push('/');
                } else {
                    ElMessage.error(res.msg || '登录失败');
                }
            } catch (error) {
                console.error("登录失败:", error);
                // 错误信息已在 request.js 拦截器中处理，这里可以不重复提示，或者提示通用错误
            } finally {
                loading.value = false;
            }
        }
    });
};

const handleRegister = () => {
    registerFormRef.value.validate(async (valid) => {
        if (valid) {
            loading.value = true;
            try {
                const res = await registerReader({
                    xueshengxuehao: registerForm.xueshengxuehao,
                    mima: registerForm.mima,
                    xingming: registerForm.xingming,
                    role: '读者'
                });

                if (res.code === 0) {
                    ElMessage.success('注册成功，请登录！');
                    toggleMode(); // 切换回登录
                } else {
                    ElMessage.error(res.msg || '注册失败');
                }
            } catch (error) {
                console.error("注册失败:", error);
                ElMessage.error('注册失败，请稍后重试');
            } finally {
                loading.value = false;
            }
        }
    });
};
</script>

<style scoped>
.register-link {
    text-align: right;
    margin-top: 10px;
}

.login-container {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    background-color: #f2f6fc;
}

.login-form {
    width: 400px;
    padding: 40px;
    border-radius: 10px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
    background-color: #fff;
}

.login-title {
    text-align: center;
    margin-bottom: 30px;
    color: #303133;
}
</style>
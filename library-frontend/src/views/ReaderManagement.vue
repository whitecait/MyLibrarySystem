<template>
    <div class="reader-management-container">
        <!-- 搜索区域 -->
        <el-card class="box-card">
            <el-form :inline="true" :model="searchForm" class="demo-form-inline">
                <el-form-item label="学号">
                    <el-input v-model="searchForm.xueshengxuehao" placeholder="请输入学号"></el-input>
                </el-form-item>
                <el-form-item label="姓名">
                    <el-input v-model="searchForm.xingming" placeholder="请输入姓名"></el-input>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="onSearch">查询</el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <!-- 按钮及表格区域 -->
        <el-card class="box-card" style="margin-top: 20px;">
            <div class="table-header">
                <el-button type="primary" @click="handleAdd">新增读者</el-button>
            </div>
            <el-table :data="tableData" style="width: 100%; margin-top: 20px;" border>
                <el-table-column prop="xueshengxuehao" label="学号"></el-table-column>
                <el-table-column prop="xingming" label="姓名"></el-table-column>
                <el-table-column label="操作" width="300" fixed="right">
                    <template #default="scope">
                        <el-button size="small" @click="handleEdit(scope.row)">查看/编辑</el-button>
                        <el-popconfirm :title="`确定要重置 ${scope.row.xingming} 的密码吗？`"
                            @confirm="handleResetPassword(scope.row)">
                            <template #reference>
                                <el-button size="small" type="warning">重置密码</el-button>
                            </template>
                        </el-popconfirm>
                        <el-popconfirm title="您确定要删除吗？" @confirm="handleDelete(scope.row)">
                            <template #reference>
                                <el-button size="small" type="danger">删除</el-button>
                            </template>
                        </el-popconfirm>
                    </template>
                </el-table-column>
            </el-table>

            <!-- 分页组件 -->
            <el-pagination style="margin-top: 20px;" :current-page="pagination.page" :page-sizes="[10, 20, 50, 100]"
                :page-size="pagination.limit" layout="total, sizes, prev, pager, next, jumper" :total="pagination.total"
                @size-change="handleSizeChange" @current-change="handleCurrentChange"></el-pagination>
        </el-card>

        <!-- 阅读/编辑 详情对话框 -->
        <el-dialog v-model="dialogVisible" :title="dialogTitle" width="70%" top="5vh">
            <el-tabs type="border-card">
                <!-- Tab 1: 基本信息 -->
                <el-tab-pane label="基本信息">
                    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
                        <el-form-item label="学号" prop="xueshengxuehao">
                            <el-input v-model="form.xueshengxuehao" />
                        </el-form-item>
                        <el-form-item label="姓名" prop="xingming">
                            <el-input v-model="form.xingming" />
                        </el-form-item>
                        <el-form-item label="密码" prop="mima" v-if="!form.id">
                            <el-input v-model="form.mima" type="password" show-password />
                        </el-form-item>
                        <el-form-item label="性别" prop="xingbie">
                            <el-radio-group v-model="form.xingbie">
                                <el-radio label="男">男</el-radio>
                                <el-radio label="女">女</el-radio>
                            </el-radio-group>
                        </el-form-item>
                        <el-form-item label="院系" prop="yuanxi">
                            <el-input v-model="form.yuanxi" />
                        </el-form-item>
                        <el-form-item label="出生年月" prop="chushengnianyue">
                            <el-date-picker v-model="form.chushengnianyue" type="date" placeholder="选择日期"
                                format="YYYY-MM-DD" value-format="YYYY-MM-DD" />
                        </el-form-item>
                        <el-form-item label="手机" prop="shouji">
                            <el-input v-model="form.shouji" />
                        </el-form-item>
                        <el-form-item label="头像" prop="touxiang">
                            <el-upload class="avatar-uploader" action="/api/file/upload" :show-file-list="false"
                                :on-success="handleUploadSuccess" :headers="uploadHeaders">
                                <img v-if="form.touxiang" :src="getImgUrl(form.touxiang)" class="avatar" />
                                <el-icon v-else class="avatar-uploader-icon">
                                    <Plus />
                                </el-icon>
                            </el-upload>
                        </el-form-item>
                        <el-form-item>
                            <el-button type="primary" @click="submitForm">保存修改</el-button>
                        </el-form-item>
                    </el-form>
                </el-tab-pane>

                <!-- Tab 2: 借阅记录 (仅在编辑时显示) -->
                <el-tab-pane label="借阅记录" v-if="form.id">
                    <el-table :data="borrowData" style="width: 100%" border stripe height="400">
                        <el-table-column prop="jieyuebianhao" label="借阅编号" width="140"></el-table-column>
                        <el-table-column prop="tushumingcheng" label="图书名称" min-width="120"></el-table-column>
                        <el-table-column prop="jieshuriqi" label="借书日期" width="110"></el-table-column>
                        <el-table-column prop="yuhairiqi" label="应还日期" width="110"></el-table-column>
                        <el-table-column label="状态" width="100">
                            <template #default="scope">
                                <el-tag :type="getStatusType(scope.row.sfsh)">
                                    {{ getStatusText(scope.row.sfsh) }}
                                </el-tag>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-tab-pane>

                <!-- Tab 3: 罚款记录 (仅在编辑时显示) -->
                <el-tab-pane label="罚款记录" v-if="form.id">
                    <el-table :data="fineData" style="width: 100%" border stripe height="400">
                        <el-table-column prop="tushumingcheng" label="图书名称" min-width="120"></el-table-column>
                        <el-table-column prop="fakuanjine" label="罚款金额" width="100">
                            <template #default="scope">
                                <span style="color: red;">￥{{ scope.row.fakuanjine }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column prop="ispay" label="支付状态" width="100">
                            <template #default="scope">
                                <el-tag :type="scope.row.ispay === '已支付' ? 'success' : 'danger'">
                                    {{ scope.row.ispay || '未支付' }}
                                </el-tag>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-tab-pane>
            </el-tabs>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="dialogVisible = false">关闭</el-button>
                </span>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { getReaderPage, saveReader, updateReader, deleteReader, resetReaderPassword } from '@/api/reader';
import { getMyBorrowings, getMyFines } from '@/api/borrow';
import { ElMessage } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { getImgUrl } from '@/utils/index';

// 搜索表单数据
const searchForm = reactive({
    xueshengxuehao: '',
    xingming: '',
});

const rules = {
    xueshengxuehao: [
        { required: true, message: '请输入学号', trigger: 'blur' },
        { pattern: /^\d{7}$/, message: '学号必须为7位数字', trigger: 'blur' }
    ],
    xingming: [
        { required: true, message: '请输入姓名', trigger: 'blur' }
    ],
    shouji: [
        { required: true, message: '请输入手机号', trigger: 'blur' },
        { pattern: /^1\d{10}$/, message: '手机号必须为11位数字且以1开头', trigger: 'blur' }
    ],
    mima: [
        { required: true, message: '请输入密码', trigger: 'blur' }
    ]
};

// 表单引用
const formRef = ref(null);

// 表单数据
const form = reactive({
    id: '',
    xueshengxuehao: '',
    mima: '',
    xingming: '',
    xingbie: '男',
    shouji: '',
    youxiang: '',
    shenfenzheng: '',
    touxiang: '',
});

// 表格数据
const tableData = ref([]);

// 分页配置
const pagination = reactive({
    page: 1,
    limit: 10,
    total: 0,
});

// 弹窗控制
const dialogVisible = ref(false);
const dialogTitle = ref('新增读者');

// 获取数据
const fetchData = async () => {
    try {
        console.log('Fetching reader data with params:', { page: pagination.page, limit: pagination.limit, ...searchForm });
        const res = await getReaderPage({
            page: pagination.page,
            limit: pagination.limit,
            ...searchForm
        });
        console.log('Reader API Response:', res);

        if (res && res.code === 0) {
            // 增加空值保护和字段兼容 (PageUtils通常用list，但有时MP返回records)
            const list = res.data ? (res.data.list || res.data.records || []) : [];
            tableData.value = list;
            pagination.total = (res.data && res.data.total) ? res.data.total : 0;
        } else {
            console.warn('API returned non-zero code:', res);
            ElMessage.error(res.msg || '获取数据失败');
        }
    } catch (error) {
        console.error("Failed to fetch reader data:", error);
        ElMessage.error('获取数据出错，请检查网络或控制台');
    }
};

const onSearch = () => {
    pagination.page = 1;
    fetchData();
};

const handleAdd = () => {
    dialogTitle.value = '新增读者';
    dialogVisible.value = true;
    // 重置表单
    Object.keys(form).forEach(key => form[key] = '');
    form.xingbie = '男';
    borrowData.value = [];
    fineData.value = [];
};

onMounted(() => {
    fetchData();
});

// 借阅和罚款数据
const borrowData = ref([]);
const fineData = ref([]);

// 获取借阅记录
const fetchBorrowHistory = async () => {
    if (!form.xueshengxuehao) return;
    try {
        const res = await getMyBorrowings({
            page: 1,
            limit: 1000,
            xueshengxuehao: form.xueshengxuehao
        });
        borrowData.value = res.data.list;
    } catch (error) {
        console.error("Failed to fetch borrow history:", error);
    }
};

// 获取罚款记录
const fetchFineHistory = async () => {
    if (!form.xueshengxuehao) return;
    try {
        const res = await getMyFines({
            page: 1,
            limit: 1000,
            xueshengxuehao: form.xueshengxuehao
        });
        fineData.value = res.data.list;
    } catch (error) {
        console.error("Failed to fetch fine history:", error);
    }
};

// 状态辅助函数
const getStatusType = (status) => {
    if (['是', '已通过', '已归还', '已支付'].includes(status)) return 'success';
    if (['否', '未通过'].includes(status)) return 'danger';
    return 'warning';
};

const getStatusText = (status) => {
    if (status === '是') return '已通过';
    if (status === '否') return '未通过';
    return status;
};

const handleEdit = (row) => {
    Object.assign(form, row);
    dialogTitle.value = '读者详情';
    dialogVisible.value = true;
    fetchBorrowHistory();
    fetchFineHistory();
};

const submitForm = async () => {
    if (!formRef.value) return;
    await formRef.value.validate(async (valid) => {
        if (valid) {
            try {
                if (form.id) {
                    await updateReader(form);
                    ElMessage.success('更新成功');
                } else {
                    await saveReader(form);
                    ElMessage.success('新增成功');
                }
                dialogVisible.value = false;
                fetchData();
            } catch (error) {
                console.error("Operation failed:", error);
                ElMessage.error(error.msg || '操作失败');
            }
        }
    });
};


const handleDelete = async (row) => {
    try {
        await deleteReader([row.id]);
        ElMessage.success('删除成功');
        fetchData();
    } catch (error) {
        console.error('删除失败:', error);
        ElMessage.error('删除失败');
    }
};

const handleResetPassword = async (row) => {
    try {
        await resetReaderPassword(row.xueshengxuehao);
        ElMessage.success(`用户 ${row.xingming} 的密码已重置为123456`);
    } catch (error) {
        console.error('重置密码失败:', error);
        ElMessage.error('重置密码失败');
    }
};

const handleUploadSuccess = (response) => {
    if (response.code === 0) {
        form.touxiang = response.file;
        ElMessage.success('头像上传成功');
    } else {
        ElMessage.error(response.msg || '上传失败');
    }
};

const handleSizeChange = (val) => {
    pagination.limit = val;
    fetchData();
};

const handleCurrentChange = (val) => {
    pagination.page = val;
    fetchData();
};
</script>

<style scoped>
.reader-management-container {
    padding: 20px;
}

.table-header {
    display: flex;
    justify-content: flex-end;
}

.avatar-uploader .avatar {
    width: 100px;
    height: 100px;
    display: block;
}

.avatar-uploader .el-upload {
    border: 1px dashed #d9d9d9;
    border-radius: 6px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
}

.avatar-uploader .el-upload:hover {
    border-color: #409EFF;
}

.avatar-uploader-icon {
    font-size: 28px;
    color: #8c939d;
    width: 100px;
    height: 100px;
    text-align: center;
}
</style>

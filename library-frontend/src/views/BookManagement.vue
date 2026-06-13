<template>
    <div class="book-management-container">
        <!-- 搜索区域 -->
        <el-card class="box-card">
            <el-form :inline="true" :model="searchForm" class="demo-form-inline">
                <el-form-item label="图书名称">
                    <el-input v-model="searchForm.tushumingcheng" placeholder="请输入图书名称"></el-input>
                </el-form-item>
                <el-form-item label="作者">
                    <el-input v-model="searchForm.zuozhe" placeholder="请输入作者"></el-input>
                </el-form-item>
                <el-form-item label="图书类别">
                    <!-- TODO: 修改下拉框宽度，根据需要调整 width 属性 -->
                    <el-select v-model="searchForm.tushuleixing" placeholder="请选择图书类别" clearable style="width: 200px;">
                        <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item"></el-option>
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="onSearch">查询</el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <!-- 按钮及表格区域 -->
        <el-card class="box-card" style="margin-top: 20px;">
            <div class="table-header">
                <el-button type="primary" @click="handleAdd">新增图书</el-button>
            </div>
            <el-table :data="tableData" style="width: 100%; margin-top: 20px;" border>
                <el-table-column prop="tushubianhao" label="图书编号" width="180"></el-table-column>
                <el-table-column prop="tushumingcheng" label="图书名称" width="180"></el-table-column>
                <el-table-column prop="tushuleixing" label="图书类别" width="180"></el-table-column>
                <el-table-column prop="tushufengmian" label="图书封面" width="120">
                    <template #default="scope">
                        <!-- 假设后端返回的是完整的图片URL -->
                        <!-- 由于图片数据我们难以支持，我们不对其进行深度处理。此处仅用于保持结构 -->
                        <img v-if="scope.row.tushufengmian" :src="getImgUrl(scope.row.tushufengmian)" alt="封面"
                            style="width: 100px; height: 100px; object-fit: cover;" />
                    </template>
                </el-table-column>
                <el-table-column prop="zuozhe" label="作者"></el-table-column>
                <el-table-column prop="chubanshe" label="出版社"></el-table-column>
                <el-table-column prop="shuliang" label="数量" width="80"></el-table-column>
                <el-table-column label="操作" width="180" fixed="right">
                    <template #default="scope">
                        <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
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

        <!-- 新增/编辑 对话框 -->
        <el-dialog v-model="dialogVisible" :title="dialogTitle" width="50%">
            <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
                <el-form-item label="图书名称" prop="tushumingcheng">
                    <el-input v-model="form.tushumingcheng" placeholder="请输入图书名称" />
                </el-form-item>
                <el-form-item label="图书类别" prop="tushuleixing">
                    <el-select v-model="form.tushuleixing" placeholder="请选择图书类别">
                        <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item"></el-option>
                    </el-select>
                </el-form-item>
                <el-form-item label="图书编号" prop="tushubianhao">
                    <el-input v-model="form.tushubianhao" placeholder="请输入13位数字（如ISBN）" maxlength="13"
                        show-word-limit />
                </el-form-item>
                <el-form-item label="作者" prop="zuozhe">
                    <el-input v-model="form.zuozhe" placeholder="请填写作者姓名" />
                </el-form-item>
                <el-form-item label="出版社" prop="chubanshe">
                    <el-input v-model="form.chubanshe" placeholder="请填写出版社名称" />
                </el-form-item>
                <el-form-item label="数量" prop="shuliang">
                    <el-input-number v-model="form.shuliang" :min="0" :precision="0" />
                </el-form-item>
                <el-form-item label="图书封面" prop="tushufengmian">
                    <el-upload class="avatar-uploader" action="/api/file/upload" :show-file-list="false"
                        :on-success="handleUploadSuccess" :headers="uploadHeaders">
                        <img v-if="form.tushufengmian" :src="getImgUrl(form.tushufengmian)" class="avatar" />
                        <el-icon v-else class="avatar-uploader-icon">
                            <Plus />
                        </el-icon>
                    </el-upload>
                </el-form-item>
                <el-form-item label="详情" prop="xiangqing">
                    <el-input v-model="form.xiangqing" type="textarea" />
                </el-form-item>
            </el-form>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="dialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="submitForm">确定</el-button>
                </span>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { getBookPage, saveBook, updateBook, deleteBook } from '@/api/book';
import { getCategoryOptions } from '@/api/category';
import { uploadFile } from '@/api/file'; // 引入上传API
import { ElMessage } from 'element-plus';
import { Plus } from '@element-plus/icons-vue'; // 引入图标
import { getImgUrl } from '@/utils/index';

// 搜索表单数据
const searchForm = reactive({
    tushumingcheng: '',
    zuozhe: '',
    tushuleixing: ''
});

// 图书类别选项
const categoryOptions = ref([]);

// 表格数据
const tableData = ref([]);

// 分页数据
const pagination = reactive({
    page: 1,
    limit: 10,
    total: 0,
});

// 对话框相关
const dialogVisible = ref(false);
const dialogTitle = ref('');
const formRef = ref(null);
const form = reactive({
    id: null,
    tushumingcheng: '',
    tushuleixing: '',
    tushubianhao: '',
    zuozhe: '',
    chubanshe: '',
    shuliang: 1,
    tushufengmian: '',
    xiangqing: '',
});

// 上传请求头
const uploadHeaders = ref({
    'Token': localStorage.getItem('token')
});

// 校验规则
const rules = {
    tushumingcheng: [{ required: true, message: '请输入图书名称', trigger: 'blur' }],
    tushuleixing: [{ required: true, message: '请选择图书类别', trigger: 'change' }],
    tushubianhao: [
        { required: true, message: '请输入图书编号', trigger: 'blur' },
        { pattern: /^[1-9]\d{12}$/, message: '图书编号必须是13位数字，且不能以0开头', trigger: 'blur' }
    ],
    zuozhe: [{ required: true, message: '请输入作者', trigger: 'blur' }],
    chubanshe: [{ required: true, message: '请输入出版社', trigger: 'blur' }],
    shuliang: [{ required: true, message: '请输入图书数量', trigger: 'blur' }],
};

// 获取数据方法
const fetchData = async () => {
    const params = {
        page: pagination.page,
        limit: pagination.limit,
        ...searchForm,
    };
    // 过滤掉空的搜索条件
    Object.keys(params).forEach(key => {
        if (params[key] === '' || params[key] === null) {
            delete params[key];
        }
    });

    try {
        const res = await getBookPage(params);
        // 后端返回的数据在 res.data.list
        tableData.value = res.data.list;
        pagination.total = parseInt(res.data.total);
    } catch (error) {
        console.error("Failed to fetch book data:", error);
    }
};

// 组件挂载时获取数据
onMounted(() => {
    fetchData();
    fetchCategoryOptions();
});

// 获取图书类别选项
const fetchCategoryOptions = async () => {
    try {
        const res = await getCategoryOptions();
        categoryOptions.value = res.data;
    } catch (error) {
        console.error("Failed to fetch category options:", error);
    }
};

// 实现搜索功能
const onSearch = () => {
    pagination.page = 1; // 每次搜索都回到第一页
    fetchData();
};

// 新增
const handleAdd = () => {
    // 重置表单
    Object.assign(form, {
        id: null,
        tushumingcheng: '',
        tushuleixing: '',
        tushubianhao: '',
        zuozhe: '',
        chubanshe: '',
        shuliang: 1,
        tushufengmian: '',
        xiangqing: '',
    });
    dialogTitle.value = '新增图书';
    dialogVisible.value = true;
};

// 编辑
const handleEdit = (row) => {
    Object.assign(form, row);
    dialogTitle.value = '编辑图书';
    dialogVisible.value = true;
};

// 提交表单
const submitForm = async () => {
    if (!formRef.value) return;
    await formRef.value.validate(async (valid) => {
        if (valid) {
            try {
                if (form.id) {
                    await updateBook(form);
                    ElMessage.success('更新成功');
                } else {
                    await saveBook(form);
                    ElMessage.success('新增成功');
                }
                dialogVisible.value = false;
                fetchData();
            } catch (error) {
                console.error('操作失败:', error);
                ElMessage.error(error.msg || '操作失败');
            }
        }
    });
};

// 文件上传成功处理
const handleUploadSuccess = (response) => {
    if (response.code === 0) {
        form.tushufengmian = response.file;
        ElMessage.success('封面上传成功');
    } else {
        ElMessage.error(response.msg || '上传失败');
    }
};

// 删除
const handleDelete = async (row) => {
    try {
        await deleteBook([row.id]);
        ElMessage.success('删除成功');
        fetchData(); // 刷新表格
    } catch (error) {
        console.error('删除失败:', error);
        ElMessage.error('删除失败');
    }
};

// 实现分页逻辑
const handleSizeChange = (val) => {
    pagination.limit = val;
    fetchData();
};

// 实现分页逻辑
const handleCurrentChange = (val) => {
    pagination.page = val;
    fetchData();
};
</script>

<style scoped>
.book-management-container {
    padding: 20px;
}

.table-header {
    display: flex;
    justify-content: flex-end;
}
</style>

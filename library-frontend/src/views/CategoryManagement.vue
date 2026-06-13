<template>
    <div class="category-management-container">
        <!-- 搜索区域 -->
        <el-card class="box-card">
            <el-form :inline="true" :model="searchForm" class="demo-form-inline">
                <el-form-item label="分类名称">
                    <el-input v-model="searchForm.tushuleixing" placeholder="请输入分类名称"></el-input>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="onSearch">查询</el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <!-- 按钮及表格区域 -->
        <el-card class="box-card" style="margin-top: 20px;">
            <div class="table-header">
                <el-button type="primary" @click="handleAdd">新增分类</el-button>
            </div>
            <el-table :data="tableData" style="width: 100%; margin-top: 20px;" border>
                <el-table-column prop="id" label="ID" width="150"></el-table-column>
                <el-table-column prop="tushuleixing" label="分类名称"></el-table-column>
                <el-table-column prop="addtime" label="创建时间" width="180"></el-table-column>
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
        <el-dialog v-model="dialogVisible" :title="dialogTitle" width="30%">
            <el-form ref="formRef" :model="form" label-width="100px" :rules="rules">
                <el-form-item label="分类名称" prop="tushuleixing">
                    <el-input v-model="form.tushuleixing" />
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
import { getCategoryPage, saveCategory, updateCategory, deleteCategory } from '@/api/category';
import { ElMessage } from 'element-plus';

// 搜索表单数据
const searchForm = reactive({
    tushuleixing: '',
});

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
    tushuleixing: '',
});

// 表单验证规则
const rules = {
    tushuleixing: [
        { required: true, message: '请输入分类名称', trigger: 'blur' },
    ],
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
        const res = await getCategoryPage(params);
        if (res.data && res.data.list) {
            tableData.value = res.data.list;
            pagination.total = parseInt(res.data.total);
        } else {
            tableData.value = [];
            pagination.total = 0;
        }
    } catch (error) {
        console.error("Failed to fetch category data:", error);
        ElMessage.error('获取数据失败');
    }
};

// 组件挂载时获取数据
onMounted(() => {
    fetchData();
});

// 实现搜索功能
const onSearch = () => {
    pagination.page = 1; // 每次搜索都回到第一页
    fetchData();
};

// 新增
const handleAdd = () => {
    // 重置表单
    form.id = null;
    form.tushuleixing = '';

    dialogTitle.value = '新增分类';
    dialogVisible.value = true;
};

// 编辑
const handleEdit = (row) => {
    // 仅复制需要的字段，避免污染
    form.id = row.id;
    form.tushuleixing = row.tushuleixing;

    dialogTitle.value = '编辑分类';
    dialogVisible.value = true;
};

// 提交表单
const submitForm = async () => {
    if (!formRef.value) return;

    await formRef.value.validate(async (valid) => {
        if (valid) {
            try {
                if (form.id) {
                    // 更新
                    await updateCategory(form);
                    ElMessage.success('更新成功');
                } else {
                    // 新增
                    await saveCategory(form);
                    ElMessage.success('新增成功');
                }
                dialogVisible.value = false;
                fetchData(); // 刷新表格
            } catch (error) {
                console.error('表单提交失败:', error);
                ElMessage.error('操作失败');
            }
        }
    });
};

// 删除
const handleDelete = async (row) => {
    try {
        await deleteCategory([row.id]);
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
.category-management-container {
    padding: 20px;
}

.table-header {
    display: flex;
    justify-content: flex-end;
}
</style>

<template>
    <div class="book-browsing-container">
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
                    <el-select v-model="searchForm.tushuleixing" placeholder="请选择图书类别" clearable style="width: 200px;">
                        <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item"></el-option>
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="onSearch">查询</el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <!-- 表格区域 -->
        <el-card class="box-card" style="margin-top: 20px;">
            <el-table :data="tableData" style="width: 100%" border stripe>
                <el-table-column prop="tushubianhao" label="图书编号" width="120"></el-table-column>
                <el-table-column prop="tushumingcheng" label="图书名称" min-width="150"></el-table-column>
                <el-table-column prop="tushuleixing" label="图书类别" width="120"></el-table-column>
                <el-table-column prop="zuozhe" label="作者" width="120"></el-table-column>
                <el-table-column prop="chubanshe" label="出版社" width="150"></el-table-column>
                <el-table-column label="图片" width="100">
                    <template #default="scope">
                        <el-image style="width: 50px; height: 50px" :src="getImgUrl(scope.row.tupian)"
                            :preview-src-list="scope.row.tupian ? [getImgUrl(scope.row.tupian)] : []" fit="cover">
                            <template #error>
                                <div class="image-slot">
                                    <el-icon>
                                        <Picture />
                                    </el-icon>
                                </div>
                            </template>
                        </el-image>
                    </template>
                </el-table-column>
                <el-table-column prop="shuliang" label="数量" width="80"></el-table-column>
                <el-table-column prop="jiage" label="价格" width="80"></el-table-column>
                <el-table-column label="操作" width="120" fixed="right">
                    <template #default="scope">
                        <el-button type="primary" size="small" :disabled="!!borrowedStatusMap[scope.row.tushubianhao]"
                            @click="handleBorrow(scope.row)">
                            {{ borrowedStatusMap[scope.row.tushubianhao] === '待审核' ? '审核中' :
                                (borrowedStatusMap[scope.row.tushubianhao] === '是' ? '已借阅' : '借阅') }}
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>

            <!-- 分页组件 -->
            <el-pagination style="margin-top: 20px;" :current-page="pagination.page" :page-sizes="[10, 20, 50, 100]"
                :page-size="pagination.limit" layout="total, sizes, prev, pager, next, jumper" :total="pagination.total"
                @size-change="handleSizeChange" @current-change="handleCurrentChange"></el-pagination>
        </el-card>

        <!-- 借阅弹窗 -->
        <el-dialog v-model="borrowDialogVisible" title="借阅图书" width="50%">
            <el-form :model="borrowForm" label-width="100px">
                <el-form-item label="借阅编号">
                    <el-input v-model="borrowForm.jieyuebianhao" disabled></el-input>
                </el-form-item>
                <el-form-item label="图书名称">
                    <el-input v-model="borrowForm.tushumingcheng" disabled></el-input>
                </el-form-item>
                <el-form-item label="图书类型">
                    <el-input v-model="borrowForm.tushuleixing" disabled></el-input>
                </el-form-item>
                <el-form-item label="数量">
                    <el-input v-model="borrowForm.shuliang" disabled></el-input>
                </el-form-item>
                <el-form-item label="借书日期">
                    <el-date-picker v-model="borrowForm.jieshuriqi" type="datetime" placeholder="选择日期"
                        value-format="YYYY-MM-DD HH:mm:ss" disabled></el-date-picker>
                </el-form-item>
                <el-form-item label="应还日期">
                    <el-date-picker v-model="borrowForm.yuhairiqi" type="datetime" placeholder="选择日期"
                        value-format="YYYY-MM-DD HH:mm:ss" disabled></el-date-picker>
                </el-form-item>
                <el-form-item label="借阅账号">
                    <el-input v-model="borrowForm.xueshengxuehao" disabled></el-input>
                </el-form-item>
                <el-form-item label="借阅姓名">
                    <el-input v-model="borrowForm.xingming" disabled></el-input>
                </el-form-item>
                <el-form-item label="借阅状态">
                    <el-input v-model="borrowForm.sfsh" disabled></el-input>
                </el-form-item>
            </el-form>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="borrowDialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="submitBorrow">确认借阅</el-button>
                </span>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { getBookPage } from '@/api/book';
import { getCategoryOptions } from '@/api/category';
import { addBorrow, getMyBorrowings } from '@/api/borrow';
import { Picture } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';
import { getImgUrl } from '@/utils/index';

// 搜索表单数据
const searchForm = reactive({
    tushumingcheng: '',
    zuozhe: '',
    tushuleixing: ''
});

// 图书类别选项
const categoryOptions = ref([]);

// 借阅状态映射 { tushubianhao: '状态' }
const borrowedStatusMap = reactive({});

// 表格数据
const tableData = ref([]);

// 分页数据
const pagination = reactive({
    page: 1,
    limit: 10,
    total: 0,
});

// 借阅弹窗相关
const borrowDialogVisible = ref(false);
const borrowForm = reactive({
    jieyuebianhao: '',
    tushubianhao: '',
    tushumingcheng: '',
    tushuleixing: '',
    zuozhe: '',
    chubanshe: '',
    shuliang: 1,
    jieyuekahao: '',
    xueshengxuehao: '',
    xingming: '',
    shouji: '',
    jieshuriqi: '',
    yuhairiqi: '',
    sfsh: '待审核'
});

// 获取借阅状态
const fetchBorrowedStatus = async () => {
    try {
        // 清空现有状态
        for (const key in borrowedStatusMap) {
            delete borrowedStatusMap[key];
        }

        // 获取当前用户的所有借阅记录
        const res = await getMyBorrowings({
            page: 1,
            limit: 1000, // 获取足够多的记录以覆盖所有
        });

        if (res.code === 0 && res.data && res.data.list) {
            res.data.list.forEach(item => {
                // 只要是未归还的（待审核或已通过），都记录下来
                // 注意：后端逻辑中，已归还的状态是 "已归还"，所以这里只要不是 "已归还" 和 "否" (审核不通过) 就可以认为是占用中
                // 但根据 TushujieyueController.add 的逻辑，它是检查 sfsh in ("是", "待审核")
                if (item.sfsh === '待审核' || item.sfsh === '是') {
                    borrowedStatusMap[item.tushubianhao] = item.sfsh;
                }
            });
        }
    } catch (error) {
        console.error('获取借阅状态失败', error);
    }
};

// 获取数据方法
const fetchData = async () => {
    const params = {
        page: pagination.page,
        limit: pagination.limit,
        ...searchForm,
    };
    // 移除空参数
    Object.keys(params).forEach(key => {
        if (params[key] === '' || params[key] === null) {
            delete params[key];
        }
    });

    try {
        const res = await getBookPage(params);
        tableData.value = res.data.list;
        pagination.total = parseInt(res.data.total);
        // 获取图书列表后，同时获取借阅状态
        fetchBorrowedStatus();
    } catch (error) {
        console.error("Failed to fetch book data:", error);
    }
};

onMounted(async () => {
    fetchData();
    const res = await getCategoryOptions();
    if (res && res.data) {
        categoryOptions.value = res.data;
    }
});

const onSearch = () => {
    pagination.page = 1;
    fetchData();
};

const handleSizeChange = (val) => {
    pagination.limit = val;
    fetchData();
};

const handleCurrentChange = (val) => {
    pagination.page = val;
    fetchData();
};

// 点击借阅按钮
const handleBorrow = (row) => {
    const user = JSON.parse(localStorage.getItem('user') || '{}');

    borrowForm.jieyuebianhao = dayjs().format('YYYYMMDDHHmmss');
    borrowForm.tushubianhao = row.tushubianhao;
    borrowForm.tushumingcheng = row.tushumingcheng;
    borrowForm.tushuleixing = row.tushuleixing;
    borrowForm.zuozhe = row.zuozhe;
    borrowForm.chubanshe = row.chubanshe;
    borrowForm.shuliang = 1;
    borrowForm.jieyuekahao = user.jieyuekahao || user.xueshengxuehao;
    borrowForm.xueshengxuehao = user.xueshengxuehao;
    borrowForm.xingming = user.xingming;
    borrowForm.shouji = user.shouji;
    borrowForm.jieshuriqi = dayjs().format('YYYY-MM-DD HH:mm:ss');
    borrowForm.yuhairiqi = dayjs().add(30, 'day').format('YYYY-MM-DD HH:mm:ss');
    borrowForm.sfsh = '待审核';

    borrowDialogVisible.value = true;
};

// 提交借阅
const submitBorrow = async () => {
    try {
        const res = await addBorrow(borrowForm);
        if (res.code === 0) {
            ElMessage.success('借阅申请提交成功');
            borrowDialogVisible.value = false;
            fetchBorrowedStatus(); // 刷新借阅状态
        } else {
            ElMessage.error(res.msg || '借阅失败');
        }
    } catch (error) {
        console.error('借阅失败:', error);
        ElMessage.error(error.msg || '借阅失败');
    }
};
</script>

<style scoped>
.book-browsing-container {
    padding: 20px;
}

.image-slot {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 100%;
    height: 100%;
    background: #f5f7fa;
    color: #909399;
}
</style>

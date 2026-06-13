<template>
    <div class="my-fines-container">
        <!-- 搜索区域 -->
        <el-card class="box-card">
            <el-form :inline="true" :model="searchForm" class="demo-form-inline">
                <el-form-item>
                    <el-checkbox v-model="showUnpaidOnly" label="显示未支付" border @change="fetchData"></el-checkbox>
                </el-form-item>
            </el-form>
        </el-card>

        <!-- 表格区域 -->
        <el-card class="box-card" style="margin-top: 20px;">
            <el-table :data="tableData" style="width: 100%" border stripe>
                <el-table-column prop="jieyuebianhao" label="借阅编号" width="150"></el-table-column>
                <el-table-column prop="tushumingcheng" label="图书名称" min-width="150"></el-table-column>
                <el-table-column prop="fakuanjine" label="罚款金额" width="100">
                    <template #default="scope">
                        <span style="color: red;">￥{{ scope.row.fakuanjine }}</span>
                    </template>
                </el-table-column>
                <el-table-column prop="chaoqishizhang" label="超期时长(天)" width="120"></el-table-column>
                <el-table-column prop="ispay" label="支付状态" width="100">
                    <template #default="scope">
                        <el-tag :type="scope.row.ispay === '已支付' ? 'success' : 'danger'">
                            {{ scope.row.ispay || '未支付' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="addtime" label="产生时间" width="180"></el-table-column>
                <el-table-column label="操作" width="120" fixed="right">
                    <template #default="scope">
                        <el-button v-if="scope.row.ispay !== '已支付'" type="primary" size="small"
                            @click="handlePay(scope.row)">支付</el-button>
                    </template>
                </el-table-column>
            </el-table>

            <!-- 分页组件 -->
            <el-pagination style="margin-top: 20px;" :current-page="pagination.page" :page-sizes="[10, 20, 50, 100]"
                :page-size="pagination.limit" layout="total, sizes, prev, pager, next, jumper" :total="pagination.total"
                @size-change="handleSizeChange" @current-change="handleCurrentChange"></el-pagination>
        </el-card>

        <!-- 支付确认弹窗 -->
        <el-dialog v-model="payDialogVisible" title="支付罚款" width="30%">
            <span>确认支付该笔罚款吗？金额：<span style="color: red; font-weight: bold;">￥{{ currentRow?.fakuanjine }}</span></span>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="payDialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="submitPay">确认支付</el-button>
                </span>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { getMyFines } from '@/api/borrow';
import { updatePenalty } from '@/api/penalty';
import { ElMessage } from 'element-plus';

// 搜索表单数据
const showUnpaidOnly = ref(false);
const searchForm = reactive({});

// 表格数据
const tableData = ref([]);

// 分页数据
const pagination = reactive({
    page: 1,
    limit: 10,
    total: 0,
});

// 支付弹窗相关
const payDialogVisible = ref(false);
const currentRow = ref(null);

// 获取数据方法
const fetchData = async () => {
    const params = {
        page: pagination.page,
        limit: pagination.limit,
        sort: 'id',
        order: 'desc',
        ...searchForm,
    };
    if (showUnpaidOnly.value) {
        params.ispay = '未支付';
    }
    // 移除空参数
    Object.keys(params).forEach(key => {
        if (params[key] === '' || params[key] === null) {
            delete params[key];
        }
    });

    try {
        const res = await getMyFines(params);
        tableData.value = res.data.list;
        pagination.total = parseInt(res.data.total);
    } catch (error) {
        console.error("Failed to fetch fines data:", error);
    }
};

onMounted(() => {
    fetchData();
});

const handleSizeChange = (val) => {
    pagination.limit = val;
    fetchData();
};

const handleCurrentChange = (val) => {
    pagination.page = val;
    fetchData();
};

// 点击支付按钮
const handlePay = (row) => {
    currentRow.value = row;
    payDialogVisible.value = true;
};

// 提交支付
const submitPay = async () => {
    if (!currentRow.value) return;

    try {
        const payData = {
            ...currentRow.value,
            ispay: '已支付'
        };
        await updatePenalty(payData);
        ElMessage.success('支付成功');
        payDialogVisible.value = false;
        fetchData();
    } catch (error) {
        console.error('支付失败:', error);
        ElMessage.error('支付失败');
    }
};
</script>

<style scoped>
.my-fines-container {
    padding: 20px;
}
</style>

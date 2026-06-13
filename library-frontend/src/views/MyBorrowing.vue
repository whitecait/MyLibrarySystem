<template>
    <div class="my-borrowing-container">
        <!-- 搜索区域 -->
        <el-card class="box-card">
            <el-form :inline="true" class="demo-form-inline">
                <el-form-item>
                    <el-checkbox v-model="showActiveOnly" label="显示待处理" border @change="fetchData"></el-checkbox>
                </el-form-item>
            </el-form>
        </el-card>

        <!-- 表格区域 -->
        <el-card class="box-card" style="margin-top: 20px;">
            <el-table :data="tableData" style="width: 100%" border stripe>
                <el-table-column prop="jieyuebianhao" label="借阅编号" width="150"></el-table-column>
                <el-table-column prop="tushumingcheng" label="图书名称" min-width="150"></el-table-column>
                <el-table-column prop="tushuleixing" label="图书类型" width="120"></el-table-column>
                <el-table-column prop="zuozhe" label="作者" width="120"></el-table-column>
                <el-table-column prop="jieshuriqi" label="借书日期" width="120"></el-table-column>
                <el-table-column prop="yuhairiqi" label="应还日期" width="120"></el-table-column>
                <el-table-column prop="shuliang" label="数量" width="80"></el-table-column>
                <el-table-column label="状态" width="100">
                    <template #default="scope">
                        <el-tag
                            :type="['是', '已归还'].includes(scope.row.sfsh) ? 'success' : (scope.row.sfsh === '否' ? 'danger' : 'warning')">
                            {{ scope.row.sfsh === '是' ? '已通过' : (scope.row.sfsh === '否' ? '未通过' : (scope.row.sfsh ===
                                '已归还' ? '已归还' : '待审核')) }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="180" fixed="right">
                    <template #default="scope">
                        <el-button v-if="scope.row.sfsh === '是'" type="success" size="small"
                            :disabled="pendingMap[scope.row.jieyuebianhao]" @click="handleReturn(scope.row)">
                            {{ pendingMap[scope.row.jieyuebianhao] ? '审核中' : '归还' }}
                        </el-button>
                        <el-button v-if="scope.row.sfsh === '是'" type="primary" size="small"
                            :disabled="pendingMap[scope.row.jieyuebianhao]" @click="handleRenew(scope.row)">
                            {{ pendingMap[scope.row.jieyuebianhao] ? '审核中' : '续借' }}
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>

            <!-- 分页组件 -->
            <el-pagination style="margin-top: 20px;" :current-page="pagination.page" :page-sizes="[10, 20, 50, 100]"
                :page-size="pagination.limit" layout="total, sizes, prev, pager, next, jumper" :total="pagination.total"
                @size-change="handleSizeChange" @current-change="handleCurrentChange"></el-pagination>
        </el-card>

        <!-- 归还弹窗 -->
        <el-dialog v-model="returnDialogVisible" title="归还图书" width="50%">
            <el-form :model="returnForm" label-width="100px">
                <el-form-item label="借阅编号">
                    <el-input v-model="returnForm.jieyuebianhao" disabled></el-input>
                </el-form-item>
                <el-form-item label="图书名称">
                    <el-input v-model="returnForm.tushumingcheng" disabled></el-input>
                </el-form-item>
                <el-form-item label="图书类型">
                    <el-input v-model="returnForm.tushuleixing" disabled></el-input>
                </el-form-item>
                <el-form-item label="数量">
                    <el-input v-model="returnForm.shuliang" disabled></el-input>
                </el-form-item>
                <el-form-item label="归还日期">
                    <el-date-picker v-model="returnForm.guihairiqi" type="datetime" placeholder="选择日期"
                        value-format="YYYY-MM-DD HH:mm:ss" disabled></el-date-picker>
                </el-form-item>
                <el-form-item label="借阅账号">
                    <el-input v-model="returnForm.xueshengxuehao" disabled></el-input>
                </el-form-item>
                <el-form-item label="借阅姓名">
                    <el-input v-model="returnForm.xingming" disabled></el-input>
                </el-form-item>
            </el-form>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="returnDialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="submitReturn">确认归还</el-button>
                </span>
            </template>
        </el-dialog>

        <!-- 续借弹窗 -->
        <el-dialog v-model="renewDialogVisible" title="续借图书" width="50%">
            <el-form :model="renewForm" label-width="100px">
                <el-form-item label="借阅编号">
                    <el-input v-model="renewForm.jieyuebianhao" disabled></el-input>
                </el-form-item>
                <el-form-item label="图书名称">
                    <el-input v-model="renewForm.tushumingcheng" disabled></el-input>
                </el-form-item>
                <el-form-item label="借阅时间">
                    <el-input v-model="renewForm.jieshuriqi" disabled></el-input>
                </el-form-item>
                <el-form-item label="原应还日期">
                    <el-input v-model="renewForm.yuhairiqi" disabled></el-input>
                </el-form-item>
                <el-form-item label="续借日期">
                    <el-date-picker v-model="renewForm.xujieriqi" type="date" placeholder="选择日期"
                        value-format="YYYY-MM-DD" disabled></el-date-picker>
                </el-form-item>
                <el-form-item label="新应还日期">
                    <el-date-picker v-model="renewForm.xuhairiqi" type="date" placeholder="选择日期"
                        value-format="YYYY-MM-DD" disabled></el-date-picker>
                </el-form-item>
                <el-form-item label="续借天数">
                    <el-input v-model="renewDays" disabled>
                        <template #append>天</template>
                    </el-input>
                </el-form-item>
            </el-form>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="renewDialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="submitRenew">确认续借</el-button>
                </span>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue';
import { getMyBorrowings } from '@/api/borrow';
import { addReturn, getReturnList } from '@/api/return';
import { addRenewal, getRenewalList } from '@/api/renewal';
import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';

const showActiveOnly = ref(false);

// 搜索表单数据
const searchForm = reactive({});

// 待审核状态映射
const pendingMap = reactive({});

// 表格数据
const tableData = ref([]);

// 分页数据
const pagination = reactive({
    page: 1,
    limit: 10,
    total: 0,
});

// 归还弹窗相关
const returnDialogVisible = ref(false);
const returnForm = reactive({
    jieyuebianhao: '',
    tushubianhao: '',
    tushumingcheng: '',
    tushuleixing: '',
    zuozhe: '',
    chubanshe: '',
    jieyuekahao: '',
    yijieshuliang: 0,
    xianjieshuliang: 0,
    jieshuxiane: 0,
    yuhairiqi: '',
    xueshengxuehao: '',
    xingming: '',
    shouji: '',
    jieshuriqi: '',
    touxiang: '',
    shuliang: 1,
    haishuriqi: '',
    sfsh: '待审核',
    shhf: ''
});

// 续借弹窗相关
const renewDialogVisible = ref(false);
const renewForm = reactive({
    jieyuebianhao: '',
    tushubianhao: '',
    tushumingcheng: '',
    tushuleixing: '',
    shuliang: 1,
    zuozhe: '',
    chubanshe: '',
    jieyuekahao: '',
    yijieshuliang: 0,
    xianjieshuliang: 0,
    jieshuxiane: 0,
    yuhairiqi: '',
    xueshengxuehao: '',
    xingming: '',
    shouji: '',
    jieshuriqi: '',
    touxiang: '',
    xujieriqi: '',
    xuhairiqi: '',
    sfsh: '待审核',
    shhf: ''
});

const renewDays = computed(() => {
    if (!renewForm.yuhairiqi || !renewForm.xuhairiqi) return 0;
    const start = dayjs(renewForm.yuhairiqi);
    const end = dayjs(renewForm.xuhairiqi);
    return end.diff(start, 'day');
});

// 获取待审核状态
const fetchPendingStatus = async () => {
    try {
        // 清空现有状态
        for (const key in pendingMap) {
            delete pendingMap[key];
        }

        // 获取待审核的归还申请
        const returnRes = await getReturnList({
            page: 1,
            limit: 1000,
            sfsh: '待审核'
        });
        if (returnRes.data && returnRes.data.list) {
            returnRes.data.list.forEach(item => {
                pendingMap[item.jieyuebianhao] = true;
            });
        }

        // 获取待审核的续借申请
        const renewalRes = await getRenewalList({
            page: 1,
            limit: 1000,
            sfsh: '待审核'
        });
        if (renewalRes.data && renewalRes.data.list) {
            renewalRes.data.list.forEach(item => {
                pendingMap[item.jieyuebianhao] = true;
            });
        }
    } catch (error) {
        console.error('获取待审核状态失败', error);
    }
};

// 获取数据方法
const fetchData = async () => {
    const params = {
        page: pagination.page,
        limit: pagination.limit,
        sort: 'id',
        order: 'desc',
        ...searchForm,
    };
    // 移除空参数
    Object.keys(params).forEach(key => {
        if (params[key] === '' || params[key] === null) {
            delete params[key];
        }
    });

    try {
        const res = await getMyBorrowings(params);
        let list = res.data.list;
        if (showActiveOnly.value) {
            list = list.filter(item => item.sfsh !== '已归还');
        }
        tableData.value = list;
        pagination.total = parseInt(res.data.total);
        // 获取完借阅列表后，获取待审核状态
        fetchPendingStatus();
    } catch (error) {
        console.error("Failed to fetch borrowing data:", error);
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

// 点击归还按钮
const handleReturn = (row) => {
    const user = JSON.parse(localStorage.getItem('user') || '{}');

    returnForm.jieyuebianhao = row.jieyuebianhao;
    returnForm.tushubianhao = row.tushubianhao;
    returnForm.tushumingcheng = row.tushumingcheng;
    returnForm.tushuleixing = row.tushuleixing;
    returnForm.zuozhe = row.zuozhe;
    returnForm.chubanshe = row.chubanshe;
    returnForm.jieyuekahao = user.jieyuekahao || user.xueshengxuehao;
    returnForm.shuliang = row.shuliang;
    returnForm.yuhairiqi = row.yuhairiqi;
    returnForm.xueshengxuehao = user.xueshengxuehao;
    returnForm.xingming = user.xingming;
    returnForm.shouji = user.shouji;
    returnForm.jieshuriqi = row.jieshuriqi;
    returnForm.haishuriqi = dayjs().format('YYYY-MM-DD HH:mm:ss');
    returnForm.sfsh = '待审核';

    returnDialogVisible.value = true;
};

// 提交归还
const submitReturn = async () => {
    try {
        await addReturn(returnForm);
        ElMessage.success('归还申请提交成功');
        returnDialogVisible.value = false;
        fetchData();
    } catch (error) {
        console.error('归还失败:', error);
    }
};

// 点击续借按钮
const handleRenew = (row) => {
    const user = JSON.parse(localStorage.getItem('user') || '{}');

    renewForm.jieyuebianhao = row.jieyuebianhao;
    renewForm.tushubianhao = row.tushubianhao;
    renewForm.tushumingcheng = row.tushumingcheng;
    renewForm.tushuleixing = row.tushuleixing;
    renewForm.shuliang = row.shuliang;
    renewForm.zuozhe = row.zuozhe;
    renewForm.chubanshe = row.chubanshe;
    renewForm.jieyuekahao = user.jieyuekahao || user.xueshengxuehao;
    renewForm.yuhairiqi = row.yuhairiqi;
    renewForm.xueshengxuehao = user.xueshengxuehao;
    renewForm.xingming = user.xingming;
    renewForm.shouji = user.shouji;
    renewForm.jieshuriqi = row.jieshuriqi;

    renewForm.xujieriqi = dayjs().format('YYYY-MM-DD');
    // 默认续借30天，基于原应还日期
    renewForm.xuhairiqi = dayjs(row.yuhairiqi).add(30, 'day').format('YYYY-MM-DD');
    renewForm.sfsh = '待审核';

    renewDialogVisible.value = true;
};

// 提交续借
const submitRenew = async () => {
    try {
        await addRenewal(renewForm);
        ElMessage.success('续借申请提交成功');
        renewDialogVisible.value = false;
        fetchData();
    } catch (error) {
        console.error('续借失败:', error);
    }
};
</script>

<style scoped>
.my-borrowing-container {
    padding: 20px;
}
</style>

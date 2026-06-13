<template>
    <div class="borrow-approval-container">
        <el-card class="box-card">
            <template #header>
                <div class="card-header">
                    <span>借阅审核</span>
                    <div class="header-actions">
                        <el-checkbox v-model="onlyPending" @change="fetchData"
                            style="margin-right: 15px;">仅显示待审核</el-checkbox>
                        <el-button type="primary" @click="fetchData">刷新列表</el-button>
                    </div>
                </div>
            </template>

            <el-table :data="tableData" style="width: 100%" v-loading="loading">
                <el-table-column prop="tushumingcheng" label="图书名称" />
                <el-table-column prop="xueshengxuehao" label="学号" />
                <el-table-column prop="xingming" label="姓名" />
                <el-table-column prop="jieshuriqi" label="借阅时间" />
                <el-table-column prop="yuhairiqi" label="应还时间" />
                <el-table-column prop="shhf" label="审核回复" />
                <el-table-column label="状态/操作" width="200">
                    <template #default="scope">
                        <span v-if="scope.row.sfsh === '待审核'">
                            <el-button size="small" type="success" @click="handleApprove(scope.row, '是')">通过</el-button>
                            <el-button size="small" type="danger" @click="handleApprove(scope.row, '否')">拒绝</el-button>
                        </span>
                        <span v-else>
                            <el-tag :type="['是', '已归还'].includes(scope.row.sfsh) ? 'success' : 'danger'">
                                {{ scope.row.sfsh === '是' ? '已通过' : (scope.row.sfsh === '否' ? '已拒绝' : scope.row.sfsh) }}
                            </el-tag>
                        </span>
                    </template>
                </el-table-column>
            </el-table>

            <div class="pagination-container">
                <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize"
                    :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper" :total="total"
                    @size-change="handleSizeChange" @current-change="handleCurrentChange" />
            </div>
        </el-card>

        <!-- 审核弹窗 -->
        <el-dialog v-model="dialogVisible" title="审核处理" width="30%">
            <el-form :model="auditForm" label-width="80px">
                <el-form-item label="审核回复">
                    <el-input v-model="auditForm.shhf" type="textarea" rows="3" placeholder="请输入审核回复"></el-input>
                </el-form-item>
            </el-form>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="dialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="submitAudit">确定</el-button>
                </span>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyBorrowings, approveBorrow } from '@/api/borrow'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const onlyPending = ref(false)

const dialogVisible = ref(false)
const auditForm = ref({
    ids: [],
    sfsh: '',
    shhf: ''
})

const fetchData = async () => {
    loading.value = true
    try {
        const params = {
            page: currentPage.value,
            limit: pageSize.value,
            sort: 'addtime',
            order: 'desc'
        }
        if (onlyPending.value) {
            params.sfsh = '待审核'
        }
        const res = await getMyBorrowings(params)
        if (res.code === 0) {
            tableData.value = res.data.list
            total.value = res.data.total
        } else {
            ElMessage.error(res.msg || '获取列表失败')
        }
    } catch (error) {
        console.error(error)
        ElMessage.error('获取列表失败')
    } finally {
        loading.value = false
    }
}

const handleApprove = (row, status) => {
    auditForm.value.ids = [row.id]
    if (status) {
        auditForm.value.sfsh = status
    } else {
        auditForm.value.sfsh = ''
    }
    auditForm.value.shhf = ''
    dialogVisible.value = true
}

const submitAudit = async () => {
    if (!auditForm.value.sfsh) {
        ElMessage.warning('请选择审核结果')
        return
    }
    try {
        const params = {
            ids: auditForm.value.ids,
            sfsh: auditForm.value.sfsh,
            shhf: auditForm.value.shhf
        }
        const res = await approveBorrow(params)
        if (res.code === 0) {
            ElMessage.success('审核成功')
            dialogVisible.value = false
            fetchData()
        } else {
            ElMessage.error(res.msg || '审核失败')
        }
    } catch (error) {
        console.error(error)
        ElMessage.error('审核失败')
    }
}

const handleSizeChange = (val) => {
    pageSize.value = val
    fetchData()
}

const handleCurrentChange = (val) => {
    currentPage.value = val
    fetchData()
}

onMounted(() => {
    fetchData()
})
</script>

<style scoped>
.borrow-approval-container {
    padding: 20px;
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.pagination-container {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
}
</style>

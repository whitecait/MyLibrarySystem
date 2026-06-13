<template>
    <div class="penalty-management-container">
        <el-card class="box-card">
            <template #header>
                <div class="card-header">
                    <span>罚款管理</span>
                    <el-button type="primary" @click="handleAdd">新增罚款</el-button>
                </div>
            </template>

            <el-table :data="tableData" style="width: 100%" v-loading="loading">
                <el-table-column prop="xueshengxuehao" label="学号" />
                <el-table-column prop="xingming" label="姓名" />
                <el-table-column prop="tushumingcheng" label="图书名称" />
                <el-table-column prop="fakuanjine" label="罚款金额" />
                <el-table-column prop="addtime" label="添加时间" />
                <el-table-column label="操作" width="200">
                    <template #default="scope">
                        <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
                        <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>

            <div class="pagination-container">
                <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize"
                    :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper" :total="total"
                    @size-change="handleSizeChange" @current-change="handleCurrentChange" />
            </div>
        </el-card>

        <!-- 罚款表单弹窗 -->
        <el-dialog v-model="dialogVisible" :title="dialogTitle" width="50%">
            <el-form :model="form" label-width="100px">
                <el-form-item label="读者账号">
                    <el-input v-model="form.duzhezhanghao" placeholder="请输入读者账号"></el-input>
                </el-form-item>
                <el-form-item label="读者姓名">
                    <el-input v-model="form.duzhexingming" placeholder="请输入读者姓名"></el-input>
                </el-form-item>
                <el-form-item label="图书名称">
                    <el-input v-model="form.tushumingcheng" placeholder="请输入图书名称"></el-input>
                </el-form-item>
                <el-form-item label="罚款金额">
                    <el-input-number v-model="form.fakuanjine" :min="0" :precision="2"></el-input-number>
                </el-form-item>
                <el-form-item label="罚款原因">
                    <el-input v-model="form.fakuanyuanyin" type="textarea" rows="3"></el-input>
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
import { ref, onMounted } from 'vue'
import { getPenaltyList, addPenalty, updatePenalty, deletePenalty } from '@/api/penalty'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const dialogTitle = ref('新增罚款')
const form = ref({
    duzhezhanghao: '',
    duzhexingming: '',
    tushumingcheng: '',
    fakuanjine: 0,
    fakuanyuanyin: ''
})

const fetchData = async () => {
    loading.value = true
    try {
        const res = await getPenaltyList({
            page: currentPage.value,
            limit: pageSize.value
        })
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

const handleAdd = () => {
    dialogTitle.value = '新增罚款'
    form.value = {
        duzhezhanghao: '',
        duzhexingming: '',
        tushumingcheng: '',
        fakuanjine: 0,
        fakuanyuanyin: ''
    }
    dialogVisible.value = true
}

const handleEdit = (row) => {
    dialogTitle.value = '编辑罚款'
    form.value = { ...row }
    dialogVisible.value = true
}

const handleDelete = (row) => {
    ElMessageBox.confirm('确认删除该罚款记录吗?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            const res = await deletePenalty([row.id])
            if (res.code === 0) {
                ElMessage.success('删除成功')
                fetchData()
            } else {
                ElMessage.error(res.msg || '删除失败')
            }
        } catch (error) {
            console.error(error)
            ElMessage.error('删除失败')
        }
    })
}

const submitForm = async () => {
    try {
        let res
        if (form.value.id) {
            res = await updatePenalty(form.value)
        } else {
            res = await addPenalty(form.value)
        }
        if (res.code === 0) {
            ElMessage.success(form.value.id ? '修改成功' : '新增成功')
            dialogVisible.value = false
            fetchData()
        } else {
            ElMessage.error(res.msg || '操作失败')
        }
    } catch (error) {
        console.error(error)
        ElMessage.error('操作失败')
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
.penalty-management-container {
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

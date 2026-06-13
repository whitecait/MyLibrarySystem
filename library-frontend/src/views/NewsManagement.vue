<template>
    <div class="news-management-container">
        <el-card class="box-card">
            <template #header>
                <div class="card-header">
                    <span>新闻资讯管理</span>
                    <el-button type="primary" @click="handleAdd">新增新闻</el-button>
                </div>
            </template>

            <el-table :data="tableData" style="width: 100%" v-loading="loading">
                <el-table-column prop="title" label="标题" />
                <el-table-column prop="introduction" label="简介" show-overflow-tooltip />
                <el-table-column prop="picture" label="图片">
                    <template #default="scope">
                        <el-image style="width: 100px; height: 60px" :src="scope.row.picture" fit="cover"
                            v-if="scope.row.picture" />
                    </template>
                </el-table-column>
                <el-table-column prop="addtime" label="发布时间" />
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

        <!-- 新闻表单弹窗 -->
        <el-dialog v-model="dialogVisible" :title="dialogTitle" width="60%">
            <el-form :model="form" label-width="80px">
                <el-form-item label="标题">
                    <el-input v-model="form.title" placeholder="请输入标题"></el-input>
                </el-form-item>
                <el-form-item label="简介">
                    <el-input v-model="form.introduction" type="textarea" rows="2" placeholder="请输入简介"></el-input>
                </el-form-item>
                <el-form-item label="图片">
                    <!-- 这里简化处理，实际应为上传组件 -->
                    <el-input v-model="form.picture" placeholder="请输入图片URL"></el-input>
                </el-form-item>
                <el-form-item label="内容">
                    <el-input v-model="form.content" type="textarea" rows="10" placeholder="请输入内容"></el-input>
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
import { getNewsList, addNews, updateNews, deleteNews } from '@/api/news'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const dialogTitle = ref('新增新闻')
const form = ref({
    title: '',
    introduction: '',
    picture: '',
    content: ''
})

const fetchData = async () => {
    loading.value = true
    try {
        const res = await getNewsList({
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
    dialogTitle.value = '新增新闻'
    form.value = {
        title: '',
        introduction: '',
        picture: '',
        content: ''
    }
    dialogVisible.value = true
}

const handleEdit = (row) => {
    dialogTitle.value = '编辑新闻'
    form.value = { ...row }
    dialogVisible.value = true
}

const handleDelete = (row) => {
    ElMessageBox.confirm('确认删除该新闻吗?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            const res = await deleteNews([row.id])
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
            res = await updateNews(form.value)
        } else {
            res = await addNews(form.value)
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
.news-management-container {
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

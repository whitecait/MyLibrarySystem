<template>
    <el-container class="main-layout">
        <el-aside width="200px">
            <el-menu active-text-color="#ffd04b" background-color="#545c64" class="el-menu-vertical-demo"
                :default-active="route.path" text-color="#fff" router>
                <el-menu-item index="/">
                    <span>首页</span>
                </el-menu-item>

                <!-- 管理员菜单 -->
                <el-sub-menu index="/management" v-if="role === 'admin'">
                    <template #title>
                        <span>信息管理</span>
                    </template>
                    <el-menu-item index="/book-management">
                        <el-icon><icon-menu /></el-icon>
                        <span>图书管理</span>
                    </el-menu-item>
                    <el-menu-item index="/category-management">
                        <el-icon>
                            <Collection />
                        </el-icon>
                        <span>分类管理</span>
                    </el-menu-item>
                    <el-menu-item index="/reader-management">
                        <el-icon>
                            <User />
                        </el-icon>
                        <span>读者管理</span>
                    </el-menu-item>
                </el-sub-menu>

                <el-sub-menu index="/transaction" v-if="role === 'admin'">
                    <template #title>
                        <span>事务处理</span>
                    </template>
                    <el-menu-item index="/borrow-approval">
                        <el-icon>
                            <DocumentChecked />
                        </el-icon>
                        <span>借阅审核</span>
                    </el-menu-item>
                    <el-menu-item index="/renewal-approval">
                        <el-icon>
                            <Timer />
                        </el-icon>
                        <span>续借审核</span>
                    </el-menu-item>
                    <el-menu-item index="/return-approval">
                        <el-icon>
                            <CircleCheck />
                        </el-icon>
                        <span>归还审核</span>
                    </el-menu-item>
                    <el-menu-item index="/penalty-management">
                        <el-icon>
                            <Warning />
                        </el-icon>
                        <span>罚款管理</span>
                    </el-menu-item>
                    <el-menu-item index="/news-management">
                        <el-icon>
                            <Notification />
                        </el-icon>
                        <span>新闻管理</span>
                    </el-menu-item>
                </el-sub-menu>

                <!-- 读者菜单 -->
                <el-sub-menu index="/reader-service" v-if="role === 'reader'">
                    <template #title>
                        <span>读者服务</span>
                    </template>
                    <el-menu-item index="/book-browsing">
                        <el-icon>
                            <Search />
                        </el-icon>
                        <span>图书浏览</span>
                    </el-menu-item>
                    <el-menu-item index="/my-borrowing">
                        <el-icon>
                            <Reading />
                        </el-icon>
                        <span>我的借阅</span>
                    </el-menu-item>
                    <el-menu-item index="/my-fines">
                        <el-icon>
                            <Money />
                        </el-icon>
                        <span>我的罚款</span>
                    </el-menu-item>
                    <el-menu-item index="/reader-profile">
                        <el-icon>
                            <Avatar />
                        </el-icon>
                        <span>个人信息</span>
                    </el-menu-item>
                </el-sub-menu>

            </el-menu>
        </el-aside>
        <el-container>
            <el-header class="main-header">
                <div>图书管理系统</div>
                <div>
                    <span>欢迎您，{{ role === 'admin' ? '管理员' : '读者' }}</span>
                    <el-button link type="primary" @click="logout">退出登录</el-button>
                </div>
            </el-header>
            <el-main>
                <!-- 子路由的视图将在这里显示 -->
                <router-view />
            </el-main>
        </el-container>
    </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import {
    Menu as IconMenu,
    User,
    Collection,
    Search,
    Reading,
    Money,
    DocumentChecked,
    Timer,
    Warning,
    Notification,
    Avatar,
    CircleCheck
} from '@element-plus/icons-vue'

const router = useRouter();
const route = useRoute();
const role = ref('');

onMounted(() => {
    role.value = localStorage.getItem('role') || 'reader'; // 默认为 reader，防止未登录状态下的闪烁（虽然有路由守卫）
});

const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('role');
    router.push('/login');
};
</script>

<style scoped>
.main-layout,
.el-menu-vertical-demo {
    height: 100vh;
}

.main-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background-color: #fff;
    border-bottom: 1px solid #ebeef5;
    font-size: 18px;
    color: #303133;
}
</style>
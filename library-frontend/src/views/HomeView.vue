<template>
  <div class="home-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>欢迎使用图书管理系统</span>
        </div>
      </template>
      <div class="welcome-text">
        <p>您好，{{ username }}！</p>
        <p>今天是 {{ currentDate }}，祝您工作愉快！</p>
      </div>
    </el-card>

    <el-row :gutter="20" class="stats-row">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background-color: #409EFF;">
              <el-icon>
                <Collection />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-title">总图书数</div>
              <div class="stat-value">{{ bookTotal }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 管理员可见 -->
      <el-col :span="8" v-if="role === 'admin'">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background-color: #67C23A;">
              <el-icon>
                <User />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-title">注册用户</div>
              <div class="stat-value">{{ readerTotal }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8" v-if="role === 'admin'">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background-color: #E6A23C;">
              <el-icon>
                <DocumentChecked />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-title">今日借阅</div>
              <div class="stat-value">{{ todayBorrowCount }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 读者可见 -->
      <el-col :span="8" v-if="role === 'reader'">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background-color: #67C23A;">
              <el-icon>
                <Reading />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-title">我的借阅</div>
              <div class="stat-value">{{ myBorrowCount }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8" v-if="role === 'reader'">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background-color: #F56C6C;">
              <el-icon>
                <Money />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-title">我的罚款</div>
              <div class="stat-value">{{ myFineCount }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 公告栏 -->
    <el-card class="box-card" style="margin-top: 20px;">
      <template #header>
        <div class="card-header" style="display: flex; justify-content: space-between; align-items: center;">
          <span>公告栏</span>
          <el-button link type="primary" @click="toggleNewsList">{{ showNewsList ? '收起列表' : '更多公告' }}</el-button>
        </div>
      </template>

      <!-- 当前公告详情 -->
      <div v-if="currentNews" class="news-detail">
        <h3 style="text-align: center;">{{ currentNews.title }}</h3>
        <div class="news-meta" style="text-align: center; color: #909399; font-size: 12px; margin-bottom: 15px;">
          发布时间：{{ currentNews.addtime }}
        </div>
        <div class="news-content" v-html="currentNews.content" style="line-height: 1.6;"></div>
        <div class="news-image" v-if="currentNews.picture" style="margin-top: 15px; text-align: center;">
          <img :src="getImgUrl(currentNews.picture)" style="max-width: 100%; max-height: 300px;" />
        </div>
      </div>
      <div v-else class="no-news" style="text-align: center; color: #909399;">暂无公告</div>

      <!-- 公告列表 (可展开) -->
      <div v-show="showNewsList" class="news-list"
        style="margin-top: 20px; border-top: 1px solid #ebeef5; padding-top: 10px;">
        <el-table :data="newsList" @row-click="handleNewsClick" :show-header="false" style="cursor: pointer;">
          <el-table-column prop="title" label="标题">
            <template #default="scope">
              <span style="font-weight: bold;">{{ scope.row.title }}</span>
              <span style="margin-left: 10px; color: #909399; font-size: 12px;">{{ scope.row.introduction }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="addtime" label="时间" width="180" align="right"></el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { Collection, User, DocumentChecked, Reading, Money } from '@element-plus/icons-vue';
import {
  getAdminSession,
  getReaderSession,
  getBookTotal,
  getReaderTotal,
  getBorrowPage,
  getFinePage,
  getNewsList
} from '@/api/home';
import { ElMessage } from 'element-plus';
import { getImgUrl } from '@/utils/index';

// 响应式数据
const role = ref('');
const username = ref('用户');
const bookTotal = ref(0);
const readerTotal = ref(0);
const todayBorrowCount = ref(0);
const myBorrowCount = ref(0);
const myFineCount = ref(0);

// 公告相关
const newsList = ref([]);
const currentNews = ref(null);
const showNewsList = ref(false);

const currentDate = computed(() => {
  const today = new Date();
  const year = today.getFullYear();
  const month = today.getMonth() + 1;
  const day = today.getDate();
  return `${year}年${month}月${day}日`;
});

// 获取首页数据
const fetchHomeData = async () => {
  role.value = localStorage.getItem('role') || 'reader';

  try {
    // 1. 获取用户信息
    let sessionRes;
    if (role.value === 'admin') {
      sessionRes = await getAdminSession();
      username.value = sessionRes.data.username;
    } else {
      sessionRes = await getReaderSession();
      username.value = sessionRes.data.xingming;
    }

    // 2. 获取通用统计：总图书数
    const bookRes = await getBookTotal();
    if (bookRes.data) {
      bookTotal.value = bookRes.data.total;
    }

    // 3. 获取角色特定统计
    if (role.value === 'admin') {
      // 注册用户数
      const readerRes = await getReaderTotal();
      readerTotal.value = readerRes.data.total;

      // 今日借阅数
      const today = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
      const borrowRes = await getBorrowPage({
        page: 1,
        limit: 1000,
      });
      if (borrowRes.data && borrowRes.data.list) {
        todayBorrowCount.value = borrowRes.data.list.filter(item => item.jieshuriqi === today).length;
      }
    } else {
      // 我的借阅数
      const myBorrowRes = await getBorrowPage({ page: 1, limit: 1 });
      myBorrowCount.value = myBorrowRes.data.total;

      // 我的罚款数
      const myFineRes = await getFinePage({ page: 1, limit: 1 });
      myFineCount.value = myFineRes.data.total;
    }

    // 4. 获取公告
    const newsRes = await getNewsList({ page: 1, limit: 10 });
    if (newsRes.data && newsRes.data.list) {
      newsList.value = newsRes.data.list;
      if (newsList.value.length > 0) {
        currentNews.value = newsList.value[0];
      }
    }

  } catch (error) {
    console.error("Failed to fetch home data:", error);
  }
};

const toggleNewsList = () => {
  showNewsList.value = !showNewsList.value;
};

const handleNewsClick = (row) => {
  currentNews.value = row;
};

onMounted(() => {
  fetchHomeData();
});
</script>

<style scoped>
.home-container {
  padding: 20px;
}

.card-header {
  font-size: 18px;
  font-weight: bold;
}

.welcome-text {
  font-size: 16px;
  line-height: 1.8;
}

.stats-row {
  margin-top: 20px;
}

.stat-item {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 30px;
  margin-right: 20px;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 5px;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}
</style>
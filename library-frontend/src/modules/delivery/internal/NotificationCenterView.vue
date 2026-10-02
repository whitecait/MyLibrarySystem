<template>
  <section class="notification-center-view">
    <h1>通知中心</h1>
    <form class="filters" data-testid="notification-filter" @submit.prevent="load()">
      <label class="check">
        <input v-model="unreadOnly" type="checkbox" data-testid="notification-unread" />
        仅看未读
      </label>
      <el-button native-type="submit" data-testid="notification-submit">查询</el-button>
    </form>

    <p v-if="state.status === 'loading'" data-testid="notification-loading">加载中…</p>
    <el-empty
      v-else-if="state.status === 'success' && notifications.length === 0"
      data-testid="notification-empty"
      description="暂无通知"
    />
    <template v-else-if="state.status === 'success'">
      <table class="notification-table" data-testid="notification-table">
        <thead>
          <tr><th>类型</th><th>内容</th><th>关联事务</th><th>预约日期</th><th>时间</th><th>状态</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="notification in notifications" :key="notification.notificationId">
            <td>{{ notification.type }}</td>
            <td>{{ notification.message }}</td>
            <td>
              <RouterLink
                v-if="isReader && notificationTransactionId(notification)"
                :to="notificationDetailRoute(notification)"
                :data-testid="`notification-detail-${notification.notificationId}`"
              >
                {{ notification.relatedTxId }}
              </RouterLink>
              <span v-else>{{ notification.relatedTxId ?? '—' }}</span>
            </td>
            <td>{{ notification.reservationDate ?? '—' }}</td>
            <td>{{ notification.createdAt }}</td>
            <td>{{ notification.read ? '已读' : '未读' }}</td>
            <td>
              <el-button
                v-if="!notification.read"
                size="small"
                :data-testid="`notification-read-${notification.notificationId}`"
                @click="markRead(notification)"
              >
                标记已读
              </el-button>
            </td>
          </tr>
        </tbody>
      </table>
      <el-button v-if="nextCursor" data-testid="notification-load-more" @click="load(nextCursor)">加载更多</el-button>
    </template>
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getRole } from '@/platform/auth/session'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import {
  getNotificationSessionKey,
  listNotifications,
  markNotificationRead,
  notificationTransactionId,
} from '../index'

const notifications = ref([])
const nextCursor = ref(null)
const unreadOnly = ref(false)
const isReader = computed(() => getRole() === 'READER')
const { state, run } = useApiRequest()
const { run: runRead } = useApiRequest()

async function load(cursor) {
  const sessionKey = getNotificationSessionKey()
  const envelope = await run(() =>
    listNotifications({ unreadOnly: unreadOnly.value || undefined, cursor, limit: 20 }),
  )
  if (!envelope || sessionKey !== getNotificationSessionKey()) return
  notifications.value = cursor ? [...notifications.value, ...envelope.data.items] : envelope.data.items
  nextCursor.value = envelope.data.nextCursor
}

async function markRead(notification) {
  const sessionKey = getNotificationSessionKey()
  const envelope = await runRead(() => markNotificationRead({ notificationId: notification.notificationId }))
  if (sessionKey !== getNotificationSessionKey()) return
  if (envelope?.data.read) {
    notification.read = true
  }
}

function notificationDetailRoute(notification) {
  const txId = notificationTransactionId(notification)
  return txId ? { name: 'my-business-detail', params: { txId } } : null
}

load()
</script>

<style scoped>
.notification-center-view {
  max-width: 60rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.filters {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.check {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.notification-table {
  width: 100%;
  border-collapse: collapse;
}

.notification-table th,
.notification-table td {
  text-align: left;
  padding: 0.4rem 0.6rem;
  border-bottom: 1px solid #e2eaf0;
}
</style>

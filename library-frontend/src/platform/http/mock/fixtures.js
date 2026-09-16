// 按操作登记的 mock 夹具：数据形状必须符合 OpenAPI 生成的 *Data schema（经 JSDoc 类型标注约束），
// 只提供罐头回放数据，不包含任何业务判定。随批次增量扩充。
// @type {Record<string, {status?: number, code: string, message?: string, data: object}>}
export const fixtures = {
  searchTitles: {
    code: 'MLS-CAT-S001',
    message: 'ok',
    data: {
      items: [{ titleId: '2001', title: 'Database Systems', authors: ['C. Date'], category: '数据库', availability: 'AVAILABLE' }],
      nextCursor: null,
    },
  },
  createSession: {
    code: 'MLS-ID-S002',
    message: 'ok',
    data: {
      accessToken: 'opaque-dev-token',
      expiresAt: '2099-01-01T00:00:00+08:00',
      actor: { accountId: '1002', role: 'LIBRARIAN', displayName: '李馆员' },
    },
  },
  endCurrentSession: { code: 'MLS-ID-S003', message: 'ended', data: { ended: true } },
  listAccounts: {
    code: 'MLS-ID-S004',
    message: 'ok',
    data: {
      items: [
        { accountId: '1001', username: 'reader01', displayName: 'Reader 01', role: 'READER', status: 'ACTIVE', version: 0 },
        { accountId: '1002', username: 'librarian01', displayName: '李馆员', role: 'LIBRARIAN', status: 'ACTIVE', version: 0 },
      ],
      nextCursor: null,
    },
  },
  requestAccountChange: { code: 'MLS-ID-S005', message: 'review requested', data: { reviewId: '6001', status: 'PENDING' } },
  resetPassword: { code: 'MLS-ID-S006', message: 'reset', data: { temporaryPassword: 'tmp-Abc12345', forceChange: true } },
  changeReaderStatus: { code: 'MLS-ID-S007', message: 'changed', data: { readerId: '1001', status: 'SUSPENDED', version: 4 } },
  getTitle: {
    code: 'MLS-CAT-S002',
    message: 'ok',
    data: {
      titleId: '2001',
      title: 'Database Systems',
      authors: ['C. Date'],
      category: '数据库',
      availability: 'AVAILABLE',
      isbn: '978-0-13-1',
      publisher: null,
      publishYear: 2020,
      priceCents: 12800,
      version: 3,
      hasReadingContent: true,
    },
  },
  getReadingContent: {
    code: 'MLS-CAT-S003',
    message: 'ok',
    data: {
      contentType: 'text/plain',
      pages: [{ pageNo: 1, text: '第一章 数据库系统概念。' }],
      nextCursor: null,
    },
  },
  createTitle: {
    code: 'MLS-CAT-S004',
    message: 'created',
    data: {
      titleId: '2002',
      title: 'New Book',
      authors: ['A. Author'],
      category: '计算机',
      availability: 'UNKNOWN',
      isbn: '978-9-999',
      publisher: null,
      publishYear: null,
      priceCents: 5900,
      version: 0,
      hasReadingContent: false,
    },
  },
  updateTitle: {
    code: 'MLS-CAT-S005',
    message: 'updated',
    data: {
      titleId: '2001',
      title: 'Database Systems (2nd)',
      authors: ['C. Date'],
      category: '数据库',
      availability: 'AVAILABLE',
      isbn: '978-0-13-1',
      publisher: null,
      publishYear: 2021,
      priceCents: 13800,
      version: 4,
      hasReadingContent: true,
    },
  },
  removeTitle: { code: 'MLS-CAT-S006', message: 'removed', data: { status: 'REMOVED' } },
  createCopy: {
    code: 'MLS-CIRC-S001',
    message: 'created',
    data: { copyId: '3001', titleId: '2001', copyNo: 'C-001', status: 'PENDING_SHELVING', version: 0 },
  },
  listCopies: {
    code: 'MLS-CIRC-S002',
    message: 'ok',
    data: {
      items: [
        { copyId: '3001', titleId: '2001', copyNo: 'C-001', status: 'AVAILABLE', version: 2 },
        { copyId: '3002', titleId: '2001', copyNo: 'C-002', status: 'BORROWED', version: 5 },
      ],
      nextCursor: null,
    },
  },
  changeCopyState: {
    code: 'MLS-CIRC-S003',
    message: 'changed',
    data: { copyId: '3001', titleId: '2001', copyNo: 'C-001', status: 'MAINTENANCE', version: 3 },
  },
  getReservationCalendar: {
    code: 'MLS-CIRC-S004',
    message: 'ok',
    data: {
      items: [
        { businessDate: '2026-09-16', availability: 'GUARANTEED' },
        { businessDate: '2026-09-17', availability: 'NOT_GUARANTEED' },
        { businessDate: '2026-09-18', availability: 'UNAVAILABLE' },
      ],
    },
  },
  reserveTitle: {
    code: 'MLS-CIRC-S005',
    message: 'confirmed',
    data: { txId: '4002', outcome: 'CONFIRMED' },
  },
  cancelReservation: {
    code: 'MLS-CIRC-S006',
    message: 'canceled',
    data: { txId: '4002', outcome: 'CANCELED' },
  },
  createWalkInLoans: {
    code: 'MLS-CIRC-S002',
    message: 'processed',
    data: {
      items: [
        { itemRef: '3001', txId: '4001', outcome: 'LENT' },
        { itemRef: '3002', txId: null, outcome: 'REJECTED', reasons: ['COPY_NOT_AVAILABLE'] },
      ],
    },
  },
  claimPickup: {
    code: 'MLS-CIRC-S007',
    message: 'claimed',
    data: { txId: '4001', outcome: 'LOAN_CREATED', status: 'ACTIVE' },
  },
  applyManualAction: {
    code: 'MLS-CIRC-S008',
    message: 'applied',
    data: { txId: '4001', outcome: 'MANUAL_RELEASED', status: 'MANUAL_HANDLING' },
  },
  renewLoan: {
    code: 'MLS-CIRC-S009',
    message: 'renewed',
    data: { txId: '4001', dueDate: '2026-09-29', outcome: 'RENEWED' },
  },
  returnCopy: {
    code: 'MLS-CIRC-S003',
    message: 'returned',
    data: { txId: '4001', outcome: 'RETURNED', fineId: '5001' },
  },
  getArrivalDashboard: {
    code: 'MLS-QUERY-S001',
    message: 'ok',
    data: { businessDate: '2026-09-16', expected: 12, arrived: 7, pending: 4, exceptions: 1 },
  },
  getMyBusiness: {
    code: 'MLS-QUERY-S002',
    message: 'ok',
    data: {
      items: [
        { kind: 'LOAN', id: '4001', status: 'ACTIVE', title: 'Database Systems', businessDate: '2026-09-15' },
        { kind: 'RESERVATION', id: '4003', status: 'WAITLIST', title: 'Database Systems', businessDate: '2026-09-16' },
        { kind: 'FINE', id: '5001', status: 'UNPAID', amountCents: 150 },
      ],
      nextCursor: null,
    },
  },
  listFines: {
    code: 'MLS-PAY-S001',
    message: 'ok',
    data: {
      items: [
        { fineId: '5001', readerId: '1001', amountCents: 150, status: 'UNPAID', createdAt: '2026-09-15T10:00:00+08:00' },
      ],
      nextCursor: null,
    },
  },
  createOrder: {
    code: 'MLS-PAY-S002',
    message: 'created',
    data: { orderId: '6002', fineId: '5001', amountCents: 150, status: 'OCCUPIED', expiresAt: null },
  },
  getOrder: {
    code: 'MLS-PAY-S003',
    message: 'ok',
    data: { orderId: '6002', fineId: '5001', amountCents: 150, status: 'OCCUPIED', expiresAt: null },
  },
  scheduleSimulatedPayment: {
    code: 'MLS-PAY-S004',
    message: 'scheduled',
    data: { simulationId: '8001', status: 'SCHEDULED' },
  },
  closeOrder: {
    code: 'MLS-PAY-S005',
    message: 'closed',
    data: { orderId: '6002', fineId: '5001', amountCents: 150, status: 'CLOSED', expiresAt: null },
  },
  requestRefund: { code: 'MLS-PAY-S006', message: 'review requested', data: { reviewId: '6003', status: 'PENDING' } },
  reconcileBusinessDate: {
    code: 'MLS-PAY-S007',
    message: 'reconciled',
    data: { reconciliationId: '9001', counts: { orders: 3, paid: 2, failed: 1 }, exceptions: [] },
  },
  listReviews: {
    code: 'MLS-REV-S001',
    message: 'ok',
    data: {
      items: [
        {
          reviewId: '6001',
          targetType: 'ACCOUNT_CHANGE',
          targetId: '1001',
          action: 'SUSPEND',
          status: 'PENDING',
          requestedAt: '2026-09-15T09:00:00+08:00',
        },
      ],
      nextCursor: null,
    },
  },
  decideReview: { code: 'MLS-REV-S002', message: 'decided', data: { reviewId: '6001', status: 'APPROVED' } },
  listNotifications: {
    code: 'MLS-DEL-S001',
    message: 'ok',
    data: {
      items: [
        {
          notificationId: '7002',
          type: 'PICKUP_READY',
          message: '您预约的书已到台，请于明日取书',
          read: false,
          relatedTxId: '4003',
          createdAt: '2026-09-15T12:00:00+08:00',
        },
        {
          notificationId: '7003',
          type: 'CLOSING_NOTICE',
          message: '今日闭馆批次已完成',
          read: true,
          createdAt: '2026-09-15T23:30:00+08:00',
        },
      ],
      nextCursor: null,
    },
  },
  markNotificationRead: { code: 'MLS-DEL-S002', message: 'read', data: { read: true } },
  listAuditEvents: {
    code: 'MLS-DEL-S003',
    message: 'ok',
    data: {
      items: [
        {
          eventId: '9501',
          eventType: 'ORDER_PAID',
          actorId: '1001',
          relatedId: '6002',
          outcome: 'SUCCESS',
          occurredAt: '2026-09-15T12:30:00+08:00',
        },
      ],
      nextCursor: null,
    },
  },
  reopenBusinessDate: {
    code: 'MLS-CIRC-S010',
    message: 'reopened',
    data: { status: 'REOPENED', epoch: 2 },
  },
  runClosing: {
    code: 'MLS-CIRC-S011',
    message: 'run',
    data: { batchId: '8501', status: 'COMPLETED' },
  },
  startAiSession: {
    code: 'MLS-AI-S001',
    message: 'ok',
    data: { sessionId: '7001', role: 'READER', quota: { remaining: 10 } },
  },
  sendAiMessage: {
    code: 'MLS-AI-S001',
    message: 'ok',
    data: {
      messageId: '7101',
      answer: '请核对后提交',
      citations: [],
      actions: [
        { actionType: 'RENEW_LOAN', level: 'L2', prefill: { txId: '4001' }, draftId: '7201', label: '打开续借表单' },
      ],
      quota: { remaining: 9 },
    },
  },
  listAiEvents: {
    code: 'MLS-AI-S002',
    message: 'ok',
    data: {
      items: [
        { eventId: '7301', type: 'TOOL_CALL', payload: { tool: 'renew_loan' }, occurredAt: '2026-09-15T14:00:00+08:00' },
        { eventId: '7302', type: 'QUOTA_CONSUMED', payload: { remaining: 9 }, occurredAt: '2026-09-15T14:00:01+08:00' },
      ],
      nextCursor: null,
    },
  },
}

export default fixtures

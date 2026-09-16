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
}

export default fixtures

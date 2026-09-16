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
}

export default fixtures

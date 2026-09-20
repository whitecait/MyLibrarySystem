// Versioned dataset generator for the M6 performance protocol
// (RA-0013 §11 data distribution [D], MLS-DEC-0025, 2026-09-10).
//
//   node e2e/data/generate-dataset.mjs --out e2e/data/out [--base-date 2026-09-10]
//
// Deterministic: fixed seed 20260910, mulberry32 PRNG, no wall clock and no
// Math.random — the same generator version produces byte-identical output,
// so the SHA-256 checksums in manifest.json are comparable across runs.
//
// Scale (capacity baseline, MLS-NFR-PER-0001): 1,000 accounts, 10,000
// titles, 30,000 copies, 100,000 borrow records. Composition (approximate
// mix per [D]; exact counts produced by the seed are registered in
// manifest.json): ~80% completed returns, 10% active loans, 7%
// reservation/waitlist family, 3% overdue/fine states. Popularity: the top
// 10% of titles carry ~80% of retrieval and loan traffic (hot set).
//
// Dataset accounts are NOT for login: they share one bcrypt hash (fixed
// salt) whose password is documented here for integrity review only —
// "dataset-no-login-20260910" — and load scenarios must use accounts
// registered through the API during warmup. Bulk login against dataset
// accounts is not a supported scenario.
import { createHash } from 'node:crypto'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import bcryptjs from 'bcryptjs'

const here = dirname(fileURLToPath(import.meta.url))

function argValue(name, fallback) {
  const i = process.argv.indexOf(name)
  return i > -1 ? process.argv[i + 1] : fallback
}
const outDir = resolve(argValue('--out', join(here, 'out')))
const baseDate = argValue('--base-date', '2026-09-10')

const SEED = 20260910
const GENERATOR_VERSION = 'seed-20260910-v1'

function mulberry32(seed) {
  let a = seed >>> 0
  return function () {
    a |= 0
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
const rnd = mulberry32(SEED)
const pick = (arr) => arr[Math.floor(rnd() * arr.length)]
const intBetween = (lo, hi) => lo + Math.floor(rnd() * (hi - lo + 1))

// --- calendar helpers (all dates UTC, BASE = --base-date) -------------------
const BASE = new Date(`${baseDate}T00:00:00Z`)
const dayOffset = (d, days) => new Date(d.getTime() + days * 86400000)
const dateStr = (d) => d.toISOString().slice(0, 10)
const tsStr = (d) => {
  const hh = String(intBetween(8, 21)).padStart(2, '0')
  const mm = String(intBetween(0, 59)).padStart(2, '0')
  const ss = String(intBetween(0, 59)).padStart(2, '0')
  return `${dateStr(d)} ${hh}:${mm}:${ss}+00`
}

// --- fixed composition -------------------------------------------------------
const ACCOUNTS = { total: 1000, readers: 900, librarians: 80, admins: 20 }
const TITLES = 10000
const HOT_COUNT = 1000 // top 10% of titles
const COPIES_PER_TITLE = 3 // 30,000 copies
const RECORDS = { returned: 80000, lent: 10000, reservationFamily: 7000, overdueFined: 3000 }
const RESERVATION_SPLIT = { confirmed: 3000, waitlist: 3000, await: 600, manual: 400 }
const FINE_SPLIT = { due: 600, settled: 2400 }

// --- corpus word lists -------------------------------------------------------
const CATALOG = [
  { cat: '计算机', subjects: ['数据库', '算法', '计算机网络', '操作系统', '编译原理', '软件工程'] },
  { cat: '文学', subjects: ['古典文学', '现代小说', '散文集', '诗词', '世界文学', '文学批评'] },
  { cat: '科幻', subjects: ['科幻世界', '星际', '时间旅行', '机器人', '赛博朋克', '太空歌剧'] },
  { cat: '历史', subjects: ['世界史', '中国史', '文明史', '考古', '战争史', '传记历史'] },
  { cat: '艺术', subjects: ['绘画', '音乐史', '建筑', '电影', '设计', '书法'] },
  { cat: '教育', subjects: ['教育学', '心理学', '教学方法', '课程设计', '儿童发展', '教育技术'] },
]
const SUFFIXES = ['导论', '基础', '实践', '原理', '新论', '手册', '札记', '研究', '入门', '指南', '概述', '探析']
const AUTHORS_CN = ['王小明', '李文渊', '张知远', '陈静怡', '刘墨白', '赵青禾', '孙书言', '周砚秋', '吴晗章', '郑雨时']
const AUTHORS_EN = ['Ada Hartley', 'Ben Okafor', 'Clara Voss', 'Dmitri Ivanov', 'Elena Moretti', 'Felix Braun']
const PUBLISHERS = ['工业出版社', '文汇出版社', '未来出版社', '史学出版社', '艺术与设计出版社', '教育联合出版社']

const passwordHash = bcryptjs.hashSync('dataset-no-login-20260910', '$2a$10$abcdefghijklmnopqrstuu')

// --- accounts (ids 1..1000 in file order) ------------------------------------
const accounts = []
for (let i = 1; i <= ACCOUNTS.readers; i++) {
  const created = dayOffset(BASE, -intBetween(30, 180))
  accounts.push({ id: i, username: `reader-${String(i).padStart(4, '0')}`, display: `读者${String(i).padStart(4, '0')}`, role: 'READER', created })
}
for (let i = 1; i <= ACCOUNTS.librarians; i++) {
  const id = ACCOUNTS.readers + i
  accounts.push({ id, username: `lib-${String(i).padStart(2, '0')}`, display: `馆员${String(i).padStart(2, '0')}`, role: 'LIBRARIAN', created: dayOffset(BASE, -intBetween(60, 180)) })
}
for (let i = 1; i <= ACCOUNTS.admins; i++) {
  const id = ACCOUNTS.readers + ACCOUNTS.librarians + i
  accounts.push({ id, username: `admin-${String(i).padStart(2, '0')}`, display: `管理员${String(i).padStart(2, '0')}`, role: 'ADMIN', created: dayOffset(BASE, -intBetween(60, 180)) })
}

// --- titles (ids 1..10000); ids 1..1000 are the hot set ----------------------
const titles = []
outer: for (let cat = 0; cat < CATALOG.length; cat++) {
  for (const subj of CATALOG[cat].subjects) {
    for (const suffix of SUFFIXES) {
      for (let vol = 1; vol <= 25; vol++) {
        if (titles.length >= TITLES) break outer
        const id = titles.length + 1
        const created = dayOffset(BASE, -intBetween(20, 200))
        titles.push({
          id,
          isbn: `9787${String(100000000 + id)}`,
          title: vol === 1 ? `${subj}${suffix}` : `${subj}${suffix}（第${vol}卷）`,
          author: id % 2 === 0 ? pick(AUTHORS_CN) : pick(AUTHORS_EN),
          publisher: pick(PUBLISHERS),
          category: CATALOG[cat].cat,
          summary: `${subj}${suffix}学习用构造书目，用于受控数据集。`,
          priceCents: intBetween(2000, 30000),
          created,
        })
      }
    }
  }
}
const isHot = (id) => id <= HOT_COUNT
// Traffic-weighted title pick: 80% hot / 20% tail.
function pickTitle() {
  const hot = rnd() < 0.8
  return hot ? intBetween(1, HOT_COUNT) : intBetween(HOT_COUNT + 1, TITLES)
}

// --- copies (ids 1..30000, three per title, file order) ----------------------
const copyId = (t, n) => (t - 1) * COPIES_PER_TITLE + n
const copyStatus = new Array(TITLES * COPIES_PER_TITLE + 1).fill('SHELF_READY')
// 500 maintenance copies, deterministic slice in the tail.
for (let k = 0; k < 500; k++) copyStatus[copyId(TITLES - k, 3)] = 'MAINTENANCE'

// --- open-status binding with per-title copy capacity ------------------------
const freeCopy = (titleId, used) => {
  for (let n = 1; n <= COPIES_PER_TITLE; n++) {
    const cid = copyId(titleId, n)
    if (!used.has(cid)) return cid
  }
  return null
}
const openPairs = new Set() // `${readerId}:${titleId}` unique among open statuses (uq_borrow_tx_open_per_reader_title)
const usedCopies = new Set() // copies currently LENT or RESERVED
function bindOpen(readerId) {
  for (let tries = 0; tries < 60; tries++) {
    const titleId = pickTitle()
    const pair = `${readerId}:${titleId}`
    if (openPairs.has(pair)) continue
    const cid = freeCopy(titleId, usedCopies)
    if (cid === null) continue
    openPairs.add(pair)
    usedCopies.add(cid)
    return { titleId, copyId: cid }
  }
  return null
}

const transactions = [] // {reader,title,copy,status,reservationDate,pickupSlot,due,queued,condition,created}
// active loans (10%)
for (let i = 0; i < RECORDS.lent; i++) {
  const readerId = intBetween(1, ACCOUNTS.readers)
  const bound = bindOpen(readerId)
  if (!bound) continue
  const created = dayOffset(BASE, -intBetween(0, 6))
  copyStatus[bound.copyId] = 'LENT'
  transactions.push({ readerId, ...bound, status: 'LENT', due: dateStr(dayOffset(created, 7)), created, updated: created })
}
// reservations family (7%)
for (let i = 0; i < RESERVATION_SPLIT.confirmed; i++) {
  const readerId = intBetween(1, ACCOUNTS.readers)
  const bound = bindOpen(readerId)
  if (!bound) continue
  const created = dayOffset(BASE, -intBetween(1, 13))
  copyStatus[bound.copyId] = 'RESERVED'
  transactions.push({ readerId, ...bound, status: 'CONFIRMED', reservationDate: dateStr(dayOffset(BASE, intBetween(1, 7))), pickupSlot: `${intBetween(9, 17)}:00-${intBetween(10, 18)}:30`, created, updated: created })
}
for (let i = 0; i < RESERVATION_SPLIT.waitlist; i++) {
  const readerId = intBetween(1, ACCOUNTS.readers)
  const created = dayOffset(BASE, -intBetween(1, 13))
  let titleId = pickTitle()
  if (openPairs.has(`${readerId}:${titleId}`)) titleId = titleId === TITLES ? 1 : titleId + 1
  openPairs.add(`${readerId}:${titleId}`)
  transactions.push({ readerId, titleId, copyId: null, status: 'WAITLIST', reservationDate: dateStr(dayOffset(BASE, intBetween(1, 7))), queued: created, created, updated: created })
}
for (let i = 0; i < RESERVATION_SPLIT.await; i++) {
  const readerId = intBetween(1, ACCOUNTS.readers)
  const created = dayOffset(BASE, -intBetween(0, 2))
  let titleId = pickTitle()
  if (openPairs.has(`${readerId}:${titleId}`)) titleId = titleId === TITLES ? 1 : titleId + 1
  openPairs.add(`${readerId}:${titleId}`)
  transactions.push({ readerId, titleId, copyId: null, status: 'AWAIT', created, updated: created })
}
for (let i = 0; i < RESERVATION_SPLIT.manual; i++) {
  const readerId = intBetween(1, ACCOUNTS.readers)
  const created = dayOffset(BASE, -intBetween(1, 10))
  let titleId = pickTitle()
  if (openPairs.has(`${readerId}:${titleId}`)) titleId = titleId === TITLES ? 1 : titleId + 1
  openPairs.add(`${readerId}:${titleId}`)
  transactions.push({ readerId, titleId, copyId: null, status: 'MANUAL_HANDLING', created, updated: created })
}
// completed returns (80%) — appended before the overdue block
const fines = [] // {reader, sourceTxIndex(0-based within transactions), amount, status, settledAt, created}
for (let i = 0; i < RECORDS.returned; i++) {
  const readerId = intBetween(1, ACCOUNTS.readers)
  const titleId = pickTitle()
  const created = dayOffset(BASE, -intBetween(14, 180))
  const due = dayOffset(created, 7)
  const returned = dayOffset(due, -intBetween(0, 6))
  const condition = rnd() < 0.95 ? 'INTACT' : rnd() < 0.5 ? 'DAMAGED' : 'LOST'
  transactions.push({ readerId, titleId, copyId: copyId(titleId, intBetween(1, COPIES_PER_TITLE)), status: 'RETURNED', due: dateStr(due), condition, created, updated: returned })
}
// overdue with fines (3%) — contiguous final block so fine.source_tx_id is deterministic
for (let i = 0; i < RECORDS.overdueFined; i++) {
  const readerId = intBetween(1, ACCOUNTS.readers)
  const titleId = pickTitle()
  const created = dayOffset(BASE, -intBetween(14, 40))
  const due = dayOffset(created, 7)
  const daysLate = intBetween(1, 10)
  const returned = dayOffset(due, daysLate)
  const amount = Math.min(Math.ceil(titles[titleId - 1].priceCents * 0.1 * daysLate), titles[titleId - 1].priceCents * 3)
  const isDue = i < FINE_SPLIT.due
  transactions.push({ readerId, titleId, copyId: copyId(titleId, intBetween(1, COPIES_PER_TITLE)), status: 'RETURNED', due: dateStr(due), condition: 'INTACT', created, updated: returned })
  fines.push({
    readerId,
    txIndex: transactions.length - 1,
    amount,
    status: isDue ? 'DUE' : 'SETTLED',
    settledAt: isDue ? null : tsStr(dayOffset(returned, 1)),
    created: returned,
  })
}
// one DUE fine per reader at most (uq_fine_open_per_reader): repair collisions
{
  const seen = new Set()
  for (const f of fines) {
    if (f.status === 'DUE') {
      if (seen.has(f.readerId)) f.status = 'SETTLED'
      else seen.add(f.readerId)
      f.settledAt = f.status === 'SETTLED' ? f.settledAt ?? tsStr(dayOffset(BASE, -1)) : null
    }
  }
}

// --- SQL emission (COPY text format; ids are file-order, sequences resynced
//     by the loader) -----------------------------------------------------------
const esc = (s) => String(s).replace(/\\/g, '\\\\').replace(/\t/g, '\\t').replace(/\n/g, '\\n')
const N = '\\N'
function copyBlock(table, columns, rows) {
  const lines = rows.map((r) => r.map((v) => (v === null || v === undefined ? N : esc(v))).join('\t'))
  return `COPY ${table} (${columns.join(', ')}) FROM stdin;\n${lines.join('\n')}\n\\.\n\n`
}

const accountRows = accounts.map((a) => [a.username, passwordHash, a.display, a.role, 'ACTIVE', 0, tsStr(a.created), tsStr(a.created)])
const titleRows = titles.map((t) => [t.isbn, t.title, t.author, t.publisher, t.category, t.summary, t.priceCents, 0, tsStr(t.created), tsStr(t.created)])
const copyRows = []
for (let t = 1; t <= TITLES; t++) {
  for (let n = 1; n <= COPIES_PER_TITLE; n++) {
    const cid = copyId(t, n)
    copyRows.push([t, `CP-${t}-${n}`, copyStatus[cid], 0, tsStr(dayOffset(BASE, -intBetween(10, 200))), tsStr(dayOffset(BASE, -intBetween(0, 10)))])
  }
}
const txRows = transactions.map((tx, i) => [
  tx.readerId, tx.titleId, tx.copyId ?? N, tx.status,
  tx.reservationDate ?? N, tx.pickupSlot ?? N, tx.due ?? N, tx.queued ? tsStr(tx.queued) : N,
  tx.condition ?? N, 0, tsStr(tx.created), tsStr(tx.updated),
])
const fineRows = fines.map((f) => [
  f.readerId, f.txIndex + 1, f.amount, f.status, f.settledAt ?? N, 0, tsStr(f.created), tsStr(f.created),
])

let sql = `-- MLS M6 performance dataset, generator ${GENERATOR_VERSION}, seed ${SEED}, base date ${baseDate}\n`
sql += `-- Load only into a FRESH dedicated instance via e2e/data/load-dataset.sh.\n\n`
sql += 'BEGIN;\n\n'
sql += copyBlock('account', ['username', 'password_hash', 'display_name', 'role', 'status', 'version', 'created_at', 'updated_at'], accountRows)
sql += copyBlock('book_title', ['isbn', 'title', 'author', 'publisher', 'category', 'summary', 'price_cents', 'version', 'created_at', 'updated_at'], titleRows)
sql += copyBlock('book_copy', ['title_id', 'copy_no', 'status', 'version', 'created_at', 'updated_at'], copyRows)
sql += copyBlock('borrow_transaction', ['reader_id', 'title_id', 'copy_id', 'status', 'reservation_date', 'pickup_slot', 'due_date', 'queued_at', 'return_condition', 'version', 'created_at', 'updated_at'], txRows)
sql += copyBlock('fine', ['reader_id', 'source_tx_id', 'amount_cents', 'status', 'settled_at', 'version', 'created_at', 'updated_at'], fineRows)
sql += 'COMMIT;\n'

// --- retrieval query sets (hot popular / tail long-tail) ---------------------
const hotTitles = titles.slice(0, HOT_COUNT)
const tailTitles = titles.slice(HOT_COUNT)
function buildQueries(pool) {
  const queries = []
  for (let i = 0; i < 40; i++) {
    const t = pool[Math.floor(rnd() * pool.length)]
    const frag = t.title.replace(/[（）()]/g, '').slice(0, intBetween(2, 4))
    queries.push({ domain: 'title-fragment', q: frag })
  }
  for (let i = 0; i < 30; i++) queries.push({ domain: 'author', q: pool[Math.floor(rnd() * pool.length)].author })
  for (let i = 0; i < 30; i++) queries.push({ domain: 'isbn', q: pool[Math.floor(rnd() * pool.length)].isbn })
  for (let i = 0; i < 20; i++) queries.push({ domain: 'category', q: pick(CATALOG).cat })
  return queries
}
const queriesHot = buildQueries(hotTitles)
const queriesTail = buildQueries(tailTitles)

// --- write outputs + manifest -------------------------------------------------
mkdirSync(outDir, { recursive: true })
const sqlFile = join(outDir, 'dataset.sql')
const hotFile = join(outDir, 'queries-hot.json')
const tailFile = join(outDir, 'queries-tail.json')
writeFileSync(sqlFile, sql)
writeFileSync(hotFile, JSON.stringify(queriesHot, null, 2))
writeFileSync(tailFile, JSON.stringify(queriesTail, null, 2))

const sha256 = (p) => createHash('sha256').update(readFileSync(p)).digest('hex')

const statusCounts = transactions.reduce((acc, t) => ((acc[t.status] = (acc[t.status] ?? 0) + 1), acc), {})
const copyCounts = copyStatus.slice(1).reduce((acc, s) => ((acc[s] = (acc[s] ?? 0) + 1), acc), {})
const manifest = {
  generatorVersion: GENERATOR_VERSION,
  seed: SEED,
  baseDate,
  scale: { accounts: accounts.length, titles: TITLES, copies: TITLES * COPIES_PER_TITLE, records: transactions.length },
  recordComposition: statusCounts,
  copyComposition: copyCounts,
  fines: { due: fines.filter((f) => f.status === 'DUE').length, settled: fines.filter((f) => f.status === 'SETTLED').length },
  hotSet: { titles: HOT_COUNT, share: 'top 10% of titles carry ~80% of retrieval and loan traffic (enforced by query sets and load mix)' },
  datasetAccounts: 'not for login (shared fixed-salt bcrypt hash, password dataset-no-login-20260910); load scenarios must use API-registered probe accounts',
  checksums: {
    'dataset.sql': sha256(sqlFile),
    'queries-hot.json': sha256(hotFile),
    'queries-tail.json': sha256(tailFile),
  },
}
writeFileSync(join(outDir, 'manifest.json'), JSON.stringify(manifest, null, 2))
console.log(JSON.stringify(manifest, null, 2))

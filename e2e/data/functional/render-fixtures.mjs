// Functional-batch fixed dataset renderer (acceptance plan 2026-09-19 §4.3).
//
//   node e2e/data/functional/render-fixtures.mjs --out e2e/data/out
//
// Emits, deterministically:
//   at-0024.sql            AT-0002 §2.24 arrival-statistics dataset (load with
//                          `psql -v D=<business date D>` into a FRESH dedicated
//                          instance; row-for-row maps to the §2.24 tables)
//   at-0029-corpus.sql     AT-0002 §2.29 annotation corpus R-01~R-12
//   at-0029-annotation.json  the DEC-0027-approved query annotation set v1.1
//   manifest.json          SHA-256 checksums of the three files
//
// All fixture accounts share one bcrypt hash (fixed salt) of the documented
// fixture password "M6-Fixture-2026!" — a controlled local learning
// credential printed here because executors must log in as these readers;
// the dataset instances are never exposed beyond Env-W/Env-P.
import { createHash } from 'node:crypto'
import { mkdirSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import bcryptjs from 'bcryptjs'

const here = dirname(fileURLToPath(import.meta.url))
const outDir = resolve(argValue('--out', join(here, '..', 'out')))

function argValue(name, fallback) {
  const i = process.argv.indexOf(name)
  return i > -1 ? process.argv[i + 1] : fallback
}
const sha256 = (s) => createHash('sha256').update(s).digest('hex')
const fixtureHash = bcryptjs.hashSync('M6-Fixture-2026!', '$2a$10$abcdefghijklmnopqrstuu')

// ---------------------------------------------------------------------------
// AT-0024 arrival statistics (AT-0002 §2.24; business date D via psql -v D)
// Account id map: 甲乙丙丁戊己庚辛壬癸子丑寅 = 920000001..920000013,
// holders 壹/贰/叁 = 920000014..16, LIB-001 = 920000017, ADM-001 = 920000018.
// Titles B1..B7 = 920100001..920100007; copies 920200001..920200016;
// transactions 920300001..920300018.
// ---------------------------------------------------------------------------
const readers = ['甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸', '子', '丑', '寅', '壹', '贰', '叁']
const accountRows = [
  ...readers.map((name, i) => [920000001 + i, `r-${name}`, fixtureHash, `读者${name}`, 'READER', 'ACTIVE']),
  [920000017, 'LIB-001', fixtureHash, '馆员一', 'LIBRARIAN', 'ACTIVE'],
  [920000018, 'ADM-001', fixtureHash, '管理员一', 'ADMIN', 'ACTIVE'],
]
const M = { 甲: 920000001, 乙: 920000002, 丙: 920000003, 丁: 920000004, 戊: 920000005, 己: 920000006, 庚: 920000007, 辛: 920000008, 壬: 920000009, 癸: 920000010, 子: 920000011, 丑: 920000012, 寅: 920000013, 壹: 920000014, 贰: 920000015, 叁: 920000016 }
const B = { B1: 920100001, B2: 920100002, B3: 920100003, B4: 920100004, B5: 920100005, B6: 920100006, B7: 920100007 }
const C = { b1a: 920200001, b1b: 920200002, b1c: 920200003, b1d: 920200004, b2a: 920200005, b2b: 920200006, b2c: 920200007, b3a: 920200008, b4a: 920200009, b5a: 920200010, b5b: 920200011, b6a: 920200012, b6b: 920200013, b6c: 920200014, b7x: 920200015, b7y: 920200016 }
const T = { 甲: 920300001, 壬1: 920300002, 壬2: 920300003, 癸1: 920300004, 癸2: 920300005, 乙: 920300006, 辛: 920300007, 子B2: 920300008, 丁: 920300009, 丙: 920300010, 丑: 920300011, 戊: 920300012, 子B5: 920300013, 己: 920300014, 寅: 920300015, 壹: 920300016, 贰: 920300017, 叁: 920300018 }

const titleDefs = [
  [B.B1, '9787000100001', '数据结构基础', '编者甲', '教育联合出版社', '教育', 9900],
  [B.B2, '9787000100002', '操作系统原理', 'Ada Hartley', '工业出版社', '计算机', 12900],
  [B.B3, '9787000100003', '古典文学选读', '李文渊', '文汇出版社', '文学', 8900],
  [B.B4, '9787000100004', '科幻世界年选', '刘墨白', '未来出版社', '科幻', 10900],
  [B.B5, '9787000100005', '艺术史纲', 'Clara Voss', '艺术与设计出版社', '艺术', 11900],
  [B.B6, '9787000100006', '中国史纲要', '张知远', '史学出版社', '历史', 9900],
  [B.B7, '9787000100007', '教育心理学', '陈静怡', '教育联合出版社', '教育', 13900],
]
const copyDefs = [
  [C.b1a, B.B1, 'b1a', 'RESERVED'], [C.b1b, B.B1, 'b1b', 'MAINTENANCE'], [C.b1c, B.B1, 'b1c', 'RESERVED'], [C.b1d, B.B1, 'b1d', 'RESERVED'],
  [C.b2a, B.B2, 'b2a', 'RESERVED'], [C.b2b, B.B2, 'b2b', 'RESERVED'], [C.b2c, B.B2, 'b2c', 'RESERVED'],
  [C.b3a, B.B3, 'b3a', 'RESERVED'], [C.b4a, B.B4, 'b4a', 'RESERVED'],
  [C.b5a, B.B5, 'b5a', 'LENT'], [C.b5b, B.B5, 'b5b', 'LENT'],
  [C.b6a, B.B6, 'b6a', 'SHELF_READY'], [C.b6b, B.B6, 'b6b', 'SHELF_READY'], [C.b6c, B.B6, 'b6c', 'RESERVED'],
  [C.b7x, B.B7, 'b7x', 'LENT'], [C.b7y, B.B7, 'b7y', 'RESERVED'],
]
// [txId, reader, title, copy, status, resDateExpr, pickupSlot, dueExpr, queuedExpr]
// resDateExpr/dueExpr/queuedExpr are RAW SQL fragments (psql :'D' variable),
// emitted verbatim; pickupSlot is a literal and goes through v().
const txDefs = [
  [T.甲, M.甲, B.B1, C.b1a, 'CONFIRMED', ":'D'::date", '14:00-14:30', null, null],
  [T.壬1, M.壬, B.B1, C.b1c, 'CONFIRMED', ":'D'::date", '09:00-09:30', null, null],
  [T.壬2, M.壬, B.B2, C.b2a, 'CONFIRMED', ":'D'::date", '09:30-10:00', null, null],
  [T.癸1, M.癸, B.B1, C.b1d, 'CONFIRMED', ":'D'::date", '10:00-10:30', null, null],
  [T.癸2, M.癸, B.B2, C.b2b, 'CONFIRMED', ":'D'::date", '10:30-11:00', null, null],
  [T.乙, M.乙, B.B3, C.b3a, 'CONFIRMED', ":'D'::date", '11:00-11:30', null, null],
  [T.辛, M.辛, B.B4, C.b4a, 'CONFIRMED', ":'D'::date", '15:00-15:30', null, null],
  [T.子B2, M.子, B.B2, C.b2c, 'CONFIRMED', ":'D'::date + 1", '09:00-09:30', null, null],
  [T.丁, M.丁, B.B7, C.b7y, 'CONFIRMED', ":'D'::date + 1", '14:00-14:30', null, null],
  [T.丙, M.丙, B.B1, null, 'WAITLIST', ":'D'::date", null, null, "(:'D'::date)::timestamptz + interval '9 hours'"],
  [T.丑, M.丑, B.B3, null, 'WAITLIST', ":'D'::date", null, null, "(:'D'::date)::timestamptz + interval '9 hours 10 minutes'"],
  [T.戊, M.戊, B.B5, null, 'WAITLIST', ":'D'::date + 1", null, null, "(:'D'::date - 2)::timestamptz + interval '15 hours'"],
  [T.子B5, M.子, B.B5, null, 'WAITLIST', ":'D'::date + 1", null, null, "(:'D'::date - 1)::timestamptz + interval '15 hours'"],
  [T.己, M.己, B.B7, null, 'WAITLIST', ":'D'::date + 1", null, null, "(:'D'::date - 1)::timestamptz + interval '16 hours'"],
  [T.寅, M.寅, B.B6, C.b6c, 'MANUAL_HANDLING', ":'D'::date - 1", null, null, null],
  [T.壹, M.壹, B.B5, C.b5a, 'LENT', null, null, ":'D'::date", "(:'D'::date - 7)::timestamptz + interval '10 hours'"],
  [T.贰, M.贰, B.B5, C.b5b, 'LENT', null, null, ":'D'::date", "(:'D'::date - 7)::timestamptz + interval '11 hours'"],
  [T.叁, M.叁, B.B7, C.b7x, 'LENT', null, null, ":'D'::date + 3", "(:'D'::date - 4)::timestamptz + interval '10 hours'"],
]
const v = (x) => (x === null ? 'NULL' : typeof x === 'number' ? String(x) : `'${String(x).replace(/'/g, "''")}'`)

const at0024Sql = `-- AT-0002 §2.24 fixed dataset (fourteen-reader arrival statistics).
-- Load into a FRESH dedicated instance with:
--   psql -v D=<business date D> -v ON_ERROR_STOP=1 -f at-0024.sql
-- S0 expected counters: 计划到馆 5 | 尚未到馆 5（0） | 今日借阅 0（0） | 明日预计 3（1）
BEGIN;
DO $$ BEGIN
  IF (SELECT count(*) FROM book_title) > 0 THEN RAISE EXCEPTION 'non-fresh instance'; END IF;
END $$;
INSERT INTO account (id, username, password_hash, display_name, role, status) OVERRIDING SYSTEM VALUE VALUES
${accountRows.map((r) => `  (${r.map(v).join(', ')})`).join(',\n')};
-- fixture password for all accounts above: M6-Fixture-2026! (controlled local dataset)
INSERT INTO book_title (id, isbn, title, author, publisher, category, price_cents) OVERRIDING SYSTEM VALUE VALUES
${titleDefs.map((r) => `  (${r.map(v).join(', ')})`).join(',\n')};
INSERT INTO book_copy (id, title_id, copy_no, status) OVERRIDING SYSTEM VALUE VALUES
${copyDefs.map((r) => `  (${r.map(v).join(', ')})`).join(',\n')};
INSERT INTO borrow_transaction (id, reader_id, title_id, copy_id, status, reservation_date, pickup_slot, due_date, queued_at) OVERRIDING SYSTEM VALUE VALUES
${txDefs
  .map((tx) => `  (${tx[0]}, ${tx[1]}, ${tx[2]}, ${tx[3] ?? 'NULL'}, ${v(tx[4])}, ${tx[5] ?? 'NULL'}, ${tx[6] ? v(tx[6]) : 'NULL'}, ${tx[7] ?? 'NULL'}, ${tx[8] ?? 'NULL'})`)
  .join(',\n')};
SELECT setval(pg_get_serial_sequence('account','id'), (SELECT max(id) FROM account));
SELECT setval(pg_get_serial_sequence('book_title','id'), (SELECT max(id) FROM book_title));
SELECT setval(pg_get_serial_sequence('book_copy','id'), (SELECT max(id) FROM book_copy));
SELECT setval(pg_get_serial_sequence('borrow_transaction','id'), (SELECT max(id) FROM borrow_transaction));
COMMIT;
`

// ---------------------------------------------------------------------------
// AT-0029 annotation corpus (AT-0002 §2.29, DEC-0027 approved 2026-09-11)
// ---------------------------------------------------------------------------
const corpus = [
  ['R-01', '数据库系统概念', 'Henry Korth', '9787111200001', '计算机'],
  ['R-02', '数据库系统实现', 'Hector Garcia-Molina', '9787111200002', '计算机'],
  ['R-03', '数据结构与算法分析', 'Mark Allen Weiss', '9787111200003', '计算机'],
  ['R-04', 'Java 编程思想', 'Bruce Eckel', '9787111200004', '计算机'],
  ['R-05', '红楼梦', '曹雪芹', '9787020000005', '文学'],
  ['R-06', '红楼梦新证', '周汝昌', '9787101000006', '文学'],
  ['R-07', '三体', '刘慈欣', '9787536000007', '科幻'],
  ['R-08', '三体Ⅱ：黑暗森林', '刘慈欣', '9787536000008', '科幻'],
  ['R-09', 'The Little Prince 小王子', 'Antoine de Saint-Exupéry', '9787544000009', '文学'],
  ['R-10', '1984', 'George Orwell', '9787544000010', '文学'],
  ['R-11', '西游记（全二册）', '吴承恩', '9787020000011', '文学'],
  ['R-12', '算法导论', 'Thomas H. Cormen', '9787111000012', '计算机'],
]
const corpusSql = `-- AT-0002 §2.29 annotation corpus R-01~R-12 (DEC-0027 approved v1.1).
-- Load into a FRESH dedicated instance with:
--   psql -v ON_ERROR_STOP=1 -f at-0029-corpus.sql
-- Fixed ids 910000001..910000012 are the titleId values referenced by
-- at-0029-annotation.json; valid only in the dedicated AT-0029 instance.
BEGIN;
DO $$ BEGIN
  IF (SELECT count(*) FROM book_title) > 0 THEN RAISE EXCEPTION 'non-fresh instance'; END IF;
END $$;
INSERT INTO book_title (id, isbn, title, author, publisher, category, price_cents) OVERRIDING SYSTEM VALUE VALUES
${corpus.map(([, , , isbn], i) => `  (${910000001 + i}, ${v(isbn)}, ${v(corpus[i][1])}, ${v(corpus[i][2])}, ${v('标注语料出版社')}, ${v(corpus[i][4])}, 5000)`).join(',\n')};
SELECT setval(pg_get_serial_sequence('book_title','id'), (SELECT max(id) FROM book_title));
COMMIT;
`
const annotation = {
  version: 'v1.1',
  registeredBy: 'MLS-DEC-0027 (approved 2026-09-11, authorized signature recorded by Codex)',
  sourceAnchor: 'MLS-DOC-AT-0002 §2.29 检索查询标注集 v1.1',
  titleIdMap: Object.fromEntries(corpus.map(([id], i) => [id, 910000001 + i])),
  corpus: corpus.map(([id, title, author, isbn, category]) => ({ id, title, author, isbn, category })),
  queries: [
    { id: 'Q-01', domain: '书名完整', query: '三体', expect: ['R-07'] },
    { id: 'Q-02', domain: '书名完整', query: '1984', expect: ['R-10'] },
    { id: 'Q-03', domain: '书名片段', query: '数据', expect: ['R-01', 'R-02', 'R-03'] },
    { id: 'Q-04', domain: '书名片段', query: '红楼梦', expect: ['R-05', 'R-06'] },
    { id: 'Q-05', domain: '作者', query: '刘慈欣', expect: ['R-07', 'R-08'] },
    { id: 'Q-06', domain: '作者', query: '曹雪芹', expect: ['R-05'] },
    { id: 'Q-07', domain: 'ISBN', query: '9787536000007', expect: ['R-07'] },
    { id: 'Q-08', domain: 'ISBN', query: '9787020000005', expect: ['R-05'] },
    { id: 'Q-09', domain: '分类', query: '计算机', expect: ['R-01', 'R-02', 'R-03', 'R-04', 'R-12'] },
    { id: 'Q-10', domain: '分类', query: '文学', expect: ['R-05', 'R-06', 'R-09', 'R-10', 'R-11'] },
    { id: 'Q-11', domain: '中文短词', query: '黑暗', expect: ['R-08'] },
    { id: 'Q-12', domain: '中文短词', query: '算法', expect: ['R-03', 'R-12'] },
    { id: 'Q-13', domain: '中英混合', query: 'Java 编程', expect: ['R-04'] },
    { id: 'Q-14', domain: '中英混合', query: 'The Little Prince 小王子', expect: ['R-09'] },
    { id: 'Q-15', domain: '数字', query: '7536000007', expect: ['R-07'] },
    { id: 'Q-16', domain: '数字', query: '7020000011', expect: ['R-11'] },
    { id: 'Q-17', domain: '全半角', query: '１９８４', expect: ['R-10'] },
    { id: 'Q-18', domain: '全半角', query: '西游记(全二册)', expect: ['R-11'] },
    { id: 'Q-19', domain: '无匹配', query: '量子纠缠史', noMatch: true },
    { id: 'Q-20', domain: '无匹配', query: 'zzzz', noMatch: true },
  ],
  judgment: 'K=10; per-query hard assertion: every expected title must appear in the top 10 (Recall@10=1.0 per query); no-match queries assert explicit empty feedback and are excluded from the denominator; no cross-query averaging.',
}

mkdirSync(outDir, { recursive: true })
const files = {
  'at-0024.sql': at0024Sql,
  'at-0029-corpus.sql': corpusSql,
  'at-0029-annotation.json': JSON.stringify(annotation, null, 2) + '\n',
}
for (const [name, content] of Object.entries(files)) writeFileSync(join(outDir, name), content)
const manifest = {
  renderer: 'e2e/data/functional/render-fixtures.mjs',
  fixturePassword: 'M6-Fixture-2026!',
  files: Object.fromEntries(Object.keys(files).map((name) => [name, sha256(files[name])])),
}
writeFileSync(join(outDir, 'fixtures-manifest.json'), JSON.stringify(manifest, null, 2))
console.log(JSON.stringify(manifest, null, 2))

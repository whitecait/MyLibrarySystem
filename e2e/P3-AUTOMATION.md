# P3 Windows 缺陷回归自动化

> 性质：针对 2026-09-30 Windows 原生复验两项失败补充的效率回归；不改变 P3 原始验收范围。
> 引用文档：
>
> - [2026-10-01 修订与测试记录](../artifacts/p3-revision-20261001/RUN-REPORT.md)
> - [M6 验收 harness](README.md)
> - [e2e 证据策略](EVIDENCE-POLICY.md)
> - [P3 Windows 集中验收规格](../docs/agents/delivery-closeout-20260925/P3-windows-acceptance.spec.md)
> - [P3 执行记录](../docs/agents/delivery-closeout-20260925/results/P3.md)
> - [Playwright 最佳实践](https://playwright.dev/docs/best-practices)
> - [Playwright 测试夹具](https://playwright.dev/docs/test-fixtures)

## 范围

新增的 Playwright 子集只重复检查两项已记录的失败：馆员罚款页在读者筛选为空时成功加载；真实模型为馆员生成的现场借阅草稿携带读者、书目、副本三字段，读者不能获得该动作，馆员须显式提交后才产生一笔借阅。

测试沿用 P3 隔离栈和 Windows 原生运行边界。它不重建 S1–S9，不运行日结调度，不改变业务日，也不把自动化通过登记成 P3 整体通过。用户批准的 IAB 正式验收范围继续有效；这组 Playwright 用例不替代 IAB 页面复验，也不引入额外浏览器矩阵。

## 分层覆盖与证据来源

| 层 | 技术与用途 | 本次覆盖 | 证据边界 |
| --- | --- | --- | --- |
| 快速组件回归 | 当前前端是 Vue 3；Vitest + Vue Test Utils 用于隔离表单状态、字段映射、权限显示和提交调用次数 | 继续使用现有组件测试；缺陷代理已在这些组件及服务端测试上完成修复验证 | 使用 mock transport 的组件通过只证明组件契约，不证明真实 API、模型或页面流程 |
| 受控浏览器 UI | Playwright 页面与受控响应可确定性检查无模型依赖的表单显示和布局 | 本批没有另建 mock 浏览器套件；不需要用 mock 重复替代组件断言 | mock UI 结果不得冒充真实后端或真实模型验收 |
| 真实后端浏览器回归 | Playwright 通过页面访问隔离候选栈，观察页面与实际 HTTP 请求 | 馆员空读者筛选；现场借阅表单应用字段；提交后的唯一 LENT 结果 | 空筛选以真实 GET 状态和表格为准；借阅以真实 POST、页面结果和副本状态为准 |
| 真实模型 Windows 链 | Playwright 不拦截 AI API，由隔离后端调用现有真实模型端点 | 读者权限拒绝；馆员 WALK_IN_LOAN L2 草稿字段、打开表单、两视口核对和显式提交 | 模型端点未配置、失败或两次尝试均未生成所需草稿时，测试失败；不能以 mock 响应替代 |

Playwright 版本沿用 e2e 已锁定的依赖。P3 专用配置固定单 worker、顺序执行、单 Chrome channel；不运行跨浏览器项目。用例使用 Playwright 提供的独立 page fixture，并优先以现有稳定 testid 和可访问标题定位；断言依赖 locator 自动等待与重试，符合[最佳实践](https://playwright.dev/docs/best-practices)和[测试夹具](https://playwright.dev/docs/test-fixtures)的建议。

## 用例与断言

### 馆员空读者筛选

`p3:fines` 使用运行时馆员账号登录 `/fines`，保留读者 ID 空值，观察自动加载及显式查询。两条请求均要求 HTTP 200、readerId 查询值为空或缺省、罚款表格可见且没有错误提示。相同页面在实际 `1280×800` 和 `800×600` 视口分别查询一次。此场景只有 GET，没有业务状态写入。

### AI 现场借阅草稿

`p3:ai-draft` 使用真实模型，不拦截 `/api/v1/ai`：

1. 以 READER 账号请求相同的馆员现场借阅动作。允许模型用 HTTP 200 正常拒绝且不返回 `WALK_IN_LOAN`，或在模型尝试调用未开放工具时由服务端以 HTTP 422、`MLS-AI-004` 拒绝；其他状态（包括 5xx、配额和认证错误）均失败。两种允许结果都要求事件流含 `MODEL_REQUEST`、没有持久化的现场借阅草稿、没有借阅请求；直接进入 `/walk-in` 仍要求显示 403。
2. 以 LIBRARIAN 账号请求指定夹具读者、书目和副本的 L2 草稿。最多发送两种明确措辞；若模型返回了 WALK_IN_LOAN 草稿，三字段必须当场逐项匹配，错误字段不会靠重试掩盖。
3. 通过该 session 的常规 events API 重新读取已保存的 `ACTION_DRAFT`，检查 actionType、L2 和 payload 中三字段，再点击草稿打开入口。
4. 检查 `/walk-in` 上三个输入值及草稿号。相同表单先后在两种指定视口核对并留 safe-shot 图。
5. 应用草稿期间，浏览器不得发出 `POST /api/v1/walk-in-loans`，且 copies API 状态仍为 AVAILABLE（数据库对应 SHELF_READY）。
6. 只有当运行配置显式设置 `P3_AI_EXPLICIT_SUBMIT=1` 时才点击“提交借阅”一次。随后要求恰好一个 POST、请求体仍含三个目标字段、草稿号头存在、响应与页面都只显示一册 LENT，copies API 状态转为 BORROWED（数据库对应 LENT）。

两个视口检查共用一个未提交草稿和同一副本；只有完成窄屏核对后才提交一次，不会因两个视口重复借出。脚本不创建或上架副本，也不清理已产生的借阅。

## 夹具与凭据

在 Windows 原生 PowerShell 下，从模板创建本地配置：

```powershell
Copy-Item e2e/.env.p3.example e2e/.env.p3
```

仅在本机编辑 `e2e/.env.p3`，填入：

- P3 隔离候选的 `P3_BASE_URL`，以及馆员、读者运行账号。
- 专用隔离夹具的 `P3_AI_READER_ID`、`P3_AI_TITLE_ID`、`P3_AI_COPY_ID`。
- 从该隔离库当前只读 readback 得到的业务日和副本 `loan_eligible_on`：`P3_BUSINESS_DATE`、`P3_AI_COPY_LOAN_ELIGIBLE_ON`。
- 证据根目录 `P3_OUTPUT_BASE`。默认在 `artifacts/m6-acceptance/p3-blockers/`；每次运行会自动创建唯一子目录。

AI 夹具必须来自当前隔离栈的实际数据读取：目标是有效 READER；副本属于给定书目、当前状态为 SHELF_READY，且 `loan_eligible_on <= P3_BUSINESS_DATE`。`P3_BUSINESS_DATE` 指后端 `BusinessClock` 按 Asia/Shanghai 计算的当前业务日，不是日结／GLOBAL `current_date`。应从当前服务器时钟或同一数据库的 Asia/Shanghai 只读时钟值核对业务日（例如 PostgreSQL：`SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Shanghai')::date`）；`loan_eligible_on` 则读取目标副本自己的实际字段，不照抄旧报告日期。浏览器运行时再以馆员权限调用现有 `GET /api/v1/copies?titleId=...` 核验书目归属和 API 状态 AVAILABLE（数据库对应 SHELF_READY）。运行预检会比较日期边界，避免把本次新建、当天尚不可借的副本当作正常夹具。脚本没有把本轮报告里的 reader/copy/order ID 写成常量。

一次显式提交会把选中副本实际借出。每轮应使用独立且此前可借的副本；测试失败或中断后，先读回隔离库状态，再决定是否重新准备夹具，不能盲目重跑同一副本。脚本不触碰共享 global 日结状态。

馆员和读者密码只保存在被忽略的本地 `.env.p3`，由单一 runner 在进程内加载；不放入 shell 参数。AI provider 凭据只由隔离后端配置，Playwright 配置文件不读取或传递模型密钥。

P3 专用配置关闭 trace、video 和 Playwright 自动截图；需要的页面截图均经过 `evidence/safe-shot.mjs`，值集包含运行时账号密码及本地模型配置秘密。没有 HAR 或 storageState。报告由 `p3-safe-summary.mjs` 生成，只含固定用例名、状态、耗时和白名单中的浏览器版本／实测视口／安全注释；不写错误消息、URL、原始 payload 或请求头。

Playwright 的失败诊断文件单独留在被忽略的 `e2e/test-results/p3/<run-id>/`，错误上下文可能包含填入值。该临时目录不自动脱敏，也不属于可交接报告；不要复制到 `artifacts/` 或发送给他人。需要留存时，应先在本机检查并按证据策略处理；否则在本地排障完成后删除。内置 trace 脱敏程序处理 trace，不会把普通错误上下文变成安全报告。

## 运行

Node 版本遵循 e2e package 的 `>=22.12.0 <25` 要求；依赖在 Windows 验证环境单独安装。先确认候选隔离栈与真实模型配置已准备就绪，再从仓库根目录执行：

```powershell
npm --prefix e2e run p3:regression:list
npm --prefix e2e run p3:fines
npm --prefix e2e run p3:ai-draft
npm --prefix e2e run p3:regression
```

`p3:regression:list` 先执行 7 项脚本协议自测，再发现用例，不读取凭据、不启动浏览器，在 WSL 可运行。正式命令由 `scripts/p3-runner.mjs` 载入本地 env 并在启动浏览器前检查 Windows 原生平台、账号、隔离夹具、日期、提交开关和证据根目录；缺条件时以退出码 2 标为 BLOCKED，不会静默 skip。直接调用 P3 Playwright config 也由 config 与 global setup 执行同等平台和配置守卫。P3_BASE_URL 不允许包含 URL 用户名、密码、query 或 fragment，避免秘密进入报告 URL。AI 模型不可用、角色拒绝失败、字段丢失、夹具状态不符，以及除前述 READER 权限拒绝明确允许的 HTTP 422 `MLS-AI-004` 外的任何非 2xx 响应都会使命令非零退出。

每次正式运行由 runner 生成一个 run ID 并传给 Playwright 子进程；重复加载配置时复用该 ID，safe summary 和截图写入同一个 `artifacts/m6-acceptance/p3-blockers/<run-id>/`，不覆盖旧轮。Playwright 临时失败诊断写入忽略的 `e2e/test-results/p3/<run-id>/`。本批只做脚本语法、测试发现和不启动浏览器的检查。以上正式命令须在 Windows 原生环境中、候选栈完成修复部署后执行；2026-09-30 准备轮尚无真实浏览器或模型通过结果；后续 Windows 与本轮修订结果见下节。

## 结果判读

安全摘要 JSON 与 safe-shot 图写入隔离的 `artifacts/m6-acceptance/p3-blockers/<run-id>/`。两项自动回归通过，只能说明相应脚本所列断言在本次候选栈、夹具、模型和视口下通过；不能覆盖本轮报告中的其他 P3 边界，也不改变既有失败记录。P3 整体结论仍按原 spec 及 Windows 原生 IAB 复验结果维护。

## 2026-10-01 已完成修订与实际证据

Windows 新候选报告已留证真实馆员草稿三字段、双实际视口预填、单笔业务借出和相邻权限／取消；罚款脚本 1/1 通过，AI 脚本在错误副本状态断言处失败，未证明唯一 POST。本轮已修正三处 API 状态断言和此前被遮住的草稿响应包装层级错误；具体根因、原红测和证据边界见本轮修订记录。

`npm --prefix e2e run p3:script-test` 不启动浏览器，实际执行发送 helper 到草稿选择 helper 的协议检查；7/7 通过。测试发现入口已自动执行该检查，再发现 2 个正式用例。本轮真实 PostgreSQL／HTTP 非空罚款隔离、Vue／实际 HTTP 适配器与本地接收端零／一次 POST 回归均已通过，后端207/207、前端223/223、契约／模块边界／lint通过。前端／后端运行源码未修改。

上述非浏览器覆盖不改写 Windows 脚本的历史失败，也不自动替代批准的 IAB。修订后 AI 浏览器脚本本轮未在 Windows 执行；是否接受本轮分层证据并跳过额外浏览器验收仍待用户裁定，P3 未登记通过。

## 修订记录

- 2026-10-01：修正 API／数据库副本状态用语，登记响应包装层级修复、协议自测准入和本轮分层证据；浏览器替代未获最终确认。

- 2026-09-30：新增两项 P3 失败的专用 Playwright runner、动态隔离夹具要求、真实模型角色链及显式一次提交边界；登记分层覆盖和正式验收边界。

## 被引用信息

- [M6 验收 harness](README.md)。
- [根交接记录](../HANDOFF.md)。
- [开发任务规划](../TODO.md)。
- [P3 执行记录](../docs/agents/delivery-closeout-20260925/results/P3.md)。

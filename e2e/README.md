# M6 验收 harness（e2e/）

> 性质：验收执行资产库，随仓库提交；提交哈希即版本。执行场所为 Env-W（Windows 原生 Docker Desktop）与 Env-P（WSL），执行口径见 `docs/agents/M6-ACCEPTANCE-PLAN-20260919.md`（本地文档，不入库）。
> 引用文档：
>
> - [系统验收条件与验收用例](../docs/01-requirements/acceptance/MLS-DOC-AT-0002.md)
> - [非功能需求](../docs/01-requirements/requirements/MLS-DOC-RA-0013.md)
> - [工程基础运行与验证](../ENGINEERING.md)

## 目录结构

| 路径 | 内容 |
| ---- | ---- |
| `playwright.config.js` | Playwright 配置；证据策略（无 HAR、不保存 storageState、截图经守卫）写在文件头注释 |
| `browsers/registry.json` | AT-0017 六浏览器矩阵登记表（版本、安装来源、隔离目录、可执行路径、自动更新策略、执行三态 `executionStatus`） |
| `browsers/verify-matrix.mjs` | 逐项验证 Playwright 可驱动；全部通过退出 0，有未登记项退出 2 |
| `ai-endpoint/` | 真实模型端点验证 S1~S6（自 `verification/ai-endpoint` 吸收，见下） |
| `evidence/safe-shot.mjs` | 截图守卫：页面内容与 URL 命中临时值集即拒绝写入 |
| `data/` | 版本化数据生成器（种子 20260910）与功能批次固定数据集渲染器 |
| `perf/` | k6 负载脚本与协议编排（RA-0013 §11 口径） |

## AI 端点验证来源与哈希登记

`e2e/ai-endpoint/` 两个文件按字节复制自不入库的 `verification/ai-endpoint/`（候选 e4eb4ee 时点），首次入库登记如下；此后 e2e 内副本独立演进，再次同步须更新本表。

| 文件 | SHA-256 |
| ---- | ---- |
| `verify-endpoint.mjs` | `c22437226093625bf1fabbb9d9928861645f1a1e74c907f88a09b3b9058162bd` |
| `verify-endpoint.test.mjs` | `10c4caa7700f637ad75ff4bbbe215b0651dc615b03ba6e744d7f8ee3bb60395e` |

用法不变：仓库根目录 `node --env-file=.env e2e/ai-endpoint/verify-endpoint.mjs`；自测 `node --test e2e/ai-endpoint/verify-endpoint.test.mjs` 无需凭据。URL 语义：`.env` 的 `BASE_URL` 是提供方基址（含 `/v1` 等真实前缀、不带尾斜杠），脚本自行拼接操作路径；后端 `AI_ENDPOINT_URL` 则是完整 `chat/completions` 地址，由 `scripts/prepare-acceptance-env.sh` 生成，两套语义不得混用。

执行口径：须以 `THINKING_MODE=disabled` 运行。后端线格式固定 `thinking.type=disabled`（`OpenAiCompatibleModelEndpoint`），2026-09-20 Env-W 端点实测：该配置下 S1~S6 全绿；provider 默认（请求无 thinking 字段）时强制 tool_choice 返回 400，S3 必败——那不是产品缺陷，是运行口径不同。

## 浏览器矩阵（AT-0017）

1. Env-W 准备六项安装：Chrome/Edge/Firefox 各当前稳定版与前一稳定大版本；前一稳定大版本安装到隔离目录并关闭自动更新（当前稳定版经 Playwright channel 驱动，前一版经 `executablePath` 指向隔离安装）。
2. 将版本号、安装来源、隔离 profile 目录、可执行路径、自动更新关闭策略逐项填入 `browsers/registry.json`（null 字段表示未登记，不得执行 AT-0017）。
3. `npm run browsers:verify` 验证可驱动性；结果落 `artifacts/m6-acceptance/browser-matrix/`。

### 登记三态（executionStatus）

`browsers/registry.json` 每个槽位带 `executionStatus` 三态标记，三态含义不同、不可互相替代：

| 取值 | 含义 | 判据 |
| ---- | ---- | ---- |
| `procedure-written` | 规程已编写：该槽位的执行线只有书面规程（Manual fallback 或安装登记路径），该执行线尚未实际执行 | `drivable` 为 `null`；或自动线实测不可驱动（`drivable: false` 已实测留档）且人工正式执行未做 |
| `drivability-verified` | 可驱动已验证：该槽位已完成 Playwright 可驱动性实测，实测记录落档 | `npm run browsers:verify` 实跑得出 `drivable` 与 `verifiedAt`，证据在 `artifacts/m6-acceptance/browser-matrix/` |
| `formal-execution-pending` | 正式矩阵执行完成前的矩阵级等待状态 | 登记于 registry `policy.executionStatus`，截至 2026-09-21 对全部六个槽位成立 |

三态两两不等：**规程已编写 ≠ 可驱动已验证 ≠ 正式矩阵执行完成**。书面规程存在不代表实测跑通过；可驱动性实测通过不代表 AT-0017 正式矩阵轮已执行。

当前六槽位状态（2026-09-21 登记）：

| 槽位 | executionStatus | 依据 |
| ---- | ---- | ---- |
| chrome-current | `drivability-verified` | `verify-2026-09-20T08-17-15-037Z.json`：drivable=true，2026-09-20T08:17:06.718Z |
| chrome-previous | `drivability-verified` | 同上：drivable=true，2026-09-20T08:17:07.687Z |
| edge-current | `drivability-verified` | 同上：drivable=true，2026-09-20T08:17:10.093Z |
| edge-previous | `procedure-written` | 版本 146.0.3856.109 已查证（Microsoft Learn 存档发行说明）；安装未执行、无可驱动性实测 |
| firefox-current | `procedure-written` | 自动线实测不可驱动（drivable=false，2026-09-20）；人工规程已编写、人工正式执行未做 |
| firefox-previous | `drivability-verified` | 同上 verify json：drivable=true，2026-09-20T08:17:15.037Z（Playwright 构建 v1543）；官方发行版人工线为 `procedure-written`，W2 待执行 |

W2 待执行项（截至 2026-09-21 均未执行）：edge-previous 真实安装（企业离线 MSI 146.0.3856.109，官方渠道可获得性待核实）、正式前一稳定 Firefox 官方发行版（155.0.1）安装、六槽位 AT-0017 正式矩阵轮。上述项完成前，任何槽位的标记都不表示「正式矩阵执行完成」。

edge-previous 已登记查证版本但 `executablePath` 仍为 null：`npm run browsers:verify` 对该槽位报 FAIL（退出码 1），直至 W2 安装完成并补齐登记。这是登记状态的真实反映，不是脚本缺陷。

### Manual fallback（书面人工执行规程）

某组合 Playwright 无法驱动时，按以下步骤人工执行并在登记表 `drivable` 填 `manual`：

1. 用该浏览器打开验收栈首页，按 AT-0002 §2.17 场景逐项手工操作（检索、登录、注册、借阅、续借、罚款、模拟支付）。
2. 分辨率按桌面 1280×720~2560×1440 与窄屏 375×667~430×932 的系统显示设置切换；键盘操作拔掉鼠标等效执行；焦点位置以截图记录。
3. 对比度用系统取色器或对比度工具对关键前景/背景取色，记录测量值。
4. 证据经 `safe-shot` 同等检查（肉眼核对无凭据）后存 `artifacts/m6-acceptance/at-0017/<browser-id>/`。

具名人工执行条目（按槽位；执行前把实际安装版本、来源、profile 目录补登记进 `browsers/registry.json` 对应槽位，执行后该槽位 `drivable` 填 `manual`）：

- **edge-previous（146 线，查证版本 146.0.3856.109）**：优先路径是 W2 安装企业离线 MSI（官方渠道可获得性待核实）后转自动驱动；安装不可得或自动驱动失败时，按上述步骤 1~4 人工执行，分辨率切换、纯键盘操作、对比度取色记录与 `safe-shot` 存证逐项留档，证据存 `artifacts/m6-acceptance/at-0017/edge-previous/`。
- **firefox-previous 官方发行版（155 线最终构建 155.0.1）**：与已验证可驱动的 Playwright 构建 v1543 是两个对象；官方安装包为 `Firefox Setup 155.0.1.exe` / `Firefox Setup 155.0.1.msi`（archive.mozilla.org 官方发行存档）。按上述步骤 1~4 人工执行，证据存 `artifacts/m6-acceptance/at-0017/firefox-previous-official/`。

## 首次安装与锁定

`npm install` 在执行场所（Env-W/Env-P）首次执行生成 `package-lock.json`，随提交锁定；此后升级依赖属 harness 变更，须登记并复跑受影响用例。`playwright install` 仅安装 Playwright 自带浏览器用于配置自检，验收矩阵以 `browsers/registry.json` 登记的六个安装为准。

## 证据边界

- 本目录产生的报告、trace、截图、数据集输出一律写 `artifacts/m6-acceptance/`（不入库），交付前经敏感证据扫描（含压缩包深扫）。
- 不采集 HAR、不保存 storageState、不在命令行参数携带凭据（与 `scripts/check-sensitive-evidence.sh` 第一控制一致）。

## 修订记录

- 2026-09-19：初建；ai-endpoint 自 verification/ai-endpoint 字节级吸收并登记哈希。
- 2026-09-20：登记端点验证执行口径（THINKING_MODE=disabled，与应用线格式一致）；scenarios 目录随 E2E-001/E2E-AI-001 纳入。
- 2026-09-21：登记表回填四槽位可驱动性实测结果（verify-2026-09-20T08-17-15-037Z.json）；edge-previous 登记查证版本 146.0.3856.109（Microsoft Learn 存档发行说明）并注明官方安装包可获得性待核实；firefox-previous 注明 Playwright 构建 v1543 非官方发行版、官方 155.0.1 列为人工规程项；新增三态标记 `executionStatus` 与 W2 待执行项登记；Manual fallback 增补 edge-previous 与 firefox-previous 官方发行版具名条目。

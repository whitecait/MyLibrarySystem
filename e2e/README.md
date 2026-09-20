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
| `browsers/registry.json` | AT-0017 六浏览器矩阵登记表（版本、安装来源、隔离目录、可执行路径、自动更新策略） |
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

### Manual fallback（书面人工执行规程）

某组合 Playwright 无法驱动时，按以下步骤人工执行并在登记表 `drivable` 填 `manual`：

1. 用该浏览器打开验收栈首页，按 AT-0002 §2.17 场景逐项手工操作（检索、登录、注册、借阅、续借、罚款、模拟支付）。
2. 分辨率按桌面 1280×720~2560×1440 与窄屏 375×667~430×932 的系统显示设置切换；键盘操作拔掉鼠标等效执行；焦点位置以截图记录。
3. 对比度用系统取色器或对比度工具对关键前景/背景取色，记录测量值。
4. 证据经 `safe-shot` 同等检查（肉眼核对无凭据）后存 `artifacts/m6-acceptance/at-0017/<browser-id>/`。

## 首次安装与锁定

`npm install` 在执行场所（Env-W/Env-P）首次执行生成 `package-lock.json`，随提交锁定；此后升级依赖属 harness 变更，须登记并复跑受影响用例。`playwright install` 仅安装 Playwright 自带浏览器用于配置自检，验收矩阵以 `browsers/registry.json` 登记的六个安装为准。

## 证据边界

- 本目录产生的报告、trace、截图、数据集输出一律写 `artifacts/m6-acceptance/`（不入库），交付前经敏感证据扫描（含压缩包深扫）。
- 不采集 HAR、不保存 storageState、不在命令行参数携带凭据（与 `scripts/check-sensitive-evidence.sh` 第一控制一致）。

## 修订记录

- 2026-09-19：初建；ai-endpoint 自 verification/ai-endpoint 字节级吸收并登记哈希。
- 2026-09-20：登记端点验证执行口径（THINKING_MODE=disabled，与应用线格式一致）；scenarios 目录随 E2E-001/E2E-AI-001 纳入。

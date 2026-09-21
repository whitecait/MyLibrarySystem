# e2e trace 证据边界（EVIDENCE-POLICY）

> 性质：M6 验收 harness 的 trace 证据边界口径，随仓库提交；提交哈希即版本。
> 引用文档：
>
> - [M6 验收 harness（e2e/）](README.md)
> - [工程基础运行与验证](../ENGINEERING.md)
> - `docs/agents/M6-ACCEPTANCE-PLAN-20260919.md`（本地文档，不入库）——验收计划对证据处理的第 2.7 项要求

## 第一控制口径

运行时 Cookie、会话令牌、Bearer 凭据与模型 API key 不进入长期证据。第一控制由三层已实现机制承担，敏感值扫描只是兜底：

1. 采集面收敛：不采集 HAR、不保存 storageState、trace 仅 `retain-on-failure`（`playwright.config.js`）。
2. 交付前处理：trace 在离开会话工作区之前，经 `scripts/sanitize-evidence-traces.sh` 销毁或脱敏（见下）。
3. 兜底扫描：交付树整体经 `scripts/check-sensitive-evidence.sh`（散文件值集）与 `scripts/check-sensitive-evidence-deep.sh`（解包 zip 深扫）复核；值集由 `scripts/collect-evidence-secret-values.sh` 从 `.env` 提取，即用即删。

## trace 的采集

- `e2e/playwright.config.js` 设 `trace: 'retain-on-failure'`：仅失败用例产生 trace，产物为 `outputDir`（默认 `test-results`，可用 `E2E_OUTPUT_DIR` 改指）下用例目录内的 `trace.zip`；旧版 Playwright 形态为名为 `trace` 的目录。
- `screenshot: 'off'`、`video: 'off'`；作为证据的截图经 `e2e/evidence/safe-shot.mjs` 守卫逐张拍摄（页面内容或 URL 命中临时值集即拒绝写入）。

## 临时保留

trace 只存在于验收会话工作区的 `test-results` 目录：不入库（不入 git），不直接拷贝进 `artifacts/m6-acceptance/`。会话结束即进入交付前处理，无长期保留状态。

## 交付前处理（sanitize）

`scripts/sanitize-evidence-traces.sh <test-results 目录>`，三种模式：

| 模式 | 行为 | 退出码 |
| ---- | ---- | ---- |
| 默认（销毁） | 删除目录内全部 `trace.zip` 与名为 `trace` 的目录，逐条输出销毁清单；`error-context.md`、`report.json` 等非 trace 文件不触碰 | 0 |
| `--keep-redacted` | 逐个解包 `trace.zip`，按内置动态凭据规则脱敏后原位重新打包，保持条目结构与 JSON 行可读；写回前复核解包树，仍有命中即放弃写回；二进制资源命中凭据模式时整体退出 1（此时改用默认销毁） | 0；命中未清除时 1 |
| `--verify` | 逐文件探测「若按内置规则脱敏是否会变化」，命中即列出文件，不修改任何文件 | 命中 1；干净 0 |

约束与限制：

- 脚本拒绝路径含 `artifacts` 段的目标：交付后材料不再就地处理，sanitize 必须发生在拷入 `artifacts/m6-acceptance/` 之前。
- `--keep-redacted` 可附加 SECRET_FILE（每行一个明文值，`collect-evidence-secret-values.sh` 生成），作为内置规则的补充而非替代。
- 二进制资源（截图、压缩体）不做就地脱敏（字节级替换会破坏其内部结构），仅做高信号值形态子集扫描；`--keep-redacted` 对二进制的核验属兜底而非完整证明。
- 脱敏按正则逐段替换，值内含转义引号的极端场景可能破坏该行 JSON 可解析性；保留的 trace 交付前应人工抽查。
- 本仓执行环境无 `zip`/`unzip` 命令，脚本经 `python3` 的 `zipfile` 解包与重打包。

## 销毁边界

进入 `artifacts/m6-acceptance/` 的材料中不得存在原始 trace：每个 `trace.zip` 要么已在交付前销毁（默认路径），要么是 `--keep-redacted` 脱敏重打包后的版本。销毁清单是过程证据，随核验记录登记（如 `artifacts/m6-acceptance/phase-w1/r07-trace/`）。

## 动态凭据规则口径

`scripts/sanitize-evidence-traces.sh` 的脱敏与核验规则内置且不依赖 `.env` 值，覆盖：

- `Cookie` / `Set-Cookie` / `Authorization` / `Proxy-Authorization` 头，含 Playwright trace 的 JSON `name`/`value` 对与纯文本行两种形态；
- `Bearer` 方案令牌；JWT（`eyJ` 开头三段式）形态；`sk-` 前缀 API key 形态；
- 会话令牌键值对形态：`sessionid=`、`session_id=`、`token=`、`access_token=`、`csrf_token=` 等（覆盖 Cookie 对与 URL 查询串）；
- JSON 字符串字段键名含 `token`/`secret`/`password`/`credential`/`api_key`/`authorization`/`cookie` 的值；
- 本系统生产会话令牌形态：32 位小写字母数字随机串（`library-server/arc/src/main/java/com/service/impl/TokenServiceImpl.java` 的 `getRandomString(32)`，字符表见 `com/utils/CommonUtil.java`），按 `\b[a-z0-9]{32}\b` 全量匹配，宁可过度脱敏不放过漏网。

## 修订记录

- 2026-09-21：初建。登记 trace 的采集、临时保留、交付前 sanitize 处理、销毁边界与第一控制口径；对应 `scripts/sanitize-evidence-traces.sh`（新增）与 `playwright.config.js` 头注释（更新为实际机制）。

## 被引用信息

- 截至初建，暂无其他文档引用本文件；`e2e/playwright.config.js` 与 `scripts/sanitize-evidence-traces.sh` 的文件头注释指向本文件。

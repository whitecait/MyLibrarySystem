# library-frontend — Vue 3 工程骨架

> 引用文档：
>
> - [项目入口](../README.md)
> - [工程基础运行与验证](../ENGINEERING.md)
> - [工程文档唯一入口](../docs/README.md)
> - [B1-A/B1-B 工程基础行动计划](../HANDOFF-B1-PLAN.md)

本目录当前只包含 Vue 3、Vite 和 Vitest 的最小工程状态页。旧业务页面、路由和 API 已从构建输入中移除；业务功能尚未实现，当前构建和测试通过不表示需求验收通过。

## 工具版本

- Node.js `>=22.12.0 <23`；下限来自当前 Vite/Vitest 的运行要求，本地验证版本为 22.23.2。
- npm 10.9.8，由 `packageManager` 字段固定。
- 依赖版本由 `package-lock.json` 固定，首次安装使用 `npm ci`。

## 正式工程检查

在本目录执行：

```bash
npm ci
npm run lint:check
npm run test:ci
npm run build
```

`lint:check` 不修改文件、不写 ESLint 缓存；`test:ci` 只发现 `src/**/*.test.js`，不会把本地原型当作正式 CI 输入。开发服务器使用：

```bash
npm run dev
```

默认入口为 `http://localhost:5173`。

## 本地原型

`prototypes/` 是被 Git 忽略的本地演示资产，不是正式设计或 CI 输入。它们继续复用 Vue Router、Element Plus 和图标库等依赖：

```bash
npm run demo:ai
npm run demo:agent-loop
npm run demo:overlays
npx vitest run --config prototypes/tech-verification/vitest.config.js prototypes/tech-verification/click-check.test.mjs
node prototypes/tech-verification/agent-sim.mjs
```

`npm run dev` 只启动正式工程状态页；AI 原型的 history 路由必须通过 `npm run demo:ai` 的专用 Vite 配置启动。Agent Loop 与 Overlay 使用各自列出的普通原型入口。

## 容器与完整验证

容器启动、前端 `/api` 代理、两轮空库与保留卷验收、CI 和证据位置见[工程基础运行与验证](../ENGINEERING.md)。

## 修订记录

| 日期 | 内容 |
| --- | --- |
| 2026-09-09 | 增加容器与完整验证入口，关联 P4/P5 配置和证据说明。 |
| 2026-09-09 | P2 遗漏修订：Node 支持范围由过宽的 `22.x` 收紧为 `>=22.12.0 <23`；补充 AI 原型必须使用专用启动命令的入口边界。 |
| 2026-09-09 | P2 将正式工程收敛为单一状态页，新增非修改式 lint、Vitest DOM 挂载测试和明确的正式测试发现范围；旧业务页面/API/路由移除，原型保持独立。 |
| 2026-09-09 | AI Demo 审计返修及点击级验证完成；具体原型证据保留在 `prototypes/tech-verification/`。 |
| 2026-09-07 | 登记 AI 助手 Demo 启动入口。 |

---

> 被引用文档：
>
> - [项目入口](../README.md)
> - [工程基础运行与验证](../ENGINEERING.md)

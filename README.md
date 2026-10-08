# MyLibrarySystem

引用文档：

- [现代书目与阅读演示追加记录](artifacts/modern-demo-import-20261008/REPORT.md)
- [日结自动恢复与本地运行更新](artifacts/closing-recovery-20261007/REPORT.md)
- [当前交接记录](HANDOFF.md)
- [两日冲刺待办](TODO.md)
- [运行与验证说明](ENGINEERING.md)
- [工程文档入口](docs/README.md)
- [前端说明](library-frontend/README.md)
- [后端说明](library-server/README.md)
- [当前页面验证记录](artifacts/frontend-backdrop-close-20261007/REPORT.md)
- [完整分类与随机图书修订](artifacts/frontend-catalog-discovery-20261007/REPORT.md)
- [全文书籍语料](data/book-corpus/README.md)
- [Issue #8](<https://github.com/whitecait/Mylibrary/issues/8>)
- [PR #9](<https://github.com/whitecait/Mylibrary/pull/9>)
- [推送 CI（五项已通过）](<https://github.com/whitecait/Mylibrary/actions/runs/37733077742>)
- [PR CI（五项已通过）](<https://github.com/whitecait/Mylibrary/actions/runs/37733313686>)
- [PR 合并提交 39c246b](<https://github.com/whitecait/Mylibrary/commit/39c246bc1773610a7a941dd2dc47d70e98856c75>)
- [合并后 main CI（五项已通过）](<https://github.com/whitecait/Mylibrary/actions/runs/37766613941>)
- [合并后 main CI 摘要](<artifacts/closing-recovery-20261007/evidence/remote-main-37766613941/verification-summary.md>)

## 当前阶段

2026-10-07，用户确认项目进入**实机部署的页面微调阶段**。两日冲刺的目标是确定一个**可部署在阿里云 ECS 云服务器上的可发布版本**。该目标已确认，发布候选与 ECS 部署验证尚未完成。

当前页面采用 A 版背景页与事务模态。首页提供连续书目信息流。详情与共享 AI 助手在桌面按 2∶1 显示。顶部保留首页与个人信息，角色工作入口位于个人信息菜单。遮罩及面板间空白可以关闭模态。

首页分类与实际可借状态采用完整书库加载的陈列按钮，点击后立即筛选。快捷区域随机展示三本现有图书，并链接到详情。

最近一次页面修订记录为 47 个测试文件、304 项前端测试通过，契约、架构、ESLint 与 HTTP 模式构建通过。上述结果对应本次首页修订。实机视觉与操作结果按实际执行另行登记。

当前前端修改仍在未提交工作树中。现有 Compose 入口绑定本机环回地址；ECS 的访问入口、运行配置和同版启动验证列在冲刺待办中。当前工作区不等同于已封存的发布候选。

本地预览后端仍运行固定闭馆恢复提交 `19b77e70a46e2c30c342eb7bfe8f375d59285d2b` 的镜像；其文件内容与远程 main merge commit [`39c246bc1773610a7a941dd2dc47d70e98856c75`](https://github.com/whitecait/Mylibrary/commit/39c246bc1773610a7a941dd2dc47d70e98856c75) 一致，合并后未重新部署。PR [#9](https://github.com/whitecait/Mylibrary/pull/9) 已合并，Issue [#8](https://github.com/whitecait/Mylibrary/issues/8) 已关闭；推送、PR 与合并后 main CI 五项作业均通过。本地 `main` HEAD 仍为基线 `3a9f142f5427b950b211d8c7607660cda3a5c3b5`，`origin/main` 已刷新到 `39c246bc1773610a7a941dd2dc47d70e98856c75`；未提交页面修改仍保留且未推送。ECS 尚未更新，Windows 原生页面复核仍待用户执行。详见[日结恢复报告](artifacts/closing-recovery-20261007/REPORT.md)。

当前预览库包含 2,001 册书目：996 册 Gutenberg 全文、1,000 条现代开放获取元数据及 5 册阅读演示图书。5 册演示图书提供 PDF 提取文字的分页阅读。追加与备份验证已完成。

## 项目定位

本项目面向单一图书馆，提供书目管理、预约、借阅、归还、罚款及相关管理功能。内嵌 AI 助手共用业务数据、规则和权限，并通过现有表单办理获准动作。

项目以真实 ERP 的 AI 对话助手为学习参照。重点是让对话助手遵守业务规则、权限和事务边界。

## 工作入口

| 事项 | 入口 |
| --- | --- |
| 当前实现、验证边界和交接 | [HANDOFF.md](HANDOFF.md) |
| 两日冲刺与发布完成条件 | [TODO.md](TODO.md) |
| 本地启动、构建及验证命令 | [ENGINEERING.md](ENGINEERING.md) |
| 已登记的需求、设计与批准 | [docs/README.md](docs/README.md) |
| 千册全文语料与导入约定 | [book-corpus README](data/book-corpus/README.md) |

旧计划、历史验收报告、快照和旧交接材料已移出工作区。清理记录与本地恢复位置见 HANDOFF.md。原型、当前页面记录、全文语料和运行工具仍保留。

## 修订记录

- 2026-10-08：登记 1,000 条现代书目与 5 册演示图书追加、ISBN 清洗、2,723 页 PDF 文字阅读、备份恢复及当前预览验证。
- 2026-10-07：登记本地后端闭馆自动恢复、启动补结、再次重启不重复结算及仍未完成的 ECS／远端验证。
- 2026-10-07：根据用户明确授权，登记修复分支已推送、Issue #8 已创建、PR #9 开放及两轮 CI 全部通过；页面修改仍未推送。
- 2026-10-07：按用户确认的当前阶段精简项目入口，移出历史批次状态，登记阿里云 ECS 两日冲刺目标。
- 2026-10-07：登记已完成的完整分类与状态筛选按钮、随机图书入口和最新前端验证结果。
- 2026-10-08：登记 PR #9 合并、Issue #8 关闭、合并后 main CI 通过；origin/main 已刷新，本地 HEAD 与未提交页面内容保持不变。
被引用文档：

- [日结自动恢复与本地运行更新](artifacts/closing-recovery-20261007/REPORT.md)
- [完整分类与随机图书修订](artifacts/frontend-catalog-discovery-20261007/REPORT.md)
- [ENGINEERING.md](ENGINEERING.md)
- [HANDOFF.md](HANDOFF.md)
- [TODO.md](TODO.md)
- [artifacts/frontend-a-implementation-20261007/REPORT.md](artifacts/frontend-a-implementation-20261007/REPORT.md)
- [artifacts/frontend-backdrop-close-20261007/REPORT.md](artifacts/frontend-backdrop-close-20261007/REPORT.md)
- [artifacts/frontend-shared-modal-20261007/REPORT.md](artifacts/frontend-shared-modal-20261007/REPORT.md)
- [artifacts/frontend-single-entry-20261007/REPORT.md](artifacts/frontend-single-entry-20261007/REPORT.md)
- [docs/00-governance/MLS-DOC-GOV-0004.md](docs/00-governance/MLS-DOC-GOV-0004.md)
- [docs/00-governance/MLS-DOC-GOV-0007.md](docs/00-governance/MLS-DOC-GOV-0007.md)
- [docs/00-governance/MLS-DOC-GOV-0008.md](docs/00-governance/MLS-DOC-GOV-0008.md)
- [docs/01-requirements/requirements/MLS-DOC-RA-0003.md](docs/01-requirements/requirements/MLS-DOC-RA-0003.md)
- [docs/01-requirements/requirements/MLS-DOC-RA-0007.md](docs/01-requirements/requirements/MLS-DOC-RA-0007.md)
- [docs/01-requirements/reviews/MLS-DOC-REV-0001.md](docs/01-requirements/reviews/MLS-DOC-REV-0001.md)
- [docs/01-requirements/reviews/MLS-DOC-REV-0002.md](docs/01-requirements/reviews/MLS-DOC-REV-0002.md)
- [docs/README.md](docs/README.md)
- [library-frontend/README.md](library-frontend/README.md)
- [library-server/README.md](library-server/README.md)

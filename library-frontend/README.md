# library-frontend - Vue 3 SPA

本目录保存 MyLibrarySystem 前端候选实现。项目正在按照 V 模型从需求分析阶段重新建立基线，[`docs/README.md`](../docs/README.md) 是工程文档唯一入口；[`REQUIREMENTS-STAGE-DESIGN.md v1.0`](../docs/01-requirements/REQUIREMENTS-STAGE-DESIGN.md) 只是已批准的阶段组织方案，不是最终需求基线。

当前需求基线为 `Not established`，验收尚未执行。本目录中的工程骨架、页面和交互逻辑只可作为直接复用、重构复用或弃用重写的候选，不能作为新需求来源，也不能被解释为当前需求契约。

## 当前候选实现的技术与开发说明

| 关注点 | 当前候选实现采用的工具 |
| --- | --- |
| 框架 | Vue 3 |
| 构建 | Vite |
| UI | Element Plus |
| HTTP | Axios，通过 `src/utils/request.js` 统一处理 |
| 路由 | Vue Router |

安装依赖并启动本地开发环境：

```powershell
cd library-frontend
npm install
npm run dev
```

当前候选实现的本地开发地址为 `http://localhost:5173`。构建检查：

```powershell
cd library-frontend
npm run build
```

以上命令只描述现有工程骨架的开发方式，不批准未来技术选型、接口形式或部署方案。

## 历史实现风险

- 多个业务 API 模块和页面仍依赖返工前的旧拼音 API、旧字段或旧响应形态；这些业务层实现原则上弃用重写，不得作为当前需求契约。
- 认证包装、登录注册界面、罚款流程及返工前扩展功能之间存在不一致，不能以局部迁移状态推定完整系统行为。
- 代理、令牌传输、页面结构和样式资产均只是复用候选，必须在需求基线和后续设计批准后重新评估。
- 精确资产、风险与后续验证要求以[代码复用候选台账](../docs/01-requirements/evidence/CODE-REUSE-INVENTORY.md)为证据入口；该台账本身也不构成设计批准。

## 后续开发约束

- 在需求基线建立前，不以旧接口、旧字段或旧页面行为补写需求。
- 在总体设计、详细设计和模块设计完成相应门禁前，不把候选实现结构固化为新系统设计。
- 新测试必须归入对应 V 模型验证层级；旧测试或构建结果不能登记为完整系统验收证据。

## 工程文档入口

- [工程文档唯一入口](../docs/README.md)
- [系统需求主规格](../docs/01-requirements/SYSTEM-REQUIREMENTS.md)
- [代码复用候选台账](../docs/01-requirements/evidence/CODE-REUSE-INVENTORY.md)
- [系统验收执行报告登记册](../docs/01-requirements/acceptance/ACCEPTANCE-REPORTS.md)（当前尚未执行）
- [历史资料归档](../docs/90-archive/README.md)

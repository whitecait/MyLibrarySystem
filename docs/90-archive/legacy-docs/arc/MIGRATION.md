> **Archive Status:** Historical Reference
> **Original Path:** `docs/arc/MIGRATION.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 返工前契约或实现记录
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约

# Migration Guide

> 本文档指导从旧项目 `VueClassWork` 和 `JavaClassWork` 向 `MyLibrarySystem` 的文件迁移。

## 迁移总览

```
VueClassWork/library-frontend/    -->  MyLibrarySystem/library-frontend/
JavaClassWork/Server/             -->  MyLibrarySystem/library-server/
VueClassWork/*.md                 -->  MyLibrarySystem/docs/
```

## Step 1: 迁移前端 (Vue 3)

**源目录:** `Projects/VueClassWork/library-frontend/`
**目标目录:** `Projects/MyLibrarySystem/library-frontend/`

### 需要复制的内容

| 源路径             | 说明                                    |
| ------------------ | --------------------------------------- |
| `src/`             | Vue 3 源码（16 个视图、12 个 API 模块） |
| `public/`          | 静态资源                                |
| `index.html`       | 入口 HTML                               |
| `package.json`     | 依赖配置                                |
| `vite.config.js`   | Vite 构建配置                           |
| `eslint.config.js` | ESLint 配置                             |
| `.prettierrc.json` | Prettier 配置                           |
| `jsconfig.json`    | JS 路径别名配置                         |
| `.editorconfig`    | 编辑器配置                              |

### 不需要复制

- `node_modules/` — `npm install` 重新安装
- `.git/` — 新仓库已独立初始化

## Step 2: 迁移后端 (Spring Boot)

**源目录:** `Projects/JavaClassWork/Server/springboot8393c/`
**目标目录:** `Projects/MyLibrarySystem/library-server/`

### 需要复制的内容

| 源路径              | 说明               |
| ------------------- | ------------------ |
| `src/`              | Java 源码          |
| `pom.xml`           | Maven 依赖配置     |
| `.mvn/`             | Maven Wrapper      |
| `mvnw` / `mvnw.cmd` | Maven Wrapper 脚本 |

### 不需要复制

- `target/` — `mvn compile` 重新构建
- `废弃/` — 已废弃资源

## Step 3: 迁移文档

| 源                                          | 目标                       |
| ------------------------------------------- | -------------------------- |
| `Projects/VueClassWork/API.ReadMe.md`       | `docs/API.ReadMe.md`       |
| `Projects/VueClassWork/Entity.md`           | `docs/Entity.md`           |
| `Projects/VueClassWork/FRONTEND_MODULES.md` | `docs/FRONTEND_MODULES.md` |
| `Projects/VueClassWork/测试用例.md`         | `docs/测试用例.md`         |

## Step 4: 废弃标记

迁移完成后，在旧项目根目录放置废弃说明：

- `Projects/VueClassWork/DEPRECATED.md`
- `Projects/JavaClassWork/DEPRECATED.md`

内容模板见下方。

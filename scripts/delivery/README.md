# 容器交付包与本机验证

## 引用文档

- [内部接口与实现对应](../../docs/04-module-design/MLS-DOC-MD-0004.md)。

- [当前限定范围与运行责任](../../docs/00-governance/MLS-DOC-GOV-0008.md)。
- [验收适用清单](../../docs/01-requirements/acceptance/MLS-DOC-AT-0002.md)。
- [形式化边界与门禁](../../formal/README.md)。
- [可执行运维入口](manage.py)。
- [干净提交打包入口](package.py)。

本说明随交付包复制到根目录，源码及上述工程文档位于包内 source/。运行依赖为 Windows 原生 Python 3.12、Docker Desktop Linux 容器与 Compose；页面验证在 Windows 原生 Chrome 执行。包不依赖 WSL 的 node_modules 或 Maven 缓存。

## 当前部署目标

2026-10-07，用户确认项目进入实机部署的页面微调阶段，目标为阿里云 ECS 云服务器上的可发布版本。本文件描述已实现的打包及本机运维入口。现有配置绑定环回地址；ECS 访问入口与同版启动尚未验证，待办由根 TODO.md 维护。

## 打包与身份

从确切提交的干净检出执行 python3 scripts/delivery/package.py --commit 完整提交SHA --output-dir 仓库内忽略的产物目录。入口拒绝未提交差异、错误提交和已存在包目录；从 git archive 获取源码，固定基础镜像构建前后端，记录镜像 ID／提交标签并导出镜像。生成包的 release.json、SHA256SUMS 和包外 seal.json 分别记录提交、文件及压缩包身份。构建与校验结果由发布候选记录；CI 和目标机验证需分别执行。

## 标准容器下载受限时的源码构建

本地容器 Maven Wrapper 下载已有 TLS 握手失败实证；标准容器方式仍为默认。--backend-build host 是显式替代入口：要求 JDK 21，从同一 git archive 源码在宿主执行 Maven 编译，再将新生成 JAR 复制进固定 JRE 镜像，从选定 JAVA_HOME 固定编译器与 Maven 环境，并核对及记录 Maven 的实际 Java 版本、编译器版本、JAR SHA-256、源码提交及镜像身份。它不接收任意外部 JAR；生成 target/ 不进入源码包。依赖仍可能需要网络，不声明冷缓存离线重建。Windows 执行机运行包内镜像时无需安装 JDK 或 Maven。

## 原生 Windows 操作

在已解压包目录使用 PowerShell。先从独立取得的 seal.json 核对压缩包 SHA-256，再执行包内文件校验。私有模型配置由本机维护，不使用共享默认口令。

```powershell
python bin/manage.py verify
python bin/manage.py load-images
python bin/manage.py --project mls-p5-local init --port 8088
# 在本机私下配置 config/runtime.env 的模型端点、模型名和密钥。
python bin/manage.py --project mls-p5-local start
python bin/manage.py --project mls-p5-local bootstrap --username librarian --role LIBRARIAN
python bin/manage.py --project mls-p5-local bootstrap --username administrator --role ADMIN
python bin/manage.py --project mls-p5-local restart
```

bin/windows.ps1 是同参数的原生 Windows 包装入口，非 Windows 环境直接拒绝。manage.py 的停写、导出及恢复采用 Python 子进程的字节流，避免 PowerShell 对 SQL 重定向转码。init 独占创建配置，拒绝覆盖；Compose 以显式私有文件中的键为输入，屏蔽同名 shell 导出覆盖，bootstrap 通过口令提示输入认证材料，通过 stdin 写库，口令不进入进程参数或公开结果。Windows 上私有环境及备份目录的访问权限仍须由本机用户核对，不以 POSIX 文件模式宣称 Windows ACL 已验证。

入口为 http://127.0.0.1:8088；数据库不发布宿主端口。日结默认 Asia/Shanghai 午夜，成功后保持隔离，由管理员显式重开。模拟支付无真实资金；退款、对账等移出能力和未完成验收以适用清单为准。

## 备份与隔离恢复

```powershell
python bin/manage.py --project mls-p5-local backup C:/private/library-backup.sql
python bin/manage.py --project mls-p5-local start
python bin/manage.py --env-file C:/private/restore.env --project mls-p5-restore init --port 8089
python bin/manage.py --project mls-p5-local restore C:/private/library-backup.sql --target-env C:/private/restore.env --target-project mls-p5-restore
```

备份拒绝覆盖已有文件，停写源前端／后端后导出；结束时源应用仍停止，须显式 start 恢复。失败或空文件不能作为有效备份；restore 在创建资源前拒绝零字节 SQL。恢复要求不同项目和不同配置，拒绝已存在容器或卷的目标；先启动新库，psql 严格导入成功后才启动后端及前端。命令不删除旧卷。stop 仅停止所选项目并保留数据。

恢复后需核对原有数据、登录与核心写入并检查日志。新包构建不会自动完成这些实际测试；新迁移不能假定可随旧镜像回退。

## 修订记录

- 2026-10-02：增量复审修正 host 入口局部导入错误、绑定同一 JDK／Maven 身份并加入错配拒绝验证；包根目录不链接未随包提供的本地审计报告。

- 2026-10-02：独立审计整改增加空备份拒绝；为已有容器下载限制提供同提交源码宿主编译方式，非任意 JAR 注入。实际包结果另行登记。

- 2026-10-01：建立可追踪打包和 Windows 原生运维入口；本说明描述已实现命令，不预填 Windows 操作、发布或 P5 结果。

- 2026-10-07：按用户确认的整理范围撤下旧计划、历史报告和交接材料链接。正文保留必要的历史来源标识；已登记的需求、设计与验收结论保持。

## 被引用信息

- [工程验证入口](../../ENGINEERING.md)。

- [当前形式化映射](../../formal/README.md)。

> **Archive Status:** Historical Reference
> **Original Path:** `docs/arc/API.ReadMe.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 返工前契约或实现记录
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约

后端采用Spring Boot服务器提供支持。各个控制器的接口API参考文档如下：
---

UsersController.java - 用户管理
基础路径: /users
提供用户（管理员）的认证与CRUD功能，核心接口包括：
用户登录: /users/login (GET, POST) - 通过 username 和 password 验证身份，成功后返回 token（@IgnoreAuth）。
用户注册: /users/register (POST) - 接收 UsersEntity 创建新用户，用户名冲突时返回错误（@IgnoreAuth）。
退出登录: /users/logout (GET) - 使当前用户会话失效。
密码重置: /users/resetPass (GET, POST) - 根据 username 重置密码为 "123456"（@IgnoreAuth）。
分页查询用户: /users/page (GET, POST) - 支持分页和模糊查询。
查询用户列表: /users/list (GET, POST) - 按 UsersEntity 条件查询用户列表。
获取用户信息: /users/info/{id} (GET, POST) - 通过用户ID获取详细信息。
获取当前会话用户: /users/session (GET, POST) - 从会话中获取当前登录用户信息。
保存用户: /users/save (POST) - 新增用户（需身份验证）。
修改用户信息: /users/update (GET, POST) - 更新用户信息，检查用户名冲突。
删除用户: /users/delete (GET, POST) - 批量删除用户（请求体为 Long[]）。

---

TushuxujieController.java - 图书续借
基础路径: /tushuxujie
管理图书续借申请的审核与查询，区分前后端访问权限：
后端分页列表: /tushuxujie/page - 获取续借申请分页列表，读者仅查看自身记录。
前端列表查询: /tushuxujie/list - 公开展示续借信息（@IgnoreAuth）。
列表查询（不分页）: /tushuxujie/lists - 按精确条件查询所有续借记录。
查询单个续借: /tushuxujie/query - 返回 TushuxujieView 详细视图对象。
后端详情: /tushuxujie/info/{id} - 获取续借记录详情（后台管理）。
前端详情: /tushuxujie/detail/{id} - 公开展示续借详情（@IgnoreAuth）。
保存续借申请（后端）: /tushuxujie/save - 保存 TushuxujieEntity 到数据库。
新增续借申请（前端）: /tushuxujie/add - 功能同 /save，供前端调用。
修改续借信息: /tushuxujie/update - 更新续借记录。
批量审核: /tushuxujie/shBatch - 批量处理审核（需ID数组、状态 sfsh 和回复 shhf）。
删除续借记录: /tushuxujie/delete - 批量删除记录（请求体为ID数组）。

---

TushuxinxiController.java - 图书信息
基础路径: /tushuxinxi
提供图书信息的CRUD与查询视图，支持匿名访问：
后端分页列表: /tushuxinxi/page - 按条件分页、模糊/区间查询并排序。
前端分页列表: /tushuxinxi/list - 公开展示分页查询（@IgnoreAuth）。
列表（不分页）: /tushuxinxi/lists - 按精确条件返回全部视图列表。
查询视图（单个）: /tushuxinxi/query - 返回 TushuxinxiView 单个视图对象。
后端详情: /tushuxinxi/info/{id} - 根据ID获取实体详情。
前端详情: /tushuxinxi/detail/{id} - 公开展示详情（@IgnoreAuth）。
后端保存: /tushuxinxi/save - 后台新增图书信息。
前端保存: /tushuxinxi/add - 前端新增图书信息。
修改: /tushuxinxi/update - 按主键全字段更新。
删除: /tushuxinxi/delete - 批量删除（请求体为 Long[]）。

---

TushuleibieController.java - 图书类别
基础路径: /tushuleibie
管理图书分类的完整CRUD，区分登录与匿名访问：
分页列表（后端）: /tushuleibie/page - 支持模糊查询和分页（需登录）。
分页列表（前端）: /tushuleibie/list - 公开展示（@IgnoreAuth）。
获取详情（后端）: /tushuleibie/info/{id} - 根据ID获取详情（需登录）。
获取详情（前端）: /tushuleibie/detail/{id} - 公开展示（@IgnoreAuth）。
新增类别（后端）: /tushuleibie/save - 请求体为JSON对象（需登录）。
新增类别（前端）: /tushuleibie/add - 功能同 /save，供前端调用。
修改类别: /tushuleibie/update - 请求体为含ID的JSON对象。
删除类别: /tushuleibie/delete - 批量删除（请求体为ID数组）。

---

MessagesController.java - 在线留言
基础路径: /messages
管理留言的增删改查，用户仅查看自身留言：
分页列表（后端）: /messages/page - 管理员查看全部，用户查看自身。
分页列表（前端）: /messages/list - 公开展示（@IgnoreAuth）。
获取详情（后端）: /messages/info/{id} - 后台管理使用。
获取详情（前端）: /messages/detail/{id} - 公开展示（@IgnoreAuth）。
新增留言: /messages/add 或 /messages/save - 提交留言（请求体为JSON）。
修改留言: /messages/update - 回复或修改内容（需ID）。
删除留言: /messages/delete - 批量删除（请求体为ID数组）。

---

NewsController.java - 公告栏
基础路径: /news
提供公告的完整CRUD功能：
分页列表（后端）: /news/page - 获取所有公告（需登录）。
分页列表（前端）: /news/list - 公开展示（@IgnoreAuth）。
获取详情（后端）: /news/info/{id} - 后台管理使用。
获取详情（前端）: /news/detail/{id} - 公开展示（@IgnoreAuth）。
新增公告: /news/add 或 /news/save - 发布公告（请求体为JSON）。
修改公告: /news/update - 修改公告（需ID）。
删除公告: /news/delete - 批量删除（请求体为ID数组）。

---

TushuguihaiController.java - 图书归还
基础路径: /tushuguihai
管理归还记录的申请、查询与审核，读者仅查看自身记录：
分页列表（后端）: /tushuguihai/page - 读者登录后筛选自身记录。
分页列表（前端）: /tushuguihai/list - 公开展示（@IgnoreAuth）。
获取详情（后端）: /tushuguihai/info/{id} - 后台管理使用。
获取详情（前端）: /tushuguihai/detail/{id} - 公开展示（@IgnoreAuth）。
新增归还记录: /tushuguihai/add 或 /tushuguihai/save - 添加记录。
修改归还记录: /tushuguihai/update - 更新记录信息。
批量审核: /tushuguihai/shBatch - 批量审核（需ID数组、状态 sfsh 和回复 shhf）。
删除归还记录: /tushuguihai/delete - 批量删除（请求体为ID数组）。

---

TushujieyueController.java - 图书借阅
基础路径: /tushujieyue
管理借阅记录的申请、查询与审核，读者仅查看自身记录：
分页列表（后端）: /tushujieyue/page - 读者登录后筛选自身记录。
分页列表（前端）: /tushujieyue/list - 公开展示（@IgnoreAuth）。
获取详情（后端）: /tushujieyue/info/{id} - 后台管理使用。
获取详情（前端）: /tushujieyue/detail/{id} - 公开展示（@IgnoreAuth）。
新增借阅申请: /tushujieyue/add 或 /tushujieyue/save - 提交申请。
修改借阅记录: /tushujieyue/update - 更新记录信息。
批量审核: /tushujieyue/shBatch - 批量审核（需ID数组、状态 sfsh 和回复 shhf）。
删除借阅记录: /tushujieyue/delete - 批量删除（请求体为ID数组）。

---

DuzheController.java - 读者管理
基础路径: /duzhe
提供读者账户认证与信息管理：
认证接口（@IgnoreAuth）:
登录: /duzhe/login (POST) - 使用学号（xueshengxuehao）和密码。
注册: /duzhe/register (POST) - 请求体为读者信息JSON。
密码重置: /duzhe/resetPass (POST) - 按学号重置密码为 "123456"。
会话接口:
退出登录: /duzhe/logout (GET)。
获取当前读者: /duzhe/session (GET)。
数据管理接口:
分页列表（后端）: /duzhe/page - 获取所有读者。
分页列表（前端）: /duzhe/list - 公开展示（@IgnoreAuth）。
获取详情（后端）: /duzhe/info/{id}。
获取详情（前端）: /duzhe/detail/{id} - 公开展示（@IgnoreAuth）。
新增读者: /duzhe/save 或 /duzhe/add。
修改读者: /duzhe/update。
删除读者: /duzhe/delete - 批量删除（请求体为ID数组）。

---

JieyuekaController.java - 借阅卡管理
基础路径: /jieyueka
管理借阅卡的CRUD，读者仅查看自身卡片：
分页列表（后端）: /jieyueka/page - 读者登录后仅返回自身卡片。
分页列表（前端）: /jieyueka/list - 公开展示（@IgnoreAuth）。
获取详情（后端）: /jieyueka/info/{id}。
获取详情（前端）: /jieyueka/detail/{id} - 公开展示（@IgnoreAuth）。
新增借阅卡: /jieyueka/save 或 /jieyueka/add。
修改借阅卡: /jieyueka/update。
删除借阅卡: /jieyueka/delete - 批量删除（请求体为ID数组）。

---

FileController.java - 文件上传与下载
基础路径: /file
通用文件处理接口（@IgnoreAuth）：
文件上传: /file/upload (POST) - multipart/form-data 类型，返回服务器文件名（如 1672200856123.jpg）。
文件下载: /file/download (GET) - 参数 fileName，用于下载文件（图片通过URL直接访问）。

---

ChaoqifakuanController.java - 超期罚款管理
基础路径: /chaoqifakuan
管理罚款记录的CRUD，读者仅查看自身罚款：
分页列表（后端）: /chaoqifakuan/page - 读者登录后筛选自身记录。
分页列表（前端）: /chaoqifakuan/list - 公开展示（@IgnoreAuth）。
获取详情（后端）: /chaoqifakuan/info/{id}。
获取详情（前端）: /chaoqifakuan/detail/{id} - 公开展示（@IgnoreAuth）。
新增罚款记录: /chaoqifakuan/save 或 /chaoqifakuan/add。
修改罚款记录: /chaoqifakuan/update - 更新支付状态等。
删除罚款记录: /chaoqifakuan/delete - 批量删除（请求体为ID数组）。

---

CommonController.java - 通用工具接口
基础路径: /
提供动态查询、统计等工具接口（大部分 @IgnoreAuth）：
获取下拉框选项: /option/{tableName}/{columnName} - 动态生成下拉菜单（如图书类别）。
数据联动: /follow/{tableName}/{columnName}?columnValue=xxx - 根据选择项填充表单。
获取提醒数量: /remind/{tableName}/{columnName}/{type} - 显示待处理事务数量（如即将到期图书）。
数据统计（图表）:
/cal/{tableName}/{columnName} - 数值列求和。
/group/{tableName}/{columnName} - 分组统计（饼图/柱状图）。
/value/{tableName}/{xColumnName}/{yColumnName} - 按x/y轴统计。
数据库备份: /mysqldump (GET) - 触发备份并下载。

---

ConfigController.java - 系统配置管理
基础路径: /config
管理配置项（如轮播图）：
分页列表（后端）: /config/page - 获取所有配置项（需登录）。
分页列表（前端）: /config/list - 公开展示（@IgnoreAuth）。
获取详情（前端）: /config/detail/{id} - 公开展示（@IgnoreAuth）。
获取详情（后端）: /config/info/{id} - 后台管理使用。
新增配置: /config/save。
修改配置: /config/update。
删除配置: /config/delete - 批量删除（请求体为ID数组）。

---
以上内容由AI生成，仅供参考。实际操作时必须再次检查文件，以补充遗漏内容，或者避免内容错误。

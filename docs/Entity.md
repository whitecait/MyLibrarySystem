# 实体类参考文档

本文档根据后端项目中的实体类定义自动生成，用于前端开发时参考字段名和含义。

---

### 1. 读者 (DuzheEntity)
- **数据库表**: `duzhe`
- **功能描述**: 存储读者的基本信息和账户资料。
- **字段列表**:
    - `id`: 主键ID
    - `xueshengxuehao`: 学生学号 (用于登录)
    - `xingming`: 姓名
    - `mima`: 密码
    - `xingbie`: 性别
    - `yuanxi`: 院系
    - `chushengnianyue`: 出生年月
    - `shouji`: 手机
    - `touxiang`: 头像 (图片文件名)
    - `addtime`: 记录创建时间

---

### 2. 图书信息 (TushuxinxiEntity)
- **数据库表**: `tushuxinxi`
- **功能描述**: 存储图书的核心信息。
- **字段列表**:
    - `id`: 主键ID
    - `tushubianhao`: 图书编号
    - `tushumingcheng`: 图书名称
    - `tushuleibie`: 图书类别
    - `tushufengmian`: 图书封面 (图片文件名)
    - `zuozhe`: 作者
    - `chubanshe`: 出版社
    - `shuliang`: 数量
    - `xiangqing`: 详情
    - `addtime`: 记录创建时间

---

### 3. 图书类别 (TushuleibieEntity)
- **数据库表**: `tushuleibie`
- **功能描述**: 定义图书的分类。
- **字段列表**:
    - `id`: 主键ID
    - `tushuleibie`: 图书类别名称
    - `addtime`: 记录创建时间

---

### 4. 图书借阅 (TushujieyueEntity)
- **数据库表**: `tushujieyue`
- **功能描述**: 记录读者的图书借阅申请和状态。
- **字段列表**:
    - `id`: 主键ID
    - `jieyueren`: 借阅人 (读者姓名)
    - `xueshengxuehao`: 学生学号
    - `tushubianhao`: 图书编号
    - `tushumingcheng`: 图书名称
    - `jieyueshijian`: 借阅时间
    - `sfsh`: 是否审核 (是/否)
    - `shhf`: 审核回复
    - `addtime`: 记录创建时间

---

### 5. 图书归还 (TushuguihaiEntity)
- **数据库表**: `tushuguihai`
- **功能描述**: 记录读者的图书归还申请和状态。
- **字段列表**:
    - `id`: 主键ID
    - `tushubianhao`: 图书编号
    - `tushumingcheng`: 图书名称
    - `guihaishijian`: 归还时间
    - `xueshengxuehao`: 学生学号
    - `xingming`: 姓名
    - `sfsh`: 是否审核 (是/否)
    - `shhf`: 审核回复
    - `addtime`: 记录创建时间

---

### 6. 图书续借 (TushuxujieEntity)
- **数据库表**: `tushuxujie`
- **功能描述**: 记录读者的图书续借申请和状态。
- **字段列表**:
    - `id`: 主键ID
    - `tushubianhao`: 图书编号
    - `tushumingcheng`: 图书名称
    - `xujieshijian`: 续借时间
    - `xueshengxuehao`: 学生学号
    - `xingming`: 姓名
    - `sfsh`: 是否审核 (是/否)
    - `shhf`: 审核回复
    - `addtime`: 记录创建时间

---

### 7. 借阅卡 (JieyuekaEntity)
- **数据库表**: `jieyueka`
- **功能描述**: 存储读者的借阅卡信息。
- **字段列表**:
    - `id`: 主键ID
    - `kahao`: 卡号
    - `xueshengxuehao`: 学生学号
    - `xingming`: 姓名
    - `banlishijian`: 办理时间
    - `addtime`: 记录创建时间

---

### 8. 超期罚款 (ChaoqifakuanEntity)
- **数据库表**: `chaoqifakuan`
- **功能描述**: 记录因超期未还产生的罚款。
- **字段列表**:
    - `id`: 主键ID
    - `fakuanbianhao`: 罚款编号
    - `tushubianhao`: 图书编号
    - `tushumingcheng`: 图书名称
    - `fakuanjine`: 罚款金额
    - `fakuanshijian`: 罚款时间
    - `xueshengxuehao`: 学生学号
    - `xingming`: 姓名
    - `sfzf`: 是否支付 (是/否)
    - `addtime`: 记录创建时间

---

### 9. 用户 (UsersEntity)
- **数据库表**: `users`
- **功能描述**: 系统管理员账户。
- **字段列表**:
    - `id`: 主键ID
    - `username`: 用户名
    - `password`: 密码
    - `role`: 角色

---
*（其他实体如 `NewsEntity`, `MessagesEntity`, `ConfigEntity` 等结构较为简单，主要包含标题、内容、图片等字段，此处不再一一列出。）*